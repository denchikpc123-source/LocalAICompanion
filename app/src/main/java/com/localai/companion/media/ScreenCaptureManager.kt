package com.localai.companion.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager

class ScreenCaptureManager(private val context: Context) {

    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null

    private val bitmapLock = Any()
    private var latestBitmap: Bitmap? = null

    fun start(projection: MediaProjection) {
        if (mediaProjection != null) return

        mediaProjection = projection

        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                stop()
            }
        }

        projectionCallback = callback

        mediaProjection?.registerCallback(
            callback,
            Handler(Looper.getMainLooper())
        )

        val windowManager =
            context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val width = metrics.widthPixels / 2
        val height = metrics.heightPixels / 2
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2
        ).apply {

            setOnImageAvailableListener({ reader ->
                var image: Image? = null

                try {
                    image = reader.acquireLatestImage()

                    if (image == null) {
                        return@setOnImageAvailableListener
                    }

                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding =
                        rowStride - pixelStride * image.width

                    val bitmap = Bitmap.createBitmap(
                        image.width + rowPadding / pixelStride,
                        image.height,
                        Bitmap.Config.ARGB_8888
                    )

                    bitmap.copyPixelsFromBuffer(buffer)

                    val croppedBitmap = Bitmap.createBitmap(
                        bitmap,
                        0,
                        0,
                        image.width,
                        image.height
                    )

                    bitmap.recycle()

                    synchronized(bitmapLock) {
                        latestBitmap?.recycle()
                        latestBitmap = croppedBitmap
                    }

                } catch (e: Exception) {
                } finally {
                    image?.close()
                }
            }, null)
        }

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }

    fun getSnapshot(): Bitmap? {
        synchronized(bitmapLock) {
            val current = latestBitmap

            if (current == null || current.isRecycled) {
                return null
            }

            return try {
                Bitmap.createBitmap(current)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun stop() {
        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.close()
        imageReader = null

        synchronized(bitmapLock) {
            latestBitmap?.recycle()
            latestBitmap = null
        }

        projectionCallback?.let {
            mediaProjection?.unregisterCallback(it)
        }

        projectionCallback = null

        mediaProjection?.stop()
        mediaProjection = null
    }
}
