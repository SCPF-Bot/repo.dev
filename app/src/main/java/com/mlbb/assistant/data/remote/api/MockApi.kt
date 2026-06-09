// File: app/src/main/java/com/mlbb/assistant/data/remote/api/MockApi.kt
package com.mlbb.assistant.data.remote.api

import com.mlbb.assistant.data.remote.dto.MetaSnapshotDto
import kotlinx.coroutines.delay

class MockApi {
    suspend fun getMetaSnapshot(): MetaSnapshotDto {
        delay(1000) // simulate network delay
        return MetaSnapshotDto(emptyList())
    }
}