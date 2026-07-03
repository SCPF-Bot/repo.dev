package com.mlbb.assistant.data.remote.api

import com.mlbb.assistant.data.remote.dto.HeroDto
import retrofit2.http.GET

interface MetaApi {
    /**
     * Fetches the hero meta roster from the GitHub raw content host.
     *
     * The file is a bare JSON array of [HeroDto] objects — the same format as the
     * bundled `res/raw/default_heroes.json` fallback — so no wrapper object is needed.
     *
     * Full resolved URL (base + path):
     *   https://raw.githubusercontent.com/SCPF-Bot/repo.dev/main/app/src/main/res/raw/default_heroes.json
     */
    @GET("app/src/main/res/raw/default_heroes.json")
    suspend fun getMetaSnapshot(): List<HeroDto>
}