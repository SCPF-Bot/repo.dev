package com.mlbb.assistant.capture

import android.content.Context
import org.json.JSONObject
import timber.log.Timber

/**
 * Loads per-hero, per-slot-type fused-hash acceptance thresholds from
 * `assets/hero_thresholds.json`, as produced by `scripts/calibrate_thresholds.py`
 * (recommendations.md §6.1).
 *
 * The asset ships empty until a labeled calibration corpus is collected
 * (recommendations.md §6.2) — [thresholdFor] simply returns null for any hero
 * without a calibrated entry, and callers fall back to the global
 * [PhaseDetectionConfig.HASH_FUSION_ACCEPT_MIN] constant.
 */
class HeroThresholds(context: Context) {

    companion object {
        private const val ASSET_PATH = "hero_thresholds.json"
        private const val TAG = "HeroThresholds"
    }

    // heroId -> slotType name -> threshold
    private val thresholds: Map<Int, Map<String, Float>> = runCatching {
        val json = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val result = HashMap<Int, Map<String, Float>>()
        for (key in root.keys()) {
            if (key == "_meta") continue
            val heroId = key.toIntOrNull() ?: continue
            val perSlot = root.getJSONObject(key)
            val slotMap = HashMap<String, Float>()
            for (slotKey in perSlot.keys()) {
                slotMap[slotKey] = perSlot.getDouble(slotKey).toFloat()
            }
            result[heroId] = slotMap
        }
        result
    }.getOrElse {
        Timber.w(it, "$TAG: failed to load $ASSET_PATH — using global defaults for all heroes")
        emptyMap()
    }

    /** Calibrated acceptance threshold for [heroId] under [slotType], or null if uncalibrated. */
    fun thresholdFor(heroId: Int, slotType: SlotType): Float? =
        thresholds[heroId]?.get(slotType.name)
}
