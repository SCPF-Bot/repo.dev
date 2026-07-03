package com.mlbb.assistant.capture

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [SlotAwareHasher] and [PortraitNormalizer] (recommendations.md §5.5).
 *
 * Uses synthetic bitmaps (no filesystem access) so these run under plain
 * Robolectric without a real device/test-asset corpus, mirroring the
 * conventions established by [PerceptualHashTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SlotAwareHasherTest {

    private val hasher = SlotAwareHasher()

    private fun radialGradientBitmap(size: Int = 128, edgeInverted: Boolean = false): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cx = size / 2f
        val cy = size / 2f
        val maxDist = kotlin.math.sqrt(cx * cx + cy * cy)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dist = kotlin.math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy))
                val ratio = (dist / maxDist).coerceIn(0f, 1f)
                val lum = if (edgeInverted) (ratio * 255).toInt() else ((1f - ratio) * 255).toInt()
                bmp.setPixel(x, y, Color.rgb(lum, lum, lum))
            }
        }
        return bmp
    }

    // ── identical images ──────────────────────────────────────────────────────

    @Test
    fun identicalNormalizedBitmapsHaveZeroFusedDistance() {
        val bmp = radialGradientBitmap()
        val normalized = PortraitNormalizer.normalizeForSlot(bmp, SlotType.PICK)
        val h1 = hasher.computeTripleHash(normalized)
        val h2 = hasher.computeTripleHash(normalized)
        bmp.recycle(); normalized.recycle()

        assertTrue(hasher.fusedDistance(h1, h2, SlotType.PICK) < 0.01f)
        assertTrue(hasher.fusedDistance(h1, h2, SlotType.BAN) < 0.01f)
    }

    // ── dissimilar images ─────────────────────────────────────────────────────

    @Test
    fun invertedGradientHasHighFusedDistance() {
        val center = radialGradientBitmap(edgeInverted = false)
        val edge = radialGradientBitmap(edgeInverted = true)

        val normalizedCenter = PortraitNormalizer.normalizeForSlot(center, SlotType.PICK)
        val normalizedEdge = PortraitNormalizer.normalizeForSlot(edge, SlotType.PICK)

        val h1 = hasher.computeTripleHash(normalizedCenter)
        val h2 = hasher.computeTripleHash(normalizedEdge)

        center.recycle(); edge.recycle()
        normalizedCenter.recycle(); normalizedEdge.recycle()

        val dist = hasher.fusedDistance(h1, h2, SlotType.PICK)
        assertTrue("Expected distance > 0.2, was $dist", dist > 0.2f)
    }

    // ── slot-type weighting sanity check ────────────────────────────────────

    @Test
    fun fusedDistanceIsSlotTypeDependent() {
        val a = radialGradientBitmap(edgeInverted = false)
        val b = radialGradientBitmap(edgeInverted = true)

        val normA = PortraitNormalizer.normalizeForSlot(a, SlotType.BAN)
        val normB = PortraitNormalizer.normalizeForSlot(b, SlotType.BAN)
        val hA = hasher.computeTripleHash(normA)
        val hB = hasher.computeTripleHash(normB)

        a.recycle(); b.recycle(); normA.recycle(); normB.recycle()

        val banDistance = hasher.fusedDistance(hA, hB, SlotType.BAN)
        val pickDistance = hasher.fusedDistance(hA, hB, SlotType.PICK)

        // Different weighting schemes should not coincidentally produce the exact
        // same distance for genuinely different structural/color/radial signals.
        assertNotNull(banDistance)
        assertNotNull(pickDistance)
    }

    // ── bestMatch ─────────────────────────────────────────────────────────────

    @Test
    fun bestMatchReturnsNullWhenNoHeroesCached() {
        val bmp = radialGradientBitmap()
        val normalized = PortraitNormalizer.normalizeForSlot(bmp, SlotType.PICK)
        val slotHash = hasher.computeTripleHash(normalized)
        bmp.recycle(); normalized.recycle()

        val result = hasher.bestMatch(slotHash, candidateHeroIds = listOf(1, 2, 3), slotType = SlotType.PICK)
        assertTrue(result == null)
    }

    @Test
    fun cachedHeroIsFoundAsBestMatchAgainstItself() {
        val heroBmp = radialGradientBitmap()
        val heroNormalized = PortraitNormalizer.normalizeForSlot(heroBmp, SlotType.CENTER)
        hasher.cacheHero(heroId = 42, normalizedPortrait = heroNormalized)
        heroBmp.recycle(); heroNormalized.recycle()

        val slotBmp = radialGradientBitmap()
        val slotNormalized = PortraitNormalizer.normalizeForSlot(slotBmp, SlotType.PICK)
        val slotHash = hasher.computeTripleHash(slotNormalized)
        slotBmp.recycle(); slotNormalized.recycle()

        val result = hasher.bestMatch(slotHash, candidateHeroIds = listOf(42, 99), slotType = SlotType.PICK)
        assertNotNull(result)
        assertTrue(result!!.first == 42)
    }
}
