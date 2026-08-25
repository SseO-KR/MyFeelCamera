package com.newfeelcamera.ui.camera.gl

import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import kotlinx.coroutines.Runnable

class CameraSurfaceProvider(
    private val queueOnGlThread: (Runnable) -> Unit,
    private val renderer: CameraRenderer,
) : Preview.SurfaceProvider {

    override fun onSurfaceRequested(request: SurfaceRequest) {
        queueOnGlThread(
            Runnable {
                renderer.onSurfaceRequest(request)
            }
        )
    }
}