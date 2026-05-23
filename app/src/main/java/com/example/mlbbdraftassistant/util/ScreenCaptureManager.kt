package com.example.mlbbdraftassistant.util

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ScreenCaptureManager {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    /**
     * Captures a single screen frame and returns it as a [Bitmap].
     *
     * Must be called after [setMediaProjection] with a valid projection.
     */
    suspend fun captureScreen(metrics: DisplayMetrics): Bitmap =
        suspendCancellableCoroutine { continuation ->
            val projection = mediaProjection
            if (projection == null) {
                continuation.cancel(Exception("MediaProjection not set"))
                return@suspendCancellableCoroutine
            }

            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            // Down‑scale for OCR performance (max 720 px on the long side)
            val maxDim = 720
            val scale = if (width > height) maxDim.toFloat() / width else maxDim.toFloat() / height
            val targetWidth = if (scale < 1f) (width * scale).toInt() else width
            val targetHeight = if (scale < 1f) (height * scale).toInt() else height

            val reader = ImageReader.newInstance(
                targetWidth, targetHeight,
                PixelFormat.RGBA_8888, 1
            )
            imageReader = reader

            reader.setOnImageAvailableListener({ r ->
                val image = r.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * targetWidth

                    val bitmap = Bitmap.createBitmap(
                        targetWidth + rowPadding / pixelStride,
                        targetHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    image.close()

                    if (!continuation.isCancelled) {
                        continuation.resume(bitmap)
                    }
                }
                r.close()
            }, Handler(Looper.getMainLooper()))

            virtualDisplay = projection.createVirtualDisplay(
                "ScreenCapture",
                targetWidth, targetHeight, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, null
            )

            continuation.invokeOnCancellation {
                release()
            }
        }

    fun setMediaProjection(projection: MediaProjection?) {
        release()
        mediaProjection = projection
    }

    fun release() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }

    fun isReady(): Boolean = mediaProjection != null
}