package com.newfeelcamera.camera.gl

import android.opengl.Matrix
import android.util.Size
import android.view.Surface
import kotlin.math.hypot

/** GL thread에서 사용하는 출력 UV → OES 샘플링 UV 변환. View 자체는 회전시키지 않는다. */
internal class PreviewTextureTransform {
    val matrix = FloatArray(16)
    private val correction = FloatArray(16)
    private val oriented = FloatArray(16)

    fun update(
        surfaceTextureMatrix: FloatArray,
        displayRotation: Int,
        info: CameraTransformSnapshot,
        bufferSize: Size,
        outputWidth: Int,
        outputHeight: Int,
    ) {
        val displayDegrees = when (displayRotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        // 직접 Camera에 연결된 ST에는 카메라 방향 변환이 이미 있다.
        // y-up UV의 역방향 샘플링 보정이므로 Display 각도의 부호를 뒤집는다.
        val uvRotation = if (info.hasCameraTransform) -displayDegrees else info.rotationDegrees
        Matrix.setIdentityM(correction, 0)
        Matrix.translateM(correction, 0, 0.5f, 0.5f, 0f)
        Matrix.rotateM(correction, 0, uvRotation.toFloat(), 0f, 0f, 1f)
        if (!info.hasCameraTransform && info.isMirroring) {
            Matrix.scaleM(correction, 0, -1f, 1f, 1f)
        }
        Matrix.translateM(correction, 0, -0.5f, -0.5f, 0f)
        Matrix.multiplyMM(oriented, 0, surfaceTextureMatrix, 0, correction, 0)

        // 회전된 각 UV 축이 원본 buffer에서 차지하는 픽셀 길이.
        // ST의 축 교환/producer crop을 포함하므로 sensor가 90°라고 하드코딩하지 않는다.
        val sourceWidth = hypot(oriented[0] * bufferSize.width, oriented[1] * bufferSize.height)
        val sourceHeight = hypot(oriented[4] * bufferSize.width, oriented[5] * bufferSize.height)
        if (sourceWidth <= 0f || sourceHeight <= 0f || outputWidth <= 0 || outputHeight <= 0) {
            oriented.copyInto(matrix)
            return
        }
        val sourceAspect = sourceWidth / sourceHeight
        val outputAspect = outputWidth.toFloat() / outputHeight
        val cropX = if (sourceAspect > outputAspect) outputAspect / sourceAspect else 1f
        val cropY = if (sourceAspect < outputAspect) sourceAspect / outputAspect else 1f

        Matrix.setIdentityM(correction, 0)
        Matrix.translateM(correction, 0, 0.5f, 0.5f, 0f)
        Matrix.scaleM(correction, 0, cropX, cropY, 1f)
        Matrix.translateM(correction, 0, -0.5f, -0.5f, 0f)
        Matrix.multiplyMM(matrix, 0, oriented, 0, correction, 0)
    }
}
