package com.mlbb.assistant.utils

import android.content.Context
import android.widget.Toast

/**
 * Shows a short Toast. Call from a coroutine or click handler on the Main dispatcher,
 * NOT directly inside a @Composable function body (side effects belong in LaunchedEffect).
 */
fun Context.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
