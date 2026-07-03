package com.mlbb.assistant.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Manages a [MediaProjection] virtual display and captures frames from it.
 *
 * ### Android 15+ (API 35) — MediaProjection consent re-prompt
 *
 * Starting in Android 15 (API level 35), the platform re-shows the
 * MediaProjection consent dialog **every time** an app calls
 * `MediaProjectionManager.createScreenCaptureIntent()` — even if the user
 * already granted it in a previous session.  The old approach of storing the
 * projection `resultCode` + `data` in a DataStore and replaying it on service
 * restart is no longer valid on API 35+ devices.
 *
 * **Required handling:** When the owning Activity or Service restarts after
 * receiving `MediaProjection.Callback.onStop()` (or after process death),
 * it must re-request a fresh `createScreenCaptureIntent()` rather than
 * re-using a cached token.  See [OverlayService.onStartCommand] for the
 * current consent flow.
 *
 * Reference: https://developer.android.com/media/grow/media-projection
 * (ideas.md §5)
 */
class ScreenCaptureManager(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var projectionCallback: MediaProjection.Callback? = null

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    /**
     * §2 — Surfaces MediaProjection revocation to callers so the overlay can show
     * a "capture unavailable" status banner instead of silently freezing the
     * last-seen frame. Set to `true` exactly when [_isCapturing] transitions to
     * `false` via [MediaProjection.Callback.onStop] (system-revoked token —
     * screen-record notification dismissed, other app started, etc.) rather than
     * via an explicit [stopCapture] call.
     */
    private val _captureRevoked = MutableStateFlow(false)
    val captureRevoked: StateFlow<Boolean> = _captureRevoked.asStateFlow()

    var screenWidth:  Int = 0
        private set
    var screenHeight: Int = 0
        private set
    private var screenDpi    = 0

    init {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // P0 fix: currentWindowMetrics was added in API 30; minSdk is 29.
        // Use the deprecated Display.getRealSize() path on API 29 devices to avoid
        // NoSuchMethodError at service startup on Android 10 hardware.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            screenWidth  = bounds.width()
            screenHeight = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val display = wm.defaultDisplay
            val size    = android.graphics.Point()
            @Suppress("DEPRECATION")
            display.getRealSize(size)
            screenWidth  = size.x
            screenHeight = size.y
        }
        screenDpi = context.resources.displayMetrics.densityDpi
    }

    fun startCapture(resultCode: Int, data: Intent) {
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = mpm.getMediaProjection(resultCode, data)
        mediaProjection = projection
        _captureRevoked.value = false

        // §2 — Register a Callback so we notice when the platform (not us) tears
        // down the projection: user dismissed the "screen is being recorded"
        // notification, another app requested a new projection, or (Android 15+)
        // any backgrounding of the owning process. Without this, captureFrame()
        // would just start silently returning null/stale frames with no signal
        // to the UI. Callback fires on the main thread per MediaProjection docs.
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                virtualDisplay?.release()
                imageReader?.close()
                virtualDisplay = null
                imageReader    = null
                mediaProjection = null
                _isCapturing.value    = false
                _captureRevoked.value = true
            }
        }
        projectionCallback = callback
        projection.registerCallback(callback, null)

        // P0-01 fix: capture imageReader into a local val immediately after construction.
        // The field `imageReader` is a mutable var; if stopCapture() is called on another
        // thread between assignment and use, the !! operator would produce an NPE.
        // Using a local val eliminates the TOCTOU race entirely.
        val reader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "MLBBCapture",
            screenWidth, screenHeight, screenDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,   // local val — never null, no !! needed
            null, null
        )

        _isCapturing.value = true
    }

    /** Clears the revoked-capture flag once the overlay has re-acquired a token and resumed. */
    fun clearRevokedFlag() {
        _captureRevoked.value = false
    }

    /** Grab the latest frame. Returns null if no frame available yet. */
    suspend fun captureFrame(): Bitmap? = withContext(Dispatchers.IO) {
        val reader = imageReader ?: return@withContext null
        val image  = reader.acquireLatestImage() ?: return@withContext null

        return@withContext try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride  = planes[0].pixelStride
            val rowStride    = planes[0].rowStride
            val rowPadding   = rowStride - pixelStride * screenWidth
            val bmp = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(buffer)
            Bitmap.createBitmap(bmp, 0, 0, screenWidth, screenHeight).also { bmp.recycle() }
        } finally {
            image.close()
        }
    }

    fun stopCapture() {
        projectionCallback?.let { mediaProjection?.unregisterCallback(it) }
        projectionCallback = null
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        virtualDisplay   = null
        imageReader      = null
        mediaProjection  = null
        _isCapturing.value = false
        // Explicit stopCapture() is a normal lifecycle event (service onDestroy,
        // manual toggle) — not a revocation, so don't flag it as one.
        _captureRevoked.value = false
    }
}
