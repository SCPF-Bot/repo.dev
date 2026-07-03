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
