package com.newfeelcamera.camera

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.takePicture
import androidx.core.content.ContextCompat
import com.newfeelcamera.camera.capture.CaptureException
import com.newfeelcamera.camera.capture.CaptureProcessor
import com.newfeelcamera.camera.filter.FilmPreset
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.Closeable
import javax.inject.Inject

class CameraController @Inject constructor (
    @param:ApplicationContext private val appContext: Context,
    private val captureProcessor: CaptureProcessor,
): Closeable {

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null

    private var previewTargetRotation = Surface.ROTATION_0
    private var imageCaptureTargetRotation = Surface.ROTATION_0

    fun bind(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
    ){
        val providerFuture = ProcessCameraProvider.getInstance(appContext)

        providerFuture.addListener({
            val cameraProvider = providerFuture.get()

            val previewUseCase = Preview.Builder()
                .setTargetRotation(previewTargetRotation)
                .build()
                .apply {
                    setSurfaceProvider(surfaceProvider)
                }

            val capture = ImageCapture.Builder()
                .setTargetRotation(imageCaptureTargetRotation)
                .setOutputFormat(ImageCapture.OUTPUT_FORMAT_JPEG)
                .build()

            cameraProvider.unbindAll()

            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                previewUseCase,
                capture,
            )

            preview = previewUseCase
            imageCapture = capture
        },
            ContextCompat.getMainExecutor(appContext)
        )
    }

    private fun isSurfaceRotation(rotation: Int): Boolean =
        when (rotation) {
            Surface.ROTATION_0,
            Surface.ROTATION_90,
            Surface.ROTATION_180,
            Surface.ROTATION_270,
                -> true

            else -> false
        }

    fun updateImageCaptureTargetRotation(rotation: Int) {
        if (!isSurfaceRotation(rotation)) return
        if (imageCaptureTargetRotation == rotation) return

        imageCaptureTargetRotation = rotation
        imageCapture?.targetRotation = rotation
    }

    fun updatePreviewTargetRotation(rotation: Int) {
        if (!isSurfaceRotation(rotation)) return
        if (previewTargetRotation == rotation) return

        previewTargetRotation = rotation
        preview?.targetRotation = rotation
    }

    suspend fun takePhoto(
        film: FilmPreset,
    ): Uri {
        val capture = imageCapture
            ?: throw CaptureException.CameraNotReady()

        Log.d(TAG, "takePhoto film=$film")

        val imageProxy = try {
            capture.takePicture()
        } catch (error: ImageCaptureException) {
            throw CaptureException.CameraCaptureFailed(error)
        }

        return captureProcessor.processAndSave(
            imageProxy = imageProxy,
            film = film,
        )
    }

    override fun close() {
        captureProcessor.close()
    }

    companion object {
        private const val TAG = "CameraController"
    }
}
