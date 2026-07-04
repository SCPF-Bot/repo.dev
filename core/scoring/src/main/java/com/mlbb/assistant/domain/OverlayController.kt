package com.mlbb.assistant.domain

/**
 * Pure-domain abstraction over starting/stopping the overlay.
 * The concrete implementation lives in [di.OverlayModule] so that the
 * domain layer has no dependency on android.* or the presentation layer.
 */
interface OverlayController {
    fun start()
    fun stop()
}
