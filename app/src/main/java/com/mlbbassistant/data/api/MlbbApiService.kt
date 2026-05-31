package com.mlbbassistant.data.api

import com.mlbbassistant.data.api.dto.MetaSnapshotDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit service for fetching MLBB meta data.
 *
 * Base URL is configured in [com.mlbbassistant.di.NetworkModule].
 * Replace the placeholder base URL in that module with your actual API endpoint.
 */
interface MlbbApiService {

    /**
     * Returns the full hero roster together with current win/pick/ban rates.
     *
     * @param patch Optional patch string (e.g. "1.8.44"). When omitted the server
     *              returns data for the latest available patch.
     */
    @GET("meta/snapshot")
    suspend fun getMetaSnapshot(
        @Query("patch") patch: String? = null
    ): MetaSnapshotDto
}
