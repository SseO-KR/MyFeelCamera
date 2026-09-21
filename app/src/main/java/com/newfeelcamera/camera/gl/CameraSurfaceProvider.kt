package com.newfeelcamera.camera.gl

import android.graphics.Rect
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import kotlinx.coroutines.Runnable
import java.util.concurrent.Executor

data class CameraTransformSnapshot(
    val cropRect: Rect,
    val rotationDegrees: Int,
    val hasCameraTransform: Boolean,
    val isMirroring: Boolean,
)

class CameraSurfaceProvider(
    private val queueOnGlThread: (Runnable) -> Unit,
    private val renderer: CameraRenderer,
) : Preview.SurfaceProvider {

    private val glExecutor = Executor { command ->
        queueOnGlThread(command)
    }

    override fun onSurfaceRequested(request: SurfaceRequest) {
        queueOnGlThread(
            Runnable {
                renderer.onSurfaceRequest(request)
            },
        )

        request.setTransformationInfoListener(
            glExecutor,
        ) { info ->
            renderer.onTransformationInfo(
                request = request,
                snapshot = CameraTransformSnapshot(
                    cropRect = Rect(info.cropRect),
                    rotationDegrees = info.rotationDegrees,
                    hasCameraTransform = info.hasCameraTransform(),
                    isMirroring = info.isMirroring,
                ),
            )
        }
    }
}
