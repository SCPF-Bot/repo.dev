package com.mlbbassistant.data.api.dto

import com.google.gson.annotations.SerializedName

data class MetaSnapshotDto(
    @SerializedName("patch")      val patch: String,
    @SerializedName("updated_at") val updatedAt: Long,
    @SerializedName("heroes")     val heroes: List<HeroDto>
)
