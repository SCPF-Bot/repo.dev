package com.mlbb.assistant.data.portrait

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.utils.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local on-disk hero portrait asset pipeline.
 *
 * Downloads the official CDN portrait once per hero ([Variant.MAIN]) and derives two
 * smaller, slot-fidelity-matched variants used purely for on-screen detection:
 *
 * - [Variant.MAIN] (256px) — the app's own UI (hero grids, detail screen, etc).
 * - [Variant.PICK] (128px) — matches [com.mlbb.assistant.capture.PortraitNormalizer]'s
 *   working resolution; used to build reference hashes for PICK slots.
 * - [Variant.BAN] (64px) — roughly half of PICK, mirroring the real on-screen ratio
 *   between ban-slot and pick-slot portrait sizes in `draft_ui_map.json`
 *   (ban abs width ≈53px vs pick abs width ≈107px); used for BAN slot reference hashes.
 *
 * Files live under `filesDir/portraits/{heroId}/hero.{main,pick,ban}.png`. All operations
 * are plain disk + bitmap work — this class holds no in-memory cache, so it is safe to
 * construct more than once (e.g. once inside [com.mlbb.assistant.capture.PortraitMatcher]
 * and once inside the Settings screen) without going out of sync.
 */
@Singleton
class PortraitAssetManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val jsonParser: JsonParser,
) {

    enum class Variant(val fileName: String, val maxDimensionPx: Int) {
        MAIN("hero.main.png", 256),
        PICK("hero.pick.png", 128),
        BAN("hero.ban.png", 64),
    }

    enum class Stage { DOWNLOADING, OPTIMIZING, DELETING }

    data class Progress(val stage: Stage, val processed: Int, val total: Int)

    companion object {
        /** Number of portraits downloaded concurrently in each batch. */
        private const val DOWNLOAD_BATCH_SIZE = 7

        /**
         * Pure path resolver — lets callers that only need a read-only file
         * reference (e.g. [com.mlbb.assistant.presentation.common.components.HeroPortrait])
         * locate an asset without constructing a full [PortraitAssetManager]
         * (which requires an [ImageLoader]).
         */
        fun resolveFile(context: Context, heroId: Int, variant: Variant): File =
            File(File(context.filesDir, "portraits/$heroId"), variant.fileName)

        fun resolveLocalFileOrNull(context: Context, heroId: Int, variant: Variant): File? =
            resolveFile(context, heroId, variant).takeIf { it.exists() && it.length() > 0 }
    }

    private val rootDir: File by lazy {
        File(context.filesDir, "portraits").apply { mkdirs() }
    }

    private fun heroDir(heroId: Int): File =
        File(rootDir, heroId.toString()).apply { mkdirs() }

    fun file(heroId: Int, variant: Variant): File = File(heroDir(heroId), variant.fileName)

    fun exists(heroId: Int, variant: Variant): Boolean = file(heroId, variant).exists()

    fun localFileOrNull(heroId: Int, variant: Variant): File? =
        file(heroId, variant).takeIf { it.exists() && it.length() > 0 }

    /** True once all three variants exist on disk for [heroId]. */
    fun isFullyOptimized(heroId: Int): Boolean =
        Variant.entries.all { exists(heroId, it) }

    fun downloadedCount(heroes: List<Hero>): Int = heroes.count { exists(it.id, Variant.MAIN) }
    fun optimizedCount(heroes: List<Hero>): Int = heroes.count { isFullyOptimized(it.id) }

    /**
     * Downloads [Variant.MAIN] for every hero that doesn't already have it cached.
     *
     * URL resolution order (most-authoritative first):
     *  1. [default_heroes.json][com.mlbb.assistant.R.raw.default_heroes] portrait URL index —
     *     always reflects the official CDN URLs regardless of DB state.
     *  2. [Hero.imageUrl] from the Room DB — used only as a fallback when the JSON index
     *     has no entry for a given hero (e.g. a brand-new hero not yet in the bundled JSON).
     *
     * Downloads run [DOWNLOAD_BATCH_SIZE] heroes concurrently. Each batch completes before
     * the next starts so progress increments smoothly and memory pressure stays bounded.
     *
     * Per-hero failures are collected; a single [IOException] summary is thrown after all
     * batches complete so the caller sees the full failure set, not just the first error.
     */
    suspend fun downloadAll(heroes: List<Hero>, onProgress: (Progress) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            // Build the URL index once up-front — O(heroes) parse, amortised across all downloads.
            val urlIndex = jsonParser.buildPortraitUrlIndex()

            val total    = heroes.size
            val failures = mutableListOf<String>()
            var processed = 0

            heroes.chunked(DOWNLOAD_BATCH_SIZE).forEach { batch ->
                coroutineScope {
                    batch.map { hero ->
                        async {
                            val url = urlIndex[hero.id]?.takeIf { it.isNotBlank() }
                                ?: hero.imageUrl
                            runCatching { downloadMain(hero, url) }
                                .onFailure { e ->
                                    Timber.w(e, "Portrait download failed: ${hero.name} (id=${hero.id}) url=$url")
                                    synchronized(failures) { failures += hero.name }
                                }
                        }
                    }.awaitAll()
                }
                processed += batch.size
                onProgress(Progress(Stage.DOWNLOADING, processed, total))
            }

            if (failures.isNotEmpty()) throw IOException(
                "${failures.size} hero(es) failed to download: ${failures.joinToString()}"
            )
        }

    /**
     * Derives [Variant.PICK] and [Variant.BAN] from each hero's already-downloaded [Variant.MAIN].
     * Per-hero failures are collected; a single [IOException] summary is thrown at the end if any failed.
     */
    suspend fun optimizeAll(heroes: List<Hero>, onProgress: (Progress) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            val total    = heroes.size
            val failures = mutableListOf<String>()
            heroes.forEachIndexed { index, hero ->
                runCatching { optimizeOne(hero.id) }
                    .onFailure { e ->
                        Timber.w(e, "Portrait optimize failed: ${hero.name} (id=${hero.id})")
                        failures += hero.name
                    }
                onProgress(Progress(Stage.OPTIMIZING, index + 1, total))
            }
            if (failures.isNotEmpty()) throw IOException(
                "${failures.size} hero(es) failed to optimize: ${failures.joinToString()}"
            )
        }

    /** Deletes every cached portrait asset, then redownloads + reoptimizes from scratch. */
    suspend fun refreshAll(heroes: List<Hero>, onProgress: (Progress) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            val n = heroes.size
            // Emit combined progress so callers see a single 0→total arc instead of three
            // independent 0→n arcs that cause the progress indicator to reset twice.
            // Each hero counts once per stage: total = n*3 (delete + download + optimize).
            val combined = n * 3
            deleteAll    { p -> onProgress(Progress(Stage.DELETING,    p.processed,           combined)) }
            downloadAll(heroes) { p -> onProgress(Progress(Stage.DOWNLOADING, n + p.processed,     combined)) }
            optimizeAll(heroes) { p -> onProgress(Progress(Stage.OPTIMIZING,  n * 2 + p.processed, combined)) }
        }

    suspend fun deleteAll(onProgress: (Progress) -> Unit = {}) = withContext(Dispatchers.IO) {
        val dirs = rootDir.listFiles()?.toList().orEmpty()
        val total = dirs.size
        dirs.forEachIndexed { index, dir ->
            dir.deleteRecursively()
            onProgress(Progress(Stage.DELETING, index + 1, total))
        }
    }

    /** Ensures both slot-detection variants exist for a single hero, downloading/optimizing on demand. */
    suspend fun ensureVariants(hero: Hero) = withContext(Dispatchers.IO) {
        if (!exists(hero.id, Variant.MAIN)) {
            val urlIndex = jsonParser.buildPortraitUrlIndex()
            val url = urlIndex[hero.id]?.takeIf { it.isNotBlank() } ?: hero.imageUrl
            downloadMain(hero, url)
        }
        if (exists(hero.id, Variant.MAIN) && !isFullyOptimized(hero.id)) optimizeOne(hero.id)
    }

    /**
     * Downloads the [Variant.MAIN] portrait for [hero] from [url].
     *
     * [url] is supplied by the caller (resolved from the JSON index by [downloadAll] /
     * [ensureVariants]) so this method never has to touch [Hero.imageUrl] directly —
     * keeping the authoritative-URL logic in one place.
     */
    private suspend fun downloadMain(hero: Hero, url: String) {
        if (exists(hero.id, Variant.MAIN)) return
        if (url.isBlank()) throw IOException("Hero ${hero.name} (id=${hero.id}) has no image URL in JSON index or DB")
        val bitmap = fetchBitmap(url)
        saveScaled(bitmap, hero.id, Variant.MAIN)
        bitmap.recycle()
    }

    private fun optimizeOne(heroId: Int) {
        val mainFile = file(heroId, Variant.MAIN)
        if (!mainFile.exists()) throw IOException(
            "Cannot optimize hero $heroId: hero.main.png not found — run Download first"
        )
        // Skip early if both derived variants already exist — avoids unnecessary bitmap decode
        // and re-write on repeated "Optimize" taps or PortraitPrefetchWorker re-runs.
        val needPick = !exists(heroId, Variant.PICK)
        val needBan  = !exists(heroId, Variant.BAN)
        if (!needPick && !needBan) return
        val mainBitmap = BitmapFactory.decodeFile(mainFile.absolutePath)
            ?: throw IOException("Failed to decode hero.main.png for hero $heroId — file may be corrupt")
        try {
            if (needPick) saveScaled(mainBitmap, heroId, Variant.PICK)
            if (needBan)  saveScaled(mainBitmap, heroId, Variant.BAN)
        } finally {
            mainBitmap.recycle()
        }
    }

    private fun saveScaled(source: Bitmap, heroId: Int, variant: Variant) {
        val target = variant.maxDimensionPx
        // Scale to fit within `target` on the longest side, preserving aspect ratio.
        // The previous square-forced path (createScaledBitmap(source, target, target))
        // distorted non-square CDN portraits on both axes, degrading display quality
        // and skewing the reference hashes used by PortraitMatcher.
        val scaled = if (source.width <= target && source.height <= target) {
            source // already small enough — no scale needed
        } else {
            val ratio = minOf(target.toFloat() / source.width, target.toFloat() / source.height)
            val w = (source.width  * ratio).toInt().coerceAtLeast(1)
            val h = (source.height * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(source, w, h, true)
        }
        // Write to a sibling temp file first, then rename atomically.
        // A direct write to the destination leaves a partially-written PNG on crash/OOM —
        // subsequent exists() checks treat it as valid but BitmapFactory.decodeFile returns null.
        val destFile = file(heroId, variant)
        val tmpFile  = File(destFile.parentFile, "${variant.fileName}.tmp")
        try {
            FileOutputStream(tmpFile).use { out ->
                // compress() returns false on failure (e.g. OOM during encoding).
                // Previously the return value was ignored, leaving a zero-byte or
                // truncated PNG that passed the exists() && length() > 0 guard.
                val ok = scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                if (!ok) throw IOException(
                    "Bitmap.compress failed for hero $heroId variant ${variant.fileName}"
                )
            }
            // renameTo() returns false without throwing when it cannot move the file.
            // Previously a false return silently deleted the tmp file in the finally
            // block, discarding the written data with no error surfaced to the caller.
            if (!tmpFile.renameTo(destFile)) throw IOException(
                "Atomic rename failed for hero $heroId variant ${variant.fileName}"
            )
        } finally {
            tmpFile.delete() // no-op if rename succeeded; cleans up on failure
        }
        if (scaled !== source) scaled.recycle()
    }

    /**
     * Fetches a [Bitmap] from [url] via OkHttp.
     *
     * Coil was removed from this path because it returned [ErrorResult] for every request
     * to `akmweb.youngjoygame.com`, even after adding a browser User-Agent — the CDN
     * responds correctly to raw OkHttp (confirmed with curl). Using OkHttp directly
     * eliminates Coil's decode/transform pipeline as a failure point.
     *
     * Must be called from a background thread (already guaranteed by [withContext] in all
     * callers — [downloadMain] → [downloadAll] / [ensureVariants]).
     */
    private fun fetchBitmap(url: String): Bitmap {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile) AppleWebKit/537.36")
            .build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("HTTP ${response.code} fetching $url")
        val bytes = response.body?.bytes()
            ?: throw IOException("Empty response body for $url")
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IOException("BitmapFactory could not decode image from $url")
    }
}
