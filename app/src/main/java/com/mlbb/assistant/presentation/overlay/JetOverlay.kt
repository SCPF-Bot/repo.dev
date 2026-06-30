package com.mlbb.assistant.presentation.overlay

import android.app.Application
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.compose.ui.platform.ComposeView
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Local shim replacing the unresolvable com.github.YazanAesmael:JetOverlay JitPack artifact.
 *
 * Implements the same API surface used by [MLBBApplication] and [OverlayService]:
 *   - [initialize] — registers the overlay composable once at app startup.
 *   - [show]       — inflates the composable into a TYPE_APPLICATION_OVERLAY window.
 *   - [hide]       — removes the window.
 *
 * Drag-to-move is provided via a simple touch listener on the root view so the
 * bubble remains repositionable without the external library.
 */
object JetOverlay {

    private var application: Application? = null
    private var overlayContent: (@Composable () -> Unit)? = null

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    fun initialize(app: Application, block: JetOverlayScope.() -> Unit) {
        application = app
        JetOverlayScope().also(block)
    }

    internal fun setContent(content: @Composable () -> Unit) {
        overlayContent = content
    }

    // P1 fix: synchronize show() and hide() to prevent a race between the
    // MLBBAccessibilityService path and the MainActivity path both calling
    // OverlayService.start() near-simultaneously. Without the lock, two threads
    // can both pass the `composeView != null` guard and then both call
    // WindowManager.addView(), causing IllegalStateException: "View already added".
    @Synchronized
    fun show() {
        val app = application ?: return
        val content = overlayContent ?: return
        if (composeView != null) return

        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }

        val owner = OverlayLifecycleOwner().also { lifecycleOwner = it }
        owner.performRestore(null)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val view = ComposeView(app).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent { content() }
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    runCatching { wm.updateViewLayout(view, params) }
                    true
                }
                else -> false
            }
        }

        composeView = view
        runCatching {
            wm.addView(view, params)
        }.onFailure { e ->
            // Typically WindowManager.BadTokenException when SYSTEM_ALERT_WINDOW
            // permission is not yet granted.  Reset state so a subsequent show()
            // call (after permission is granted) can retry cleanly.
            android.util.Log.e("JetOverlay", "addView failed — overlay permission may be missing", e)
            composeView = null
            windowManager = null
            lifecycleOwner = null
        }
    }

    @Synchronized
    fun hide() {
        val view = composeView ?: return
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        runCatching { windowManager?.removeView(view) }
        composeView = null
        windowManager = null
        lifecycleOwner = null
    }
}

class JetOverlayScope {
    fun overlayContent(content: @Composable () -> Unit) {
        JetOverlay.setContent(content)
    }
}

private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun handleLifecycleEvent(event: Lifecycle.Event) = lifecycleRegistry.handleLifecycleEvent(event)
    fun performRestore(savedState: Bundle?) = savedStateRegistryController.performRestore(savedState)
}
