package com.sseo.feelcamera.camera.gl

import android.graphics.Rect
import android.opengl.Matrix
import android.util.Size
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sseo.feelcamera.ui.camera.uiContentRotationDegrees
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.hypot

@RunWith(AndroidJUnit4::class)
class PreviewRotationTest {
    private val rotations = listOf(Surface.ROTATION_0, Surface.ROTATION_90,
        Surface.ROTATION_180, Surface.ROTATION_270)
    private val bufferSize = Size(1280, 960)
    // 실제 후면 카메라 로그의 SurfaceTexture 행렬: y-flip과 sensor 90° 변환.
    private val cameraMatrix = floatArrayOf(
        0f, -1f, 0f, 0f, -1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f,
    )

    private fun info(degrees: Int, cameraTransform: Boolean = true, mirror: Boolean = false) =
        CameraTransformSnapshot(Rect(0, 0, 1280, 960), degrees, cameraTransform, mirror)

    private fun point(matrix: FloatArray, x: Float, y: Float): FloatArray =
        FloatArray(4).also { Matrix.multiplyMV(it, 0, matrix, 0, floatArrayOf(x, y, 0f, 1f), 0) }

    @Test
    fun uiRotationAccountsForAllSixteenDisplayAndDeviceCombinations() {
        // 행=Display, 열=device target. Phone 고정과 adaptive Window를 모두 검사한다.
        val expected = arrayOf(
            floatArrayOf(0f, 90f, -180f, -90f),
            floatArrayOf(-90f, 0f, 90f, -180f),
            floatArrayOf(-180f, -90f, 0f, 90f),
            floatArrayOf(90f, -180f, -90f, 0f),
        )
        rotations.forEachIndexed { d, display ->
            rotations.forEachIndexed { t, target ->
                assertEquals(expected[d][t], uiContentRotationDegrees(display, target), 0f)
            }
        }
    }

    @Test
    fun cameraSurfaceMapsTheTopLeftToTheCorrectBufferCornerInFourDirections() {
        val expected = arrayOf(floatArrayOf(0f, 1f), floatArrayOf(0f, 0f),
            floatArrayOf(1f, 0f), floatArrayOf(1f, 1f))
        val transform = PreviewTextureTransform()
        rotations.forEachIndexed { index, display ->
            val portrait = index % 2 == 0
            transform.update(cameraMatrix, display, info((90 - index * 90 + 360) % 360),
                bufferSize, if (portrait) 960 else 1280, if (portrait) 1280 else 960)
            val topLeft = point(transform.matrix, 0f, 1f)
            assertEquals(expected[index][0], topLeft[0], 0.00001f)
            assertEquals(expected[index][1], topLeft[1], 0.00001f)
        }
    }

    @Test
    fun cameraTransformDoesNotApplyMetadataRotationAgain() {
        val transform = PreviewTextureTransform()
        transform.update(cameraMatrix, Surface.ROTATION_270, info(180), bufferSize, 1280, 960)
        val first = transform.matrix.copyOf()
        // metadata 갱신 순서가 달라도 hasCameraTransform 경로의 방향은 Display가 결정한다.
        transform.update(cameraMatrix, Surface.ROTATION_270, info(90), bufferSize, 1280, 960)
        assertArrayEquals(first, transform.matrix, 0.00001f)
    }

    @Test
    fun nonCameraSurfaceAppliesMetadataInsteadOfDisplayRotation() {
        val flipY = FloatArray(16).also {
            Matrix.setIdentityM(it, 0)
            Matrix.translateM(it, 0, 0f, 1f, 0f)
            Matrix.scaleM(it, 0, 1f, -1f, 1f)
        }
        val camera = PreviewTextureTransform()
        val copied = PreviewTextureTransform()
        rotations.forEachIndexed { index, display ->
            val metadata = (90 - index * 90 + 360) % 360
            camera.update(cameraMatrix, display, info(metadata), bufferSize, 500, 333)
            copied.update(flipY, display, info(metadata, cameraTransform = false), bufferSize, 500, 333)
            assertArrayEquals(camera.matrix, copied.matrix, 0.00001f)
        }
    }

    @Test
    fun centerCropPreservesPixelAspectAndKeepsSamplesWithinTheBuffer() {
        val transform = PreviewTextureTransform()
        val outputs = listOf(Size(300, 400), Size(600, 400), Size(900, 180), Size(150, 900))
        rotations.forEach { display ->
            outputs.forEach { output ->
                transform.update(cameraMatrix, display, info(90), bufferSize, output.width, output.height)
                val m = transform.matrix
                val horizontalPixels = hypot(m[0] * 1280, m[1] * 960)
                val verticalPixels = hypot(m[4] * 1280, m[5] * 960)
                assertEquals(output.width.toFloat() / output.height,
                    horizontalPixels / verticalPixels, 0.00001f)
                val center = point(m, 0.5f, 0.5f)
                assertEquals(0.5f, center[0], 0.00001f)
                assertEquals(0.5f, center[1], 0.00001f)
                for (x in listOf(0f, 1f)) for (y in listOf(0f, 1f)) {
                    val corner = point(m, x, y)
                    assertTrue(corner[0] in -0.00001f..1.00001f)
                    assertTrue(corner[1] in -0.00001f..1.00001f)
                }
            }
        }
    }
}
