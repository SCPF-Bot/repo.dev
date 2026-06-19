package com.mlbb.assistant.domain.model

import androidx.compose.runtime.Stable

/**
 * A recommended core item for a hero's build path.
 *
 * Annotated [@Stable] so Compose skips recomposition when the instance
 * hasn't changed — the equality contract is satisfied by [data class].
 */
@Stable
data class CoreItem(
    val name: String,
    val imageUrl: String = ""
)
