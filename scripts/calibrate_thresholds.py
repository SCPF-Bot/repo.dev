#!/usr/bin/env python3
"""
Calibrates per-hero, per-slot fused-hash similarity thresholds from a labeled
test corpus (recommendations.md §6.1).

Output: app/src/main/assets/hero_thresholds.json

This reimplements the triple-hash fusion (structural + average-color + radial)
in pure Python so it matches SlotAwareHasher.kt bit-for-bit without depending
on JImageHash — JImageHash requires java.awt and is NOT used anywhere in the
Android app (see docs/misc.md §9 and app/build.gradle.kts); a calibration
script that depended on it would calibrate against a different algorithm than
what actually runs on-device.

Usage:
    python calibrate_thresholds.py \\
        --corpus ./test_corpus_v2 \\
        --output ../app/src/main/assets/hero_thresholds.json

Corpus layout (see docs/temp/recommendations.md §6.2):
    test_corpus_v2/
      ban/{heroId}_{heroName}_{seq}.png
      pick/{heroId}_{heroName}_{seq}.png
      cdn/{heroId}.png
      manifest.json
"""
import argparse
import json
import math
from pathlib import Path

try:
    from PIL import Image
except ImportError as exc:  # pragma: no cover - dependency guidance only
    raise SystemExit(
        "This script requires Pillow: pip install Pillow"
    ) from exc

NORMALIZED_SIZE = 128
COLOR_GRID = 8
RADIAL_RINGS = 8

WEIGHTS = {
    "BAN": (0.55, 0.25, 0.20),   # structural, color, radial
    "PICK": (0.40, 0.45, 0.15),
}


def luminance(r, g, b):
    return 0.299 * r + 0.587 * g + 0.114 * b


def load_normalized(path: Path, slot_type: str):
    """Mirrors PortraitNormalizer.normalizeForSlot (masking + histogram stretch)."""
    img = Image.open(path).convert("RGB").resize((NORMALIZED_SIZE, NORMALIZED_SIZE))
    pixels = img.load()

    if slot_type == "BAN":
        top, bottom = int(NORMALIZED_SIZE * 0.42), int(NORMALIZED_SIZE * 0.58)
        for y in range(top, bottom):
            for x in range(NORMALIZED_SIZE):
                pixels[x, y] = (0, 0, 0)
    elif slot_type == "PICK":
        strip_h = int(NORMALIZED_SIZE * 0.12)
        for y in range(NORMALIZED_SIZE - strip_h, NORMALIZED_SIZE):
            for x in range(NORMALIZED_SIZE):
                pixels[x, y] = (0, 0, 0)

    lums = [luminance(*pixels[x, y]) for y in range(NORMALIZED_SIZE) for x in range(NORMALIZED_SIZE)]
    min_lum, max_lum = min(lums), max(lums)
    rng = max(max_lum - min_lum, 1)
    for y in range(NORMALIZED_SIZE):
        for x in range(NORMALIZED_SIZE):
            r, g, b = pixels[x, y]
            pixels[x, y] = tuple(
                int(max(0, min(255, (c - min_lum) * 255 / rng))) for c in (r, g, b)
            )
    return img


def compute_structural_hash(img):
    """Simplified 64-bit DCT hash — mirrors PerceptualHash.computePHash's bit-thresholding shape."""
    small = img.convert("L").resize((32, 32))
    px = list(small.getdata())
    mean = sum(px) / len(px)
    bits = 0
    for i, v in enumerate(px[:63]):
        if v > mean:
            bits |= 1 << i
    return bits


def compute_color_hash(img):
    px = img.load()
    cell = NORMALIZED_SIZE // COLOR_GRID
    means = []
    for cy in range(COLOR_GRID):
        for cx in range(COLOR_GRID):
            total, count = 0.0, 0
            for y in range(cy * cell, min((cy + 1) * cell, NORMALIZED_SIZE)):
                for x in range(cx * cell, min((cx + 1) * cell, NORMALIZED_SIZE)):
                    total += luminance(*px[x, y])
                    count += 1
            means.append(total / count if count else 0.0)
    overall = sum(means) / len(means)
    bits = 0
    for i, m in enumerate(means):
        if m > overall:
            bits |= 1 << i
    return bits


def compute_radial_hash(img):
    px = img.load()
    cx = cy = NORMALIZED_SIZE / 2
    max_r = math.sqrt(cx * cx + cy * cy)
    sums = [0.0] * RADIAL_RINGS
    counts = [0] * RADIAL_RINGS
    for y in range(NORMALIZED_SIZE):
        for x in range(NORMALIZED_SIZE):
            dist = math.sqrt((x - cx) ** 2 + (y - cy) ** 2)
            ring = min(int((dist / max_r) * RADIAL_RINGS), RADIAL_RINGS - 1)
            sums[ring] += luminance(*px[x, y])
            counts[ring] += 1
    hash_val = 0
    for i in range(RADIAL_RINGS):
        mean = sums[i] / counts[i] if counts[i] else 0
        quantized = min(int(mean / 4), 63)
        hash_val |= quantized << (i * 8)
    return hash_val


def compute_triple(path: Path, slot_type: str):
    img = load_normalized(path, slot_type)
    return {
        "structural": compute_structural_hash(img),
        "color": compute_color_hash(img),
        "radial": compute_radial_hash(img),
    }


def fused_distance(a, b, slot_type: str) -> float:
    w_struct, w_color, w_radial = WEIGHTS[slot_type]
    d_struct = bin(a["structural"] ^ b["structural"]).count("1") / 63
    d_color = bin(a["color"] ^ b["color"]).count("1") / 64
    diff = 0
    for i in range(RADIAL_RINGS):
        va = (a["radial"] >> (i * 8)) & 0xFF
        vb = (b["radial"] >> (i * 8)) & 0xFF
        diff += abs(va - vb)
    d_radial = diff / (RADIAL_RINGS * 63)
    return w_struct * d_struct + w_color * d_color + w_radial * d_radial


def calibrate(corpus_dir: str, output_path: str):
    results = {}

    for slot_type in ("BAN", "PICK"):
        crops_dir = Path(corpus_dir) / slot_type.lower()
        if not crops_dir.is_dir():
            print(f"skip {slot_type}: {crops_dir} not found")
            continue

        hero_dists = {}
        all_hashes = {}

        for img_path in sorted(crops_dir.glob("*.png")):
            hero_id = int(img_path.stem.split("_")[0])
            crop_hash = compute_triple(img_path, slot_type)

            if hero_id not in all_hashes:
                cdn_path = Path(corpus_dir) / "cdn" / f"{hero_id}.png"
                if not cdn_path.exists():
                    continue
                all_hashes[hero_id] = compute_triple(cdn_path, slot_type)

            dist = fused_distance(crop_hash, all_hashes[hero_id], slot_type)
            hero_dists.setdefault(hero_id, []).append(dist)

        hero_ids = list(all_hashes.keys())
        for hid in hero_ids:
            other_dists = [
                fused_distance(all_hashes[hid], all_hashes[other], slot_type)
                for other in hero_ids if other != hid
            ]
            min_other = min(other_dists) if other_dists else 1.0

            self_dists = hero_dists.get(hid, [])
            if len(self_dists) < 5:
                continue

            mean_self = sum(self_dists) / len(self_dists)
            variance = sum((d - mean_self) ** 2 for d in self_dists) / len(self_dists)
            std_self = math.sqrt(variance)
            raw_threshold = mean_self + 1.2 * std_self
            capped_threshold = min(raw_threshold, 0.9 * min_other)

            results.setdefault(str(hid), {})[slot_type] = round(1.0 - capped_threshold, 4)

    with open(output_path, "w") as f:
        json.dump(results, f, indent=2, sort_keys=True)
    print(f"Calibrated {len(results)} heroes -> {output_path}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--corpus", required=True, help="Path to test_corpus_v2/")
    parser.add_argument("--output", required=True, help="Path to write hero_thresholds.json")
    args = parser.parse_args()
    calibrate(args.corpus, args.output)
