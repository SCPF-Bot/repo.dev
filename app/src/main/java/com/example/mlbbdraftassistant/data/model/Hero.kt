package com.example.mlbbdraftassistant.data.model

data class Hero(
    val hero_id: Int,
    val hero_name: String,
    val hero_role: String,
    val hero_image: String?,
    val hero_counters: List<Counter>?,
    val hero_synergies: List<Synergy>?,
    val win_rate: Float?,
    val pick_rate: Float?,
    val ban_rate: Float?
) {
    /**
     * Normalized lowercase name without punctuation – used for matching.
     */
    val normalizedName: String
        get() = hero_name.lowercase().replace(Regex("[^a-z0-9]"), "")
}