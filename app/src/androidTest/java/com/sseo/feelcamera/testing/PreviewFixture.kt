package com.sseo.feelcamera.testing

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.opengl.GLES30
import android.util.Size
import android.view.Surface
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.impl.CameraInternal
import com.sseo.feelcamera.camera.capture.CaptureEglSession
import com.sseo.feelcamera.camera.filter.FilmPreset
import com.sseo.feelcamera.camera.gl.CameraRenderer
import java.io.Closeable
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue

/** 고정 Bitmap을 실제 Surface/OES 텍스처로 보내 production 미리보기 셰이더를 실행한다. */
@SuppressLint("RestrictedApi")
internal class PreviewFixture(private val input: Bitmap) : Closeable {
    private val egl = CaptureEglSession.create(input.width, input.height)
    private val frameReady = CountDownLatch(1)
    @Volatile private var waitingForFrame = false
    private val renderer = CameraRenderer(TestImages.context.assets,
        requestRender = { if (waitingForFrame) frameReady.countDown() },
        resultExecutor = Executor { it.run() })
    private val request: SurfaceRequest

    init {
        val camera = Proxy.newProxyInstance(CameraInternal::class.java.classLoader,
            arrayOf(CameraInternal::class.java)) { _, method, _ ->
            error("Unexpected CameraInternal call: ${method.name}")
        } as CameraInternal
        request = SurfaceRequest(Size(input.width, input.height), camera) {}
        renderer.onViewAttached()
        renderer.onSurfaceCreated(null, null)
        renderer.onSurfaceChanged(null, input.width, input.height)
        renderer.onSurfaceRequest(request)
        request.updateTransformationInfo(SurfaceRequest.TransformationInfo.of(
            Rect(0, 0, input.width, input.height), 0, Surface.ROTATION_0, false, Matrix(), false))
        renderer.onDrawFrame(null)
        val surface = request.deferrableSurface.surface.get(3, TimeUnit.SECONDS)
        val canvas = surface.lockCanvas(null)
        try {
            canvas.drawBitmap(input, 0f, 0f, null)
        } finally {
            waitingForFrame = true
            surface.unlockCanvasAndPost(canvas)
        }
        assertTrue("No OES frame arrived", frameReady.await(5, TimeUnit.SECONDS))
    }

    fun render(film: FilmPreset): Bitmap {
        egl.makeCurrent()
        renderer.setFilmPreset(film)
        renderer.onDrawFrame(null)
        assertTrue("No frame was rendered", renderer.renderedFrame?.film == film)
        val buffer = ByteBuffer.allocateDirect(input.width * input.height * 4).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, input.width, input.height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
        assertTrue("Preview glReadPixels failed", GLES30.glGetError() == GLES30.GL_NO_ERROR)
        buffer.rewind()
        val bottomUp = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        bottomUp.copyPixelsFromBuffer(buffer)
        return try {
            Bitmap.createBitmap(bottomUp, 0, 0, bottomUp.width, bottomUp.height,
                Matrix().apply { setScale(1f, -1f) }, false)
        } finally {
            bottomUp.recycle()
        }
    }

    override fun close() {
        egl.makeCurrent()
        renderer.close()
        request.deferrableSurface.close()
        renderer.onDrawFrame(null)
        renderer.onViewDetached()
        egl.close()
    }
}
