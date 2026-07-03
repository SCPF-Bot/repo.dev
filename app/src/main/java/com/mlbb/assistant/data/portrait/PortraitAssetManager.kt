package com.mlbb.assistant.data.portrait

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.mlbb.assistant.domain.model.Hero
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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
    private val imageLoader: ImageLoader,
) {

    enum class Variant(val fileName: String, val maxDimensionPx: Int) {
        MAIN("hero.main.png", 256),
        PICK("hero.pick.png", 128),
        BAN("hero.ban.png", 64),
    }

    enum class Stage { DOWNLOADING, OPTIMIZING, DELETING }

    data class Progress(val stage: Stage, val processed: Int, val total: Int)

    companion object {
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

    /** Downloads [Variant.MAIN] for every hero that doesn't already have it cached. */
    suspend fun downloadAll(heroes: List<Hero>, onProgress: (Progress) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            val total = heroes.size
            heroes.forEachIndexed { index, hero ->
                downloadMain(hero)
                onProgress(Progress(Stage.DOWNLOADING, index + 1, total))
            }
        }

    /** Derives [Variant.PICK] and [Variant.BAN] from each hero's already-downloaded [Variant.MAIN]. */
    suspend fun optimizeAll(heroes: List<Hero>, onProgress: (Progress) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            val total = heroes.size
            heroes.forEachIndexed { index, hero ->
                optimizeOne(hero.id)
                onProgress(Progress(Stage.OPTIMIZING, index + 1, total))
            }
        }

    /** Deletes every cached portrait asset, then redownloads + reoptimizes from scratch. */
    suspend fun refreshAll(heroes: List<Hero>, onProgress: (Progress) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            deleteAll(onProgress)
            downloadAll(heroes, onProgress)
            optimizeAll(heroes, onProgress)
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
        if (!exists(hero.id, Variant.MAIN)) downloadMain(hero)
        if (exists(hero.id, Variant.MAIN) && !isFullyOptimized(hero.id)) optimizeOne(hero.id)
    }

    private suspend fun downloadMain(hero: Hero) {
        if (hero.imageUrl.isBlank() || exists(hero.id, Variant.MAIN)) return
        val bitmap = fetchBitmap(hero.imageUrl) ?: return
        saveScaled(bitmap, hero.id, Variant.MAIN)
        bitmap.recycle()
    }

    private fun optimizeOne(heroId: Int) {
        val mainFile = file(heroId, Variant.MAIN)
        if (!mainFile.exists()) return
        val mainBitmap = BitmapFactory.decodeFile(mainFile.absolutePath) ?: return
        saveScaled(mainBitmap, heroId, Variant.PICK)
        saveScaled(mainBitmap, heroId, Variant.BAN)
        mainBitmap.recycle()
    }

    private fun saveScaled(source: Bitmap, heroId: Int, variant: Variant) {
        val target = variant.maxDimensionPx
        val scaled = if (source.width == target && source.height == target) {
            source
        } else {
            Bitmap.createScaledBitmap(source, target, target, true)
        }
        FileOutputStream(file(heroId, variant)).use { out ->
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (scaled !== source) scaled.recycle()
    }

    private suspend fun fetchBitmap(url: String): Bitmap? = runCatching {
        val request = ImageRequest.Builder(context).data(url).build()
        val result = imageLoader.execute(request)
        (result as? SuccessResult)?.image?.toBitmap()
    }.getOrNull()
}
