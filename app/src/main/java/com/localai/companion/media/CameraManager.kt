package com.localai.companion.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume

class CameraManager(private val context: Context) {

    private var imageCapture: ImageCapture? = null
    private var previewUseCase: Preview? = null
    private val executor: Executor = ContextCompat.getMainExecutor(context)
    private var cameraProvider: ProcessCameraProvider? = null

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        initialSurfaceProvider: Preview.SurfaceProvider?
    ) {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                cameraProvider?.unbindAll()

                previewUseCase = Preview.Builder()
                    .build()
                    .apply {
                        if (initialSurfaceProvider != null) {
                            setSurfaceProvider(initialSurfaceProvider)
                        }
                    }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(
                        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                    )
                    .build()

                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    previewUseCase,
                    imageCapture
                )
            } catch (e: Exception) {
                imageCapture = null
                previewUseCase = null
            }
        }, executor)
    }

    fun setSurfaceProvider(
        provider: Preview.SurfaceProvider?
    ) {
        previewUseCase?.setSurfaceProvider(provider)
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
        imageCapture = null
        previewUseCase = null
    }

    suspend fun takeSnapshot(): Bitmap? =
        suspendCancellableCoroutine { cont ->

            val capture = imageCapture

            if (capture == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            capture.takePicture(
                executor,
                object : ImageCapture.OnImageCapturedCallback() {

                    override fun onCaptureSuccess(
                        image: ImageProxy
                    ) {
                        try {
                            val buffer =
                                image.planes[0].buffer

                            val bytes =
                                ByteArray(buffer.capacity())

                            buffer.get(bytes)

                            val bitmap =
                                BitmapFactory.decodeByteArray(
                                    bytes,
                                    0,
                                    bytes.size,
                                    null
                                )

                            if (bitmap != null) {
                                val rotationDegrees =
                                    image.imageInfo.rotationDegrees
                                        .toFloat()

                                if (rotationDegrees != 0f) {
                                    val matrix = Matrix().apply {
                                        postRotate(rotationDegrees)
                                    }

                                    val rotatedBitmap =
                                        Bitmap.createBitmap(
                                            bitmap,
                                            0,
                                            0,
                                            bitmap.width,
                                            bitmap.height,
                                            matrix,
                                            true
                                        )

                                    if (rotatedBitmap != bitmap) {
                                        bitmap.recycle()
                                    }

                                    cont.resume(rotatedBitmap)
                                } else {
                                    cont.resume(bitmap)
                                }
                            } else {
                                cont.resume(null)
                            }
                        } catch (e: Exception) {
                            cont.resume(null)
                        } finally {
                            image.close()
                        }
                    }

                    override fun onError(
                        exception: ImageCaptureException
                    ) {
                        cont.resume(null)
                    }
                }
            )
        }
}
