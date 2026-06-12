package com.mlbb.assistant.data.remote.api

import com.mlbb.assistant.data.remote.dto.MetaSnapshotDto
import retrofit2.http.GET

interface MetaApi {
    @GET("v1/meta/snapshot")
    suspend fun getMetaSnapshot(): MetaSnapshotDto
}