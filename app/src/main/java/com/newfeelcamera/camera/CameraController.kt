package com.newfeelcamera.camera

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
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

    private var imageCapture: ImageCapture? = null

    fun bind(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
    ){
        val providerFuture = ProcessCameraProvider.getInstance(appContext)

        providerFuture.addListener({
            val cameraProvider = providerFuture.get()

            val preview = Preview.Builder()
                .build()
                .apply {
                    setSurfaceProvider(surfaceProvider)
                }

            val capture = ImageCapture.Builder().build()

            cameraProvider.unbindAll()

            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                capture,
            )

            imageCapture = capture
        },
            ContextCompat.getMainExecutor(appContext)
        )
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