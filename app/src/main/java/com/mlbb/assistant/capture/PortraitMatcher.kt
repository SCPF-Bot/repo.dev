package com.mlbb.assistant.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.mlbb.assistant.data.portrait.PortraitAssetManager
import com.mlbb.assistant.domain.model.Hero
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

data class MatchResult(
    val hero: Hero?,
    val confidence: Float,            // 0.0–1.0
    val requiresConfirmation: Boolean // true if confidence < CONFIRM_THRESHOLD
)

/**
 * Matches a cropped hero-slot bitmap against the hero roster.
 *
 * ### Matching strategy — TFLite primary, pHash fallback
 *
 * #### Primary: TFLite [HeroClassifier]
 * A MobileNetV3Small model (`mlbb_hero_classifier.tflite`, ~2 MB) classifies each
 * cropped slot in a single forward pass.  If the top-1 softmax confidence is ≥
 * [PhaseDetectionConfig.TFLITE_ACCEPT_THRESHOLD] (default 0.70), the result is
 * returned immediately without computing perceptual hashes.
 *
 * If the confidence is in the tentative band
 * [[PhaseDetectionConfig.TFLITE_TENTATIVE_THRESHOLD], [PhaseDetectionConfig.TFLITE_ACCEPT_THRESHOLD]),
 * the TFLite result is used but marked `requiresConfirmation = true`, triggering the
 * multi-frame confirmation counter.
 *
 * If the classifier is unavailable (model load failed, label file missing, or
 * label count ≠ output size) or confidence is below the tentative threshold, the
 * call falls through to the pHash + histogram path.
 *
 * #### Fallback: pHash + colour histogram
 * dHash acts as a fast pre-filter: candidates whose dHash similarity is below
 * [DHASH_PREFILTER_THRESHOLD] are skipped before computing the more expensive
 * DCT-based pHash + 48-bin colour histogram.  The hybrid score is
 * `pHash × 0.65 + histogram × 0.35`.
 *
 * #### Multi-frame confirmation
 * Both paths share the same [consecutiveHits] counter keyed by [slotKey].
 * A result is promoted to full confidence only when the same heroId wins for
 * [PhaseDetectionConfig.CONFIRMATION_FRAMES_REQUIRED] consecutive calls from the
 * same slot.
 *
 * See `misc.md` §13 for the integration rationale and tuning guidance.
 */
class PortraitMatcher(
    private val context: Context,
    private val imageLoader: ImageLoader,
    private val portraitAssetManager: PortraitAssetManager = PortraitAssetManager(context, imageLoader),
) {

    companion object {
        private const val CONFIRM_THRESHOLD       = 0.82f
        private const val REJECT_THRESHOLD        = 0.42f

        private const val HISTOGRAM_BINS          = 16     // per channel → 48-element vector
        private const val PHASH_WEIGHT            = 0.65f
        private const val HISTOGRAM_WEIGHT        = 0.35f

        private const val DHASH_PREFILTER_THRESHOLD = 0.60f

        private const val TAG = "PortraitMatcher"
    }

    // ── TFLite classifier (primary matching path) ──────────────────────────

    private val classifier = HeroClassifier(context)

    // ── Slot-aware triple-hash fusion (recommendations.md §5) ──────────────

    private val slotAwareHasher = SlotAwareHasher()
    private val heroThresholds  = HeroThresholds(context)
    private val consensusManager = SlotConsensusManager()

    // ── pHash / histogram caches (fallback path) ───────────────────────────

    private val dHashCache     = LruCache<Int, Long>(200)
    private val pHashCache     = LruCache<Int, Long>(200)
    private val histogramCache = LruCache<Int, FloatArray>(200)

    // slotKey → (heroId, consecutiveCount)  — shared by both paths.
    // ConcurrentHashMap: resetConfirmation() can be called from IO while
    // match() runs on Default (P0-04 / P0-05 pattern).
    private val consecutiveHits = ConcurrentHashMap<String, Pair<Int, Int>>()

    // ── Lifecycle ──────────────────────────────────────────────────────────

    /** Release the TFLite interpreter. Call when the owning service stops. */
    fun close() = classifier.close()

    // ── Preloading ────────────────────────────────────────────────────────

    /**
     * Parallel lazy preload — computes dHash, pHash, and colour histogram
     * for each hero's portrait in batches of 10.
     *
     * When [classifier] is available, hash preloading is still performed so
     * the pHash fallback can engage immediately on any individual hero whose
     * TFLite confidence is below the accept threshold.
     */
    suspend fun preloadHashes(heroes: List<Hero>) = withContext(Dispatchers.IO) {
        if (classifier.isAvailable) {
            Timber.d("$TAG: TFLite classifier available — preloading pHash as fallback only")
        }
        val batches = heroes.chunked(10)
        for (batch in batches) {
            coroutineScope {
                batch.forEach { hero ->
                    launch {
                        if (pHashCache.get(hero.id) == null) {
                            // Prefer the already-downloaded local asset (hero.main.png) over a
                            // network fetch — avoids redundant CDN traffic when PortraitAssetManager
                            // has already cached the portrait on disk.
                            val bmp = portraitAssetManager.localFileOrNull(hero.id, PortraitAssetManager.Variant.MAIN)
                                ?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
                                ?: fetchPortrait(hero.imageUrl)
                                ?: return@launch
                            dHashCache.put(hero.id, PerceptualHash.compute(bmp))
                            pHashCache.put(hero.id, PerceptualHash.computePHash(bmp))
                            histogramCache.put(hero.id, computeHistogram(bmp))
                            bmp.recycle()
                        }

                        if (CvFeatureFlags.useSlotAwareHash &&
                            (!slotAwareHasher.hasCached(hero.id, SlotType.PICK) ||
                                !slotAwareHasher.hasCached(hero.id, SlotType.BAN))
                        ) {
                            cacheSlotAwareHashes(hero)
                        }
                    }
                }
            }
        }
    }

    /**
     * Builds and caches the PICK/BAN reference hashes for [hero] from the
     * [PortraitAssetManager]-managed, size-matched local assets (`hero.pick.png` /
     * `hero.ban.png`), downloading/optimizing them on demand if not already cached
     * on disk. Reference portraits carry no ban-slash/name-strip occlusion, so a
     * CENTER-style normalization (no masking) is the correct baseline to hash
     * against — the slot-side crop is normalized per its own SlotType at match time.
     */
    private suspend fun cacheSlotAwareHashes(hero: Hero) {
        runCatching { portraitAssetManager.ensureVariants(hero) }
            .onFailure { Timber.w(it, "$TAG: failed to prepare portrait assets for hero ${hero.id}") }

        val variantsBySlot = listOf(
            PortraitAssetManager.Variant.PICK to SlotType.PICK,
            PortraitAssetManager.Variant.BAN to SlotType.BAN,
        )
        for ((variant, slotType) in variantsBySlot) {
            if (slotAwareHasher.hasCached(hero.id, slotType)) continue
            val file = portraitAssetManager.localFileOrNull(hero.id, variant) ?: continue
            val bmp = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull() ?: continue
            val normalized = PortraitNormalizer.normalizeForSlot(bmp, SlotType.CENTER)
            slotAwareHasher.cacheHero(hero.id, slotType, normalized)
            normalized.recycle()
            bmp.recycle()
        }
    }

    /** Reset per-slot confirmation counters (call at session start / phase change). */
    fun resetConfirmation() {
        consecutiveHits.clear()
        consensusManager.clearAll()
    }

    // ── Matching ──────────────────────────────────────────────────────────

    /**
     * Match a cropped slot bitmap against the hero roster.
     *
     * Tries [HeroClassifier] first (TFLite primary path), falls back to
     * pHash + histogram when the classifier is unavailable or confidence is low.
     *
     * @param slotBitmap      Cropped slot image from [SlotRegions.cropSlot].
     * @param availableHeroes Candidate hero list (all heroes, or filtered to available).
     * @param slotKey         Unique slot identifier (e.g. "enemyBan3", "ourPick1").
     *                        Used to namespace the consecutive-hit confirmation counter.
     * @param slotType        Which UI region [slotBitmap] came from (recommendations.md §3).
     *                        Drives [PortraitNormalizer] occlusion-masking and the
     *                        [SlotAwareHasher] fusion weights. Defaults to [SlotType.PICK]
     *                        for backward compatibility with call sites that don't (yet)
     *                        distinguish slot regions.
     */
    fun match(
        slotBitmap: Bitmap,
        availableHeroes: List<Hero>,
        slotKey: String = "",
        slotType: SlotType = SlotType.PICK,
    ): MatchResult {
        // ── 1. TFLite primary path ─────────────────────────────────────────
        if (classifier.isAvailable) {
            val tfliteResults = classifier.classify(slotBitmap, topK = PhaseDetectionConfig.TFLITE_TOP_K)
            val top1 = tfliteResults.firstOrNull()

            if (top1 != null && top1.confidence >= PhaseDetectionConfig.TFLITE_TENTATIVE_THRESHOLD) {
                val matchedHero = availableHeroes.firstOrNull { it.id == top1.heroId }

                if (matchedHero != null) {
                    Timber.v(
                        "$TAG [TFLite] slot=$slotKey hero=${matchedHero.name} " +
                            "conf=%.3f (top2=%s)".format(
                                top1.confidence,
                                tfliteResults.getOrNull(1)
                                    ?.let { "%.3f".format(it.confidence) } ?: "—"
                            )
                    )

                    val confirmed = applyConfirmation(slotKey, matchedHero.id, matchedHero)
                    val isHighConf = top1.confidence >= PhaseDetectionConfig.TFLITE_ACCEPT_THRESHOLD

                    return when {
                        isHighConf -> MatchResult(
                            hero = confirmed ?: matchedHero,
                            confidence = top1.confidence,
                            requiresConfirmation = confirmed == null,
                        )
                        else -> MatchResult(
                            hero = matchedHero,
                            confidence = top1.confidence,
                            requiresConfirmation = true,
                        )
                    }
                }
            }

            // TFLite fired but heroId not in availableHeroes (hero already picked/banned)
            // or confidence < tentative threshold → fall through to pHash.
            if (top1 != null) {
                Timber.v(
                    "$TAG [TFLite] falling through to pHash: slot=$slotKey " +
                        "top1.heroId=${top1.heroId} conf=%.3f".format(top1.confidence)
                )
            }
        }

        // ── 2. Slot-aware triple-hash fusion (recommendations.md §5.2–5.3) ──
        if (CvFeatureFlags.useSlotAwareHash) {
            val normalized = PortraitNormalizer.normalizeForSlot(slotBitmap, slotType)
            val slotHash = try {
                slotAwareHasher.computeTripleHash(normalized)
            } finally {
                normalized.recycle()
            }

            val candidateIds = availableHeroes.map { it.id }
            val best = slotAwareHasher.bestMatch(slotHash, candidateIds, slotType)

            if (best != null && best.second >= PhaseDetectionConfig.HASH_FUSION_TENTATIVE_MIN) {
                val (bestHeroId, fusedSim) = best
                val matchedHero = availableHeroes.firstOrNull { it.id == bestHeroId }

                if (matchedHero != null) {
                    Timber.v(
                        "$TAG [HashFusion] slot=$slotKey slotType=$slotType hero=${matchedHero.name} " +
                            "sim=%.3f".format(fusedSim)
                    )

                    val confirmed = applyConfirmation(slotKey, matchedHero.id, matchedHero)
                    val acceptThreshold = heroThresholds.thresholdFor(matchedHero.id, slotType)
                        ?: PhaseDetectionConfig.HASH_FUSION_ACCEPT_MIN
                    val isHighConf = fusedSim >= acceptThreshold

                    // Temporal consensus (recommendations.md §5.4): a plurality vote across
                    // the last few frames, independent of applyConfirmation()'s stricter
                    // "same heroId N times in a row" streak — one noisy frame won't reset it.
                    // §5 kill-switch: CvFeatureFlags.enableTemporalConsensus allows disabling
                    // this tier independently if it regresses accuracy in production.
                    val consensusEnabled = CvFeatureFlags.enableTemporalConsensus
                    if (consensusEnabled && slotKey.isNotEmpty()) consensusManager.update(slotKey, matchedHero.id, fusedSim)
                    val consensus = if (consensusEnabled && slotKey.isNotEmpty()) consensusManager.confirm(slotKey) else null
                    val consensusConfirmsHero = consensus?.first == matchedHero.id

                    // §6 P3-observability: structured event for detection-accuracy tuning
                    // (todo.md §6, tag CV_MIGRATION). Logs the calibrated threshold used so
                    // hero_thresholds.json recalibrations can be validated in production logs.
                    Timber.tag("CV_MIGRATION").d(
                        "slot=%s heroId=%d confidence=%.3f matchedDistance=%.3f threshold=%.3f",
                        slotKey, matchedHero.id, fusedSim, 1f - fusedSim, acceptThreshold
                    )

                    return when {
                        isHighConf || consensusConfirmsHero -> {
                            if (slotKey.isNotEmpty()) consensusManager.clear(slotKey)
                            MatchResult(
                                hero = confirmed ?: matchedHero,
                                confidence = maxOf(fusedSim, consensus?.second ?: 0f),
                                requiresConfirmation = confirmed == null && !consensusConfirmsHero,
                            )
                        }
                        else -> MatchResult(
                            hero = matchedHero,
                            confidence = fusedSim,
                            requiresConfirmation = true,
                        )
                    }
                }
            }

            Timber.v("$TAG [HashFusion] no confident match for slot=$slotKey — falling through to legacy dHash+histogram")
        }

        // ── 3. pHash + histogram fallback (legacy path, kept per recommendations.md §5.3 step 3) ──
        if (!CvFeatureFlags.tfliteFallbackEnabled) {
            val confirmedHero = applyConfirmation(slotKey, -1, null)
            return MatchResult(confirmedHero, 0f, requiresConfirmation = true)
        }
        val slotDHash     = PerceptualHash.compute(slotBitmap)
        val slotPHash     = PerceptualHash.computePHash(slotBitmap)
        val slotHistogram = computeHistogram(slotBitmap)

        var bestHero: Hero? = null
        var bestSim  = 0f

        for (hero in availableHeroes) {
            val heroDHash = dHashCache.get(hero.id) ?: continue
            val heroPHash = pHashCache.get(hero.id) ?: continue
            val heroHist  = histogramCache.get(hero.id)

            val dHashSim = PerceptualHash.similarity(slotDHash, heroDHash)
            if (dHashSim < DHASH_PREFILTER_THRESHOLD) continue

            val pHashSim  = PerceptualHash.pHashSimilarity(slotPHash, heroPHash)
            val histSim   = heroHist?.let { histogramSimilarity(slotHistogram, it) } ?: 0f
            val histW     = if (heroHist != null) HISTOGRAM_WEIGHT else 0f
            val pHashW    = 1f - histW

            val hybridSim = pHashW * pHashSim + histW * histSim

            if (hybridSim > bestSim) {
                bestSim  = hybridSim
                bestHero = hero
            }
        }

        val confirmedHero = applyConfirmation(slotKey, bestHero?.id ?: -1, bestHero)

        return when {
            bestSim >= CONFIRM_THRESHOLD -> MatchResult(
                hero = confirmedHero ?: bestHero,
                confidence = bestSim,
                requiresConfirmation = confirmedHero == null,
            )
            bestSim >= REJECT_THRESHOLD  -> MatchResult(bestHero, bestSim, true)
            else                         -> MatchResult(null, bestSim, true)
        }
    }

    // ── Confirmation helper ────────────────────────────────────────────────

    /**
     * Updates [consecutiveHits] for [slotKey] and returns [hero] only when
     * the same heroId has appeared for [PhaseDetectionConfig.CONFIRMATION_FRAMES_REQUIRED]
     * consecutive calls.  Returns `null` when confirmation is still in progress.
     */
    private fun applyConfirmation(slotKey: String, heroId: Int, hero: Hero?): Hero? {
        if (slotKey.isEmpty() || hero == null) return hero
        val prev     = consecutiveHits[slotKey]
        val newCount = if (prev?.first == heroId) prev.second + 1 else 1
        consecutiveHits[slotKey] = heroId to newCount
        return if (newCount >= PhaseDetectionConfig.CONFIRMATION_FRAMES_REQUIRED) hero else null
    }

    // ── Histogram ─────────────────────────────────────────────────────────

    /**
     * Normalised 48-element colour histogram (16 bins × R/G/B).
     * Values within each channel sum to 1.0.
     */
    private fun computeHistogram(bmp: Bitmap): FloatArray {
        val bins = HISTOGRAM_BINS
        val hist = FloatArray(bins * 3) { 0f }
        val step = 2
        var count = 0

        for (x in 0 until bmp.width step step) {
            for (y in 0 until bmp.height step step) {
                val px = bmp.getPixel(x, y)
                val r  = android.graphics.Color.red(px)
                val g  = android.graphics.Color.green(px)
                val b  = android.graphics.Color.blue(px)

                hist[(r * bins / 256).coerceIn(0, bins - 1)]            += 1f
                hist[bins + (g * bins / 256).coerceIn(0, bins - 1)]     += 1f
                hist[bins * 2 + (b * bins / 256).coerceIn(0, bins - 1)] += 1f
                count++
            }
        }

        if (count > 0) {
            for (c in 0 until 3) {
                val offset = c * bins
                val sum = hist.slice(offset until offset + bins).sum().coerceAtLeast(1f)
                for (i in 0 until bins) hist[offset + i] /= sum
            }
        }

        return hist
    }

    /**
     * Per-channel Bhattacharyya similarity — returns [0, 1].
     * Computed independently per R/G/B channel then averaged.
     */
    private fun histogramSimilarity(h1: FloatArray, h2: FloatArray): Float {
        if (h1.size != h2.size) return 0f
        val bins = HISTOGRAM_BINS
        var totalBc = 0f
        for (c in 0 until 3) {
            val offset = c * bins
            var bc = 0f
            for (i in 0 until bins) {
                bc += Math.sqrt((h1[offset + i] * h2[offset + i]).toDouble()).toFloat()
            }
            totalBc += bc
        }
        return (totalBc / 3f).coerceIn(0f, 1f)
    }

    // ── Network ───────────────────────────────────────────────────────────

    private suspend fun fetchPortrait(url: String): Bitmap? = runCatching {
        val req = ImageRequest.Builder(context).data(url).build()
        val res = imageLoader.execute(req)
        (res as? SuccessResult)?.image?.toBitmap()
    }.getOrNull()
}
