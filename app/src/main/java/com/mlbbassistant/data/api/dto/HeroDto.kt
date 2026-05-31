package com.mlbbassistant.data.api.dto

import com.google.gson.annotations.SerializedName

data class HeroDto(
    @SerializedName("id")           val id: Int,
    @SerializedName("name")         val name: String,
    @SerializedName("role")         val role: String,
    @SerializedName("secondary_role") val secondaryRole: String?,
    @SerializedName("win_rate")     val winRate: Float,
    @SerializedName("pick_rate")    val pickRate: Float,
    @SerializedName("ban_rate")     val banRate: Float,
    @SerializedName("counters")     val counters: List<Int>,
    @SerializedName("countered_by") val counteredBy: List<Int>,
    @SerializedName("synergies")    val synergies: List<Int>,
    @SerializedName("lane")         val lane: String,
    @SerializedName("image_url")    val imageUrl: String
)
