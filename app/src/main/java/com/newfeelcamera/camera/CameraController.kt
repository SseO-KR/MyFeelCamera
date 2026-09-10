package com.newfeelcamera.camera

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.view.Surface
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class CameraController @Inject constructor (
    @param:ApplicationContext private val appContext: Context,
) {

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

    fun takePhoto(
        onPhotoSaved: (Uri?) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val capture = imageCapture ?: return onError(IllegalStateException("Camera is not ready."))

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "FeelCamera_${System.currentTimeMillis()}")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FeelCamera")
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            appContext.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues,
        ).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(appContext),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onPhotoSaved(outputFileResults.savedUri)
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception)
                }
            },
        )
    }
}