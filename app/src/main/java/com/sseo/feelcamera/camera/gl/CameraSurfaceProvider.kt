package com.sseo.feelcamera.camera.gl

import android.graphics.Rect
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest

data class CameraTransformSnapshot(
    val cropRect: Rect,
    val rotationDegrees: Int,
    val hasCameraTransform: Boolean,
    val isMirroring: Boolean,
)

class CameraSurfaceProvider(
    private val renderer: CameraRenderer,
) : Preview.SurfaceProvider {
    override fun onSurfaceRequested(request: SurfaceRequest) {
        // Renderer가 요청만 기록한다. EGL 준비 전에도 안전하며 종료된 View는 즉시 거절한다.
        renderer.onSurfaceRequest(request)
    }
}
