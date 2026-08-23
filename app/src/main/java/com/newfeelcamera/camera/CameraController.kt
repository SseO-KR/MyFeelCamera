package com.newfeelcamera.camera

import android.content.Context
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.core.content.ContextCompat

class CameraController(
    private val appContext: Context,
) {

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

            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
            )
        },
            ContextCompat.getMainExecutor(appContext)
        )
    }
}