package com.mlbb.assistant.capture

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Triple-hash signature for a normalized portrait crop.
 *
 * Note on JImageHash: `KilianB/JImageHash` is declared in the Gradle version
 * catalog but intentionally NOT wired into `app/build.gradle.kts` — it depends
 * on `java.awt.image.BufferedImage`, which is unavailable on the Android
 * runtime (ART ships only stub `java.awt.*` classes). See `docs/misc.md` §9 and
 * the comment in `build.gradle.kts`. [structural] below plays the same role
 * recommendations.md's "WaveletHash" component would have played — a
 * structural-edge signature that's robust to skin-tier colour overlays — but is
 * implemented with the existing pure-Kotlin [PerceptualHash.computePHash]
 * (DCT-based) instead of the unavailable JImageHash class.
 */
data class TripleHash(
    val structural: Long,   // DCT-based structural hash (PerceptualHash.computePHash)
    val color: Long,        // Custom grid-based average-color hash (no java.awt needed)
    val radial: Long        // Custom 64-bit radial gradient signature
)

/**
 * SlotType-aware perceptual matching engine (recommendations.md §5.2–5.3).
 *
 * Fuses three cheap, pure-Kotlin hashes computed on a [PortraitNormalizer]-normalized
 * crop, weighted differently depending on [SlotType] since BAN slots occlude the
 * center with a red slash (favoring edge/structural signal) while PICK slots occlude
 * only the bottom name strip (color signal stays reliable).
 */
class SlotAwareHasher {

    companion object {
        private const val COLOR_GRID = 8          // 8x8 grid → 64-bit average-color hash
        private const val RADIAL_RINGS = 8        // 8 concentric rings, 8 bits each → 64-bit hash
    }

    // heroId -> (slotType -> cached TripleHash), populated by PortraitMatcher.preloadHashes.
    //
    // Keyed per SlotType (not just heroId) because PortraitAssetManager now produces
    // differently-sized reference assets per slot (hero.pick.png / hero.ban.png) that
    // better match the resolution of what's actually captured on-screen for that slot
    // type, rather than hashing one shared full-size portrait for every slot.
    private val heroHashes = HashMap<Int, MutableMap<SlotType, TripleHash>>()

    /** Precompute and cache the triple-hash for [heroId]/[slotType] from its already-normalized portrait. */
    fun cacheHero(heroId: Int, slotType: SlotType, normalizedPortrait: Bitmap) {
        heroHashes.getOrPut(heroId) { mutableMapOf() }[slotType] = computeTripleHash(normalizedPortrait)
    }

    /** Back-compat overload — caches under [SlotType.CENTER]. */
    fun cacheHero(heroId: Int, normalizedPortrait: Bitmap) = cacheHero(heroId, SlotType.CENTER, normalizedPortrait)

    fun hasCached(heroId: Int, slotType: SlotType = SlotType.CENTER): Boolean =
        heroHashes[heroId]?.containsKey(slotType) == true

    /**
     * Computes a [TripleHash] for [normalizedBitmap] (already [PortraitNormalizer.normalizeForSlot]'d).
     */
    fun computeTripleHash(normalizedBitmap: Bitmap): TripleHash {
        val structural = PerceptualHash.computePHash(normalizedBitmap)
        val color = computeAverageColorHash(normalizedBitmap)
        val radial = computeRadialGradientHash(normalizedBitmap)
        return TripleHash(structural, color, radial)
    }

    /**
     * Best-matching hero from the cached roster for [slotHash], scored with
     * [fusedSimilarity]. Returns null if no heroes are cached.
     */
    fun bestMatch(slotHash: TripleHash, candidateHeroIds: Collection<Int>, slotType: SlotType): Pair<Int, Float>? {
        var bestId: Int? = null
        var bestSim = -1f
        for (heroId in candidateHeroIds) {
            // Prefer the slot-specific reference hash (built from the correctly-sized
            // PICK/BAN asset); fall back to the CENTER hash if that variant isn't cached yet.
            val hashesForHero = heroHashes[heroId] ?: continue
            val heroHash = hashesForHero[slotType] ?: hashesForHero[SlotType.CENTER] ?: continue
            val sim = fusedSimilarity(slotHash, heroHash, slotType)
            if (sim > bestSim) {
                bestSim = sim
                bestId = heroId
            }
        }
        return bestId?.let { it to bestSim }
    }

    /**
     * Fused similarity in [0,1] (1 = identical). Weights follow recommendations.md §5.2's
     * table: BAN slots lean on the structural hash (occlusion-resistant edges), PICK slots
     * lean on colour (bottom-strip mask leaves the full portrait colour palette intact).
     */
    fun fusedSimilarity(a: TripleHash, b: TripleHash, slotType: SlotType): Float =
        1f - fusedDistance(a, b, slotType)

    fun fusedDistance(a: TripleHash, b: TripleHash, slotType: SlotType): Float {
        val wStructural: Float
        val wColor: Float
        val wRadial: Float
        when (slotType) {
            SlotType.BAN -> { wStructural = 0.55f; wColor = 0.25f; wRadial = 0.20f }
            SlotType.PICK -> { wStructural = 0.40f; wColor = 0.45f; wRadial = 0.15f }
            SlotType.CENTER -> { wStructural = 0.50f; wColor = 0.30f; wRadial = 0.20f }
        }

        val dStructural = java.lang.Long.bitCount(a.structural xor b.structural) / 63f
        val dColor = java.lang.Long.bitCount(a.color xor b.color) / 64f
        val dRadial = radialDistance(a.radial, b.radial)

        return wStructural * dStructural + wColor * dColor + wRadial * dRadial
    }

    // ── Custom average-color hash (grid-based, no java.awt) ─────────────────

    /**
     * Divides the normalized bitmap into an 8×8 grid, computes the mean luminance
     * per cell, and sets a bit when a cell is brighter than the overall mean.
     * Plays the same discriminative role as JImageHash's AverageColorHash without
     * requiring a JVM-only dependency.
     */
    private fun computeAverageColorHash(bitmap: Bitmap): Long {
        val size = PortraitNormalizer.NORMALIZED_SIZE
        val cellSize = size / COLOR_GRID
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)

        val cellMeans = FloatArray(COLOR_GRID * COLOR_GRID)
        var overallSum = 0f

        for (cy in 0 until COLOR_GRID) {
            for (cx in 0 until COLOR_GRID) {
                var sum = 0f
                var count = 0
                val startX = cx * cellSize
                val startY = cy * cellSize
                for (y in startY until (startY + cellSize).coerceAtMost(size)) {
                    for (x in startX until (startX + cellSize).coerceAtMost(size)) {
                        sum += PerceptualHash.luminance(pixels[y * size + x])
                        count++
                    }
                }
                val mean = if (count > 0) sum / count else 0f
                cellMeans[cy * COLOR_GRID + cx] = mean
                overallSum += mean
            }
        }

        val overallMean = overallSum / cellMeans.size
        var hash = 0L
        for (i in cellMeans.indices) {
            if (cellMeans[i] > overallMean) hash = hash or (1L shl i)
        }
        return hash
    }

    // ── Custom radial gradient hash ──────────────────────────────────────────

    /**
     * Divides the normalized bitmap into [RADIAL_RINGS] concentric rings around
     * its center, computes mean luminance per ring, and quantizes each ring's
     * value into 8 bits — packed into a 64-bit hash. Captures MLBB's signature
     * center-bright → edge-dark portrait vignette style, and is naturally
     * robust to corner badges / bottom name strips since those only touch the
     * outermost ring.
     */
    private fun computeRadialGradientHash(bitmap: Bitmap): Long {
        val size = PortraitNormalizer.NORMALIZED_SIZE
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)

        val cx = size / 2f
        val cy = size / 2f
        val maxRadius = sqrt(cx * cx + cy * cy)
        val ringSums = LongArray(RADIAL_RINGS)
        val ringCounts = IntArray(RADIAL_RINGS)

        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - cx
                val dy = y - cy
                val dist = sqrt((dx * dx + dy * dy).toDouble())
                val ring = ((dist / maxRadius) * RADIAL_RINGS).toInt().coerceIn(0, RADIAL_RINGS - 1)
                val lum = PerceptualHash.luminance(pixels[y * size + x]).toLong()
                ringSums[ring] += lum
                ringCounts[ring]++
            }
        }

        var hash = 0L
        for (i in 0 until RADIAL_RINGS) {
            val mean = if (ringCounts[i] > 0) ringSums[i] / ringCounts[i] else 0L
            val quantized = (mean / 4).coerceIn(0, 63) // 6-bit value per ring, room for 8-bit slot
            hash = hash or (quantized shl (i * 8))
        }
        return hash
    }

    private fun radialDistance(a: Long, b: Long): Float {
        var diff = 0
        for (i in 0 until RADIAL_RINGS) {
            val va = (a shr (i * 8)) and 0xFF
            val vb = (b shr (i * 8)) and 0xFF
            diff += abs(va - vb).toInt()
        }
        return diff.toFloat() / (RADIAL_RINGS * 63)
    }
}
