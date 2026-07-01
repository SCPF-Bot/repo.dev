# MLBB Draft Assistant — Detection & Architecture Ideas

Compiled from research and technical discussion, July 2026. Cross-reference against `docs/mission.md` before treating any item here as authoritative — this file supplements it, not replaces it.

**Problem in scope:** reliably detecting which heroes occupy the ban slots and pick slots on the MLBB draft screen, from MediaProjection screen capture, across varying Android aspect ratios.

---

## 1. Detection Pipeline Architecture

**Treat ban slots and pick slots as separate detectors.**
- *Ban slots* (top strip, both teams): small ~50px portraits with a red "no" overlay icon centered on the face. The overlay occludes the middle of the portrait — mask out the center ~30% of the crop before hashing, in both the reference set and the live capture, or the overlay itself gets measured instead of the hero.
- *Pick slots* (left panel = own team, right panel = enemy "Player 1–5"): toggle between a placeholder state (role icon + username text, no hero) and a locked-in state (portrait fills the circle).

**Add an occupied/empty classifier before matching.** A cheap pixel-variance or edge-density check per pick slot, run before attempting hero ID, avoids wasting matching cycles on empty slots and prevents false matches against placeholder avatars/text.

**Coordinate system.** Define each slot region as `{x_offset_pct, y_offset_pct_from_edge, diameter_pct_of_height}` — height-normalized, edge-anchored — rather than raw pixels, to stay accurate across the 16:9–20:9 Android aspect-ratio range.

**Two-stage matching strategy.**
1. Fast first-pass filter (perceptual hash / classical CV) narrows ~125 heroes to a small candidate set.
2. When the top-2 candidates are too close to trust, escalate to a trained model (Section 3) for disambiguation.

No project surveyed here — LoL, Dota, or MLBB — relies on OCR or raw template matching alone for portrait ID. It's always hash-or-embed-and-nearest-neighbor.

**Separate confidence thresholds for ban vs. pick slots.** Ban portraits are smaller and more compression-affected than pick portraits — use two thresholds (or two pre-scaled reference sets) rather than one universal matcher.

---

## 2. Reference Image Sourcing

| Source | Notes |
|---|---|
| [p3hndrx/MLBB-API](https://github.com/p3hndrx/MLBB-API) | Community-maintained hero/item/emblem JSON; manually kept in sync, can lag game updates |
| [ridwaanhall/api-mobilelegends](https://github.com/ridwaanhall/api-mobilelegends) | Public FastAPI hero-data API, sourced from public content, credits Moonton |
| [mobaguides/mobile-legends-api](https://github.com/mobaguides/mobile-legends-api) (PHP) | Exposes a `heroAvatar()` fetcher + documents Moonton's own icon-mapping endpoint (`mapi.mobilelegends.com/api/icon`) — undocumented, treat as fragile |
| [Kaggle MLBB Hero Dataset](https://www.kaggle.com/datasets/christina0512/mobile-legends-bang-bang-mlbb-hero-dataset) | Pre-packaged image dataset, starting point if you don't want to build your own |

Clean reference crops from any of these beat screenshotting your own device — no double-compression or scaling artifacts baked in twice.

---

## 3. Matching / Model Options, Ranked by Effort

1. **[JImageHash](https://github.com/KilianB/JImageHash)** (pure Java, no JNI) — aHash, dHash, DCT-based pHash, wavelet hash, compared via Hamming distance. Best starting point; lets you A/B multiple algorithms in one library.
2. **[pHashCalc](https://github.com/avbase/pHashCalc)** (Kotlin, Maven Central, API 26+) / **[kophash](https://github.com/shiveenp/kophash)** (pure Kotlin pHash) — lighter single-algorithm alternatives.
3. **TFLite Model Maker** — fine-tune EfficientNet-Lite/MobileNetV2/ResNet50 on your own labeled hero crops; exports a ready Android classifier with metadata. The "hashing isn't enough" escalation tier.
4. **Siamese/embedding network** — academically standard approach for small closed-set icon/logo recognition: fine-tuned backbone produces an embedding per portrait, compared by learned distance, nearest-neighbor at runtime. Effectively a hand-rolled mini-CLIP scoped to your roster.
5. **[MobileCLIP / MobileCLIP2](https://github.com/apple/ml-mobileclip)** (Apple, open-source) — S0 variant matches OpenAI ViT-B/16 CLIP quality at 4.8x faster / 2.8x smaller; [quantized TFLite ports already exist](https://huggingface.co/anton96vice/mobileclip2_tflite). Overkill for a fixed ~125-class set — reach for this only if device/lighting variance breaks the simpler stack in real testing.

---

## 4. OCR & Phase Detection

- **ML Kit Text Recognition v2** — Latin + CJK + Devanagari, offline, solid block/paragraph detection. Use for: occupied/empty slot text ("Player N"), ban-phase counters, timer.
- Constrain OCR against `RankRuleEngine`'s known ban-count structure (6/8/10 by rank) so it only has to **confirm** a phase transition, not guess cold.
- **ML Kit GenAI Image Description** (Gemini Nano / AICore, Nano 4 rolling out through 2026) — not a primary matcher (non-deterministic, heavier), but a plausible low-confidence fallback or manual-review aid.

---

## 5. Platform Constraints

**Unity rendering rules out Accessibility-tree detection.** MLBB runs on Unity (upgraded to Unity 2017 in the 2.0 update). Unity renders its whole UI to a single GL/Vulkan surface with no native Android View tree, so `AccessibilityService` structurally cannot read hero identities off screen.
> **Action item:** document explicitly in the codebase that Accessibility Service is doing foreground-app detection / dialog automation / health-watchdog work only, never portrait detection — so nobody (including a coding LLM working from this doc later) assumes otherwise.

**MediaProjection changed for Android 15+.**
- Fresh user consent required before every capture session — tokens can't be cached across app restarts.
- Persistent status bar chip on Android 15 QPR1+ alerts users to any in-progress capture, tappable to kill the session.
- Projection auto-stops when the screen locks.
> **Action item:** any process death mid-draft (the case DataStore serialization in Pillar 5 is meant to survive) means the user hits a real consent dialog on relaunch, not a silent resume. Pillar 4's "one-tap overlay relaunch" needs to re-prompt for capture consent inline, with the manual tap-based path available immediately in the gap.

---

## 6. Recommended Build Order (mapped to mission.md Pillar 1)

1. **Rank detection from emblem region** — fewer classes than heroes means wider margins; the existing PerceptualHash matcher likely handles it with less tuning. Feeds `RankRuleEngine`.
2. **First-pick side auto-detection** — likely a binary state (which side's turn indicator is lit); a color/brightness threshold on a fixed region is probably sufficient, no hashing/model needed. Feeds `PickSequenceEngine`.
3. **BAN_ROUND_1/2 phase OCR** — now constrained by `RankRuleEngine`'s known structure from step 1.
4. **Hybrid perceptual hash + TFLite disambiguation layer** on portrait matching — last, since steps 1–3 may reduce how often it's even needed.

---

## 7. Prior Art (context, not direct reuse)

- **[pyLoL](https://github.com/league-of-legends-replay-extractor/pyLoL)** — LoL VOD analyzer; champion portraits sourced from Riot's Data Dragon CDN, trained detector reaching 92.2% mAP / 91.3% precision / 90.2% recall across 167 champions.
- **["League of Legends Overlay Assistant"](https://csci599-lol-team-pages.github.io/markdown/)** (USC student project) — closest documented blueprint to this project: Tesseract/OpenCV OCR for text + a separately trained CNN for champion portrait ID, built specifically for a ban/pick assistant tool.
- **[PlayerImageRecognizerLeagueOfLegends](https://github.com/DaniRuizPerez/PlayerImageRecognizerLeagueOfLegends)** — older SVM-over-RGB-features approach; illustrates why the field moved to CNNs/embeddings.
- Existing MLBB draft tools — [ridwaanhall/mlbb-draft-assistant](https://github.com/ridwaanhall/mlbb-draft-assistant), [vin-03/mlbb-draft-assistant](https://github.com/vin-03/mlbb-draft-assistant), [R-N/ml_draftpick_dss](https://github.com/R-N/ml_draftpick_dss) — all take **manual** hero input. None do automatic screen-capture detection. That part is genuinely unclaimed territory for MLBB.
