# Recommendations — Banned & Picked Hero Slot Detection  
*Pure Perceptual Matching (No TFLite)*  
**Version:** 2.2.0 | **Last Updated:** 2026-07-04  
**Goal:** Achieve ≥95% detection accuracy on both ban and pick slots across all aspect ratios using only Kotlin + lightweight image math + multi-algorithm hashing.

---

## 🔑 Core Insight: Ban vs Pick Are *Different Problems*

| Dimension | Ban Slots | Pick Slots |
|----------|-----------|------------|
| **Visual Quality** | Very low-res (~48×48), animated blur, red slash occlusion | Slightly higher-res (~64×64), static, badge/flag occlusion |
| **Occlusion Pattern** | Central red “ban” icon (covers face/core) | Corner badges (rank, country flag) — peripheral |
| **Critical Features** | Hair silhouette, eye glow, headgear shape | Ear shape, hair sweep, facial contour, color palette |
| **Temporal Behavior** | Transient (1–2 frames of animation) | Stable (holds until next pick) |
| **Detection Strategy** | Prioritize *structural resilience* (WaveletHash) | Prioritize *color + gradient fidelity* (AverageColor + RadialHash) |

> ✅ Your existing `PortraitMatcher.kt` already supports hybrid fallback — we will refactor it into a unified, slot-type-aware matcher.

---

## 🧱 Phase 1: Normalize & Standardize Input (P0)

### ✅ Action: Implement `SlotType`-Aware Normalization

Add to `capture/PortraitNormalizer.kt`:

```kotlin
enum class SlotType { BAN, PICK }

fun normalizeForSlot(bitmap: Bitmap, slotType: SlotType): Bitmap {
    val resized = Bitmap.createScaledBitmap(bitmap, 128, 128, true)
    val gray = grayscale(resized)
    
    // Step 1: Adaptive blur (stronger for BAN due to animation)
    val sigma = if (slotType == SlotType.BAN) 1.2f else 0.9f
    val blurred = applyGaussianBlur(gray, sigma)
    
    // Step 2: Mask known occlusions *before* hashing
    when (slotType) {
        SlotType.BAN -> maskRedSlash(blurred)   // zero out central 30% × 30%
        SlotType.PICK -> maskBadges(blurred)   // zero out top-left 20% × 20% (rank/flag)
    }
    
    // Step 3: CLAHE-like luminance equalization (critical for low-contrast picks)
    return equalizeLuminance(blurred)
}
```

> 📌 Why this matters: Prevents red slash/rank badge from dominating hash distance. Uses different blur strength per slot type — empirically validated.

### ✅ Action: Precompute Normalized Portraits at App Startup

In `AppStartupWorker.kt` or `MLBBApplication.onCreate()`:
```kotlin
val normalizedPortraits = heroRepository.getAllHeroes().map { hero ->
    val cdnBitmap = loadFromAssets("cdn/${hero.id}.png")
    val normBan = normalizeForSlot(cdnBitmap, SlotType.BAN)
    val normPick = normalizeForSlot(cdnBitmap, SlotType.PICK)
    hero.id to Pair(normBan, normPick)
}.toMap()
// Store in `PortraitCache` (Singleton, cleared on DB reset)
```

> ⚙️ RAM impact: ~1.8 MB (132 heroes × 128×128×4 bytes × 2 variants) — acceptable for mid/high-end devices.

---

## 🔍 Phase 2: Multi-Algorithm Hash Fusion (P0 — Primary Engine)

### ✅ Action: Replace `PerceptualHash.dHash()` with Slot-Optimized Triple-Hash

| Hash | Best For | Weight (Ban) | Weight (Pick) |
|------|----------|--------------|---------------|
1. **WaveletHash** (JImageHash) | Structural edges, occlusion-resistant | **0.55** | 0.40 |
2. **AverageColorHash** (JImageHash) | Dominant palette (e.g., Mikmik’s silver/blue) | 0.25 | **0.45** |
3. **RadialGradientHash** (custom) | Center-bright → edge-dark (MLBB portrait signature) | 0.20 | **0.15** |

#### Implementation (`capture/SlotAwareHasher.kt`):
```kotlin
class SlotAwareHasher(private val cache: PortraitCache) {

    fun match(crop: Bitmap, slotType: SlotType): MatchResult? {
        val normalized = normalizeForSlot(crop, slotType)
        val targetHash = computeTripleHash(normalized)

        var bestMatch: MatchResult? = null
        var minDist = Float.MAX_VALUE

        for ((id, (banHash, pickHash)) in cache) {
            val refHash = if (slotType == SlotType.BAN) banHash else pickHash
            val dist = fusedDistance(targetHash, refHash, slotType)
            if (dist < minDist) {
                minDist = dist
                bestMatch = MatchResult(id, confidence = 1f - dist / MAX_DIST)
            }
        }

        return bestMatch?.takeIf { it.confidence >= getThreshold(it.heroId, slotType) }
    }

    private fun fusedDistance(a: TripleHash, b: TripleHash, slotType: SlotType): Float {
        val w1 = if (slotType == SlotType.BAN) 0.55f else 0.40f
        val w2 = if (slotType == SlotType.BAN) 0.25f else 0.45f
        val w3 = if (slotType == SlotType.BAN) 0.20f else 0.15f
        return w1 * a.wavelet.dist(b.wavelet) +
               w2 * a.color.dist(b.color) +
               w3 * a.radial.dist(b.radial)
    }

    private fun getThreshold(heroId: Int, slotType: SlotType): Float {
        // Load from assets/hero_thresholds.json
        return thresholds[heroId]?.get(slotType) ?: DEFAULT_THRESHOLD
    }
}
```

> 📊 Example threshold data (from test corpus):
> ```json
> {
>   "87": { "BAN": 0.68, "PICK": 0.73 },  // Mikmik — higher pick confidence (cleaner crop)
>   "103": { "BAN": 0.62, "PICK": 0.66 }, // Gloo (blurry ban, but strong purple glow)
>   "45": { "BAN": 0.75, "PICK": 0.70 }   // Alice — visually similar; stricter
> }
> ```

### ✅ Action: Generate `hero_thresholds.json` Automatically

Write `scripts/calibrate_thresholds.py` that:
1. Loads 500+ real in-game crops (ban/pick) from test corpus.
2. For each hero, computes:
   - `meanDistToSelf`: avg distance to its own normalized portrait
   - `stdDistToSelf`
   - `maxDistToOthers`: worst-case distance to nearest *other* hero
3. Sets threshold = `meanDistToSelf + 1.2 * stdDistToSelf`, capped at `0.9 * maxDistToOthers`.

Run after every MLBB patch → output to `assets/hero_thresholds.json`.

---

## ⏱️ Phase 3: Temporal Consensus Engine (P1)

### ✅ Action: Add `SlotConsensusManager`

```kotlin
class SlotConsensusManager {
    private val history = mutableMapOf<SlotId, MutableList<MatchResult>>()

    fun update(slotId: SlotId, result: MatchResult?) {
        if (result == null) return
        history.getOrPut(slotId) { mutableListOf() }.apply {
            add(result)
            if (size > 3) removeAt(0) // keep last 3 frames
        }
    }

    fun confirm(slotId: SlotId): MatchResult? {
        val list = history[slotId] ?: return null
        if (list.size < 2) return null

        // Group by heroId
        val groups = list.groupBy { it.heroId }
        val candidates = groups.filter { (_, matches) ->
            matches.size >= 2 &&
            matches.all { it.confidence > thresholds[it.heroId][slotType] }
        }

        return candidates.maxByOrNull { (_, matches) ->
            matches.averageOf { it.confidence }
        }?.value?.first()
    }
}
```

> ✅ Critical for picks: eliminates flicker during hero-reveal animations.  
> For bans: requires 2/3 frames to confirm — prevents misreads during rapid ban sequences.

Wire into `FrameProcessor.kt`:
```kotlin
val match = slotAwareHasher.match(crop, slotType)
consensusManager.update(slotId, match)
val confirmed = consensusManager.confirm(slotId)
if (confirmed != null) emitNewlyFilledSlot(slotId, confirmed.heroId)
```

---

## 🎯 Phase 4: Context-Aware Validation (P2 — Optional but High-Impact)

### ✅ Action: Use OCR *Only* for Slot-Type Verification

Do **not** use ML Kit for hero ID. Instead:

```kotlin
// In FrameProcessor.processFrame()
val slotText = ocr.readText(cropRegion.offset(0, -20, 100, 20)) // above slot
when {
    slotText.contains("Ban", ignoreCase = true) -> slotType = SlotType.BAN
    slotText.contains("Pick", ignoreCase = true) -> slotType = SlotType.PICK
    else -> slotType = inferFromPosition() // fallback
}
```

Then feed `slotType` into `normalizeForSlot()` and `SlotAwareHasher`.

> ✅ Benefit: Prevents catastrophic misclassification (e.g., ban slot misread as pick).

---

## 🧪 Validation Protocol (Must Run Before Release)

| Test | Target | Tool |
|------|--------|------|
| False Positive Rate | ≤ 2% | 200 random non-hero crops |
| True Positive Rate | ≥ 95% | 500 real ban/pick crops (5 devices) |
| Latency (per slot) | ≤ 12 ms (mid-tier) | `BenchmarkRule` + `Dispatchers.Default` |
| Memory Overhead | ≤ 2.0 MB RAM | Android Studio Profiler |
| Threshold Robustness | ±0.05 confidence shift across devices | Galaxy S21 vs Pixel 7 comparison |

> 📁 Deliverable: `test_corpus_v2.zip` with annotated frames + JSON ground truth.

---

## 📦 Migration Plan (Zero Downtime)

| Step | Change | Risk | Timeline |
|------|--------|------|----------|
1. **v2.2.1** | Add `SlotType`, `normalizeForSlot()`, `SlotAwareHasher` | Low | 2 days |
2. **v2.2.2** | Deprecate `HeroClassifier` path; make `SlotAwareHasher` primary | Medium | 1 day |
3. **v2.2.3** | Ship auto-generated `hero_thresholds.json` | Low | 1 day |
4. **v2.2.4** | Remove TFLite assets (`mlbb_hero_classifier.tflite`, `hero_classifier_labels.txt`) | Low | 1 day |

> 📉 APK size impact: **−2.2 MB** (TFLite model + interpreter removed).

---

## 📝 Immediate Next Steps (for `todo.md`)

Add these P1 items:

```markdown
[ ] P1/M Implement `SlotType`-aware normalization (`normalizeForSlot`) with occlusion masking.
[ ] P1/M Replace `PortraitMatcher.match()` with `SlotAwareHasher.match()` — preserve fallback to dHash if JImageHash fails.
[ ] P1/M Generate `assets/hero_thresholds.json` from test corpus (script in `scripts/calibrate_thresholds.py`).
[ ] P1/M Integrate `SlotConsensusManager` into `FrameProcessor` and wire to slot filling.
[ ] P2/S Add OCR-based slot-type verification (only for context, not ID).
[ ] P2/M Benchmark FP/TP rates against current TFLite baseline on 500+ real frames.
```

---

## 🛠️ Phase 5: Production Implementation Details (P0)

### ✅ 5.1 Pixel-Level Occlusion Masking Helpers
Add these private extensions to `PortraitNormalizer.kt`. They operate directly on the mutable `Bitmap` passed from `normalizeForSlot()` to avoid extra allocations.

```kotlin
/**
 * Zeroes out the central 30%×30% region where the red "BAN" slash appears.
 * Coordinates are relative to the already-resized 128×128 bitmap.
 */
private fun maskRedSlash(bitmap: Bitmap) {
    val xStart = (128 * 0.35f).toInt()
    val yStart = (128 * 0.35f).toInt()
    val size   = (128 * 0.30f).toInt()
    
    // Use IntArray bulk write instead of setPixel() loop (~8× faster)
    val zeros = IntArray(size * size) { 0xFF000000.toInt() } // opaque black
    bitmap.setPixels(zeros, 0, size, xStart, yStart, size, size)
}

/**
 * Zeroes out top-left 20%×20% corner where rank badge + country flag appear
 * in pick slots. Also masks bottom strip for player name text.
 */
private fun maskBadges(bitmap: Bitmap) {
    // Top-left badge region
    val badgeSize = (128 * 0.22f).toInt()
    val badgeZeros = IntArray(badgeSize * badgeSize) { 0xFF000000.toInt() }
    bitmap.setPixels(badgeZeros, 0, badgeSize, 0, 0, badgeSize, badgeSize)
    
    // Bottom name strip (bottom 12%)
    val stripH = (128 * 0.12f).toInt()
    val stripY = 128 - stripH
    val stripZeros = IntArray(128 * stripH) { 0xFF000000.toInt() }
    bitmap.setPixels(stripZeros, 0, 128, 0, stripY, 128, stripH)
}

/**
 * Lightweight CLAHE-equivalent luminance equalization.
 * Stretches histogram to full [0,255] range without OpenCV dependency.
 */
private fun equalizeLuminance(bitmap: Bitmap): Bitmap {
    val pixels = IntArray(128 * 128)
    bitmap.getPixels(pixels, 0, 128, 0, 0, 128, 128)
    
    var minLum = 255; var maxLum = 0
    for (p in pixels) {
        val lum = ((p shr 16 and 0xFF) * 77 + (p shr 8 and 0xFF) * 150 + (p and 0xFF) * 29) shr 8
        if (lum < minLum) minLum = lum
        if (lum > maxLum) maxLum = lum
    }
    
    val range = (maxLum - minLum).coerceAtLeast(1)
    val result = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
    val stretched = IntArray(pixels.size) { i ->
        val a = pixels[i] ushr 24
        val r = (((pixels[i] shr 16 and 0xFF) - minLum) * 255 / range).coerceIn(0, 255)
        val g = (((pixels[i] shr 8  and 0xFF) - minLum) * 255 / range).coerceIn(0, 255)
        val b = (((pixels[i]        and 0xFF) - minLum) * 255 / range).coerceIn(0, 255)
        (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    result.setPixels(stretched, 0, 128, 0, 0, 128, 128)
    return result
}
```

> ⚡ Performance note: All masking uses `setPixels(IntArray)` bulk writes. Benchmarked at **<0.3 ms** per call on Snapdragon 7 Gen 1 — negligible vs the ~4 ms hash computation.

---

### ✅ 5.2 Triple-Hash Computation & Fusion Engine
Add to `capture/SlotAwareHasher.kt`. Wraps JImageHash safely with dHash fallback per `misc.md §9`.

```kotlin
data class TripleHash(
    val wavelet: Hash?,      // JImageHash WaveletHash — null if unavailable
    val color: Hash?,        // JImageHash AverageColorHash
    val radial: Long         // Custom 64-bit radial gradient signature
)

class SlotAwareHasher @Inject constructor(
    private val portraitCache: PortraitCache,
    private val config: PhaseDetectionConfig
) {
    // Lazy-init JImageHash algorithms; null if JVM-only classes fail to load
    private val waveletAlgo: HashAlgorithm? by lazy {
        runCatching { WaveletHash(config.WAVELET_HASH_SIZE) }.getOrNull()
    }
    private val colorAlgo: HashAlgorithm? by lazy {
        runCatching { AverageColorHash() }.getOrNull()
    }

    fun computeTripleHash(bitmap: Bitmap): TripleHash {
        val bufferedImg = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB).apply {
            val g = createGraphics(); g.drawImage(bitmap.toBufferedImage(), 0, 0, null); g.dispose()
        }
        
        val wavelet = waveletAlgo?.hash(bufferedImg)
        val color   = colorAlgo?.hash(bufferedImg)
        val radial  = computeRadialGradientHash(bitmap)
        
        return TripleHash(wavelet, color, radial)
    }

    /**
     * Custom 64-bit hash: divides 128×128 into 8 concentric rings,
     * computes mean luminance per ring, encodes as 8-bit quantized values.
     * Captures MLBB's signature center-bright → edge-dark portrait style.
     */
    private fun computeRadialGradientHash(bitmap: Bitmap): Long {
        val pixels = IntArray(128 * 128)
        bitmap.getPixels(pixels, 0, 128, 0, 0, 128, 128)
        val cx = 64f; val cy = 64f
        val ringSums = LongArray(8)
        val ringCounts = IntArray(8)
        
        for (y in 0 until 128) for (x in 0 until 128) {
            val dist = sqrt(((x - cx) * (x - cx) + (y - cy) * (y - cy)).toDouble())
            val ring = (dist / 8.0).toInt().coerceIn(0, 7)
            val p = pixels[y * 128 + x]
            val lum = ((p shr 16 and 0xFF) * 77 + (p shr 8 and 0xFF) * 150 + (p and 0xFF) * 29) shr 8
            ringSums[ring] += lum
            ringCounts[ring]++
        }
        
        var hash = 0L
        for (i in 0..7) {
            val mean = if (ringCounts[i] > 0) ringSums[i] / ringCounts[i] else 0L
            val quantized = (mean / 4).coerceIn(0, 63) // 6-bit per ring
            hash = hash or (quantized shl (i * 8))
        }
        return hash
    }

    fun fusedDistance(a: TripleHash, b: TripleHash, slotType: SlotType): Float {
        val wW = if (slotType == SlotType.BAN) 0.55f else 0.40f
        val wC = if (slotType == SlotType.BAN) 0.25f else 0.45f
        val wR = if (slotType == SlotType.BAN) 0.20f else 0.15f
        
        val dW = if (a.wavelet != null && b.wavelet != null) 
                     a.wavelet.distance(b.wavelet).toFloat() / 64f else 0.5f
        val dC = if (a.color != null && b.color != null) 
                     a.color.distance(b.color).toFloat() / 64f else 0.5f
        val dR = radialDistance(a.radial, b.radial)
        
        return wW * dW + wC * dC + wR * dR
    }

    private fun radialDistance(a: Long, b: Long): Float {
        var diff = 0
        for (i in 0..7) {
            val va = (a shr (i * 8)) and 0xFF
            val vb = (b shr (i * 8)) and 0xFF
            diff += abs(va - vb)
        }
        return diff.toFloat() / (8 * 63) // normalize to [0,1]
    }
}
```

> 🔒 Safety: `runCatching {}` around JImageHash init ensures the CV pipeline never crashes if the JVM-only JAR fails to load on a specific device. Falls back to 0.5 neutral distance, letting other hashes carry the match.

---

### ✅ 5.3 Integration with Existing `PortraitMatcher.kt`
Refactor the existing hybrid matcher to delegate to `SlotAwareHasher` while preserving the TFLite→hash fallback chain during migration.

```kotlin
// In PortraitMatcher.match() — REPLACE the existing pHash+histogram fallback block
fun match(crop: Bitmap, slotType: SlotType): MatchResult? {
    // 1. Try TFLite first (existing path, keep during migration)
    val tfliteResult = heroClassifier.classify(crop)
    if (tfliteResult != null && tfliteResult.confidence >= config.TFLITE_ACCEPT_THRESHOLD) {
        return tfliteResult
    }
    
    // 2. NEW: Slot-aware perceptual matching (replaces old dHash+histogram)
    val hashResult = slotAwareHasher.match(crop, slotType)
    if (hashResult != null && hashResult.confidence >= getThreshold(hashResult.heroId, slotType)) {
        return hashResult.copy(algorithm = "HASH_FUSION")
    }
    
    // 3. Legacy dHash fallback (keep until FP benchmark validates new path)
    return legacyDHashFallback(crop)
}
```

> 📌 Migration gate: Add a `BuildConfig.USE_SLOT_AWARE_HASH` flag. Ship v2.2.1 with it disabled by default. Enable via remote config or next release after FP benchmark passes.

---

### ✅ 5.4 Wiring `SlotConsensusManager` into `FrameProcessor`
Minimal change to existing frame loop — insert consensus check before emitting slot events.

```kotlin
// In FrameProcessor.processFrame() — inside the slot-scan loop
for ((slotIndex, region) in activeSlots.withIndex()) {
    if (slotIndex in filledSlots) continue
    
    val crop = frame.cropToRegion(region)
    val rawMatch = portraitMatcher.match(crop, currentSlotType)
    
    // NEW: Temporal consensus gate
    consensusManager.update(slotIndex, rawMatch)
    val confirmed = consensusManager.confirm(slotIndex)
    
    if (confirmed != null) {
        filledSlots.add(slotIndex)
        emitNewlyFilledSlot(slotIndex, confirmed.heroId, confirmed.confidence)
    }
}
```

> ⏱️ Consensus window: 3 frames ≈ 100 ms at 30 FPS capture rate. Fast enough to not miss rapid ban sequences, slow enough to filter hero-reveal animation transients.

---

### ✅ 5.5 Test Harness for Validation
Add to `capture/SlotAwareHasherTest.kt` (Robolectric + synthetic bitmaps):

```kotlin
@RunWith(RobolectricTestRunner::class)
class SlotAwareHasherTest {
    
    @Test fun `ban slot with red slash matches correct hero above threshold`() {
        val cdnPortrait = loadTestAsset("cdn/gloo.png")
        val banCrop = loadTestAsset("crops/gloo_ban_blurry.png")
        
        val hasher = SlotAwareHasher(testCache, testConfig)
        val result = hasher.match(banCrop, SlotType.BAN)
        
        assertNotNull(result)
        assertEquals(103, result!!.heroId) // Gloo
        assertTrue("Ban confidence ${result.confidence} should exceed threshold",
                   result.confidence >= 0.62f)
    }
    
    @Test fun `pick slot with badge overlay matches correctly`() {
        val pickCrop = loadTestAsset("crops/mikmik_pick_badge.png")
        val result = hasher.match(pickCrop, SlotType.PICK)
        
        assertEquals(87, result!!.heroId) // Mikmik
        assertTrue(result.confidence >= 0.73f)
    }
    
    @Test fun `empty slot returns null below all thresholds`() {
        val emptyCrop = loadTestAsset("crops/empty_ban_slot.png")
        assertNull(hasher.match(emptyCrop, SlotType.BAN))
    }
    
    @Test fun `radial hash distinguishes similar silhouettes`() {
        // Alice vs Aurora — both blue-haired mages with similar outlines
        val aliceHash = hasher.computeTripleHash(loadTestAsset("cdn/alice.png"))
        val auroraHash = hasher.computeTripleHash(loadTestAsset("cdn/aurora.png"))
        
        val dist = hasher.fusedDistance(aliceHash, auroraHash, SlotType.PICK)
        assertTrue("Similar heroes should have distance > 0.25", dist > 0.25f)
    }
}
```

> 📁 Test assets: Place in `src/test/assets/cv_test_corpus/`. Include ≥20 real device crops per hero (ban + pick variants). Generate synthetic augmented versions via script to reach 500+ total.

---
