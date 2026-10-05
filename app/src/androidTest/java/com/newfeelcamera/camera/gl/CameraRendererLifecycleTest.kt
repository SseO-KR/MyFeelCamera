package com.newfeelcamera.camera.gl

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.util.Size
import android.view.Surface
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.impl.CameraInternal
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newfeelcamera.camera.capture.CaptureEglSession
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 실제 EGL/OES/SurfaceRequest를 사용해 콜백 순서와 native 자원 수명을 검증한다. */
@SuppressLint("RestrictedApi")
@RunWith(AndroidJUnit4::class)
class CameraRendererLifecycleTest {
    private val requests = mutableListOf<SurfaceRequest>()
    private var egl: CaptureEglSession? = null
    private var renderer: CameraRenderer? = null

    private fun newRenderer(executor: Executor = Executor { it.run() }): CameraRenderer =
        CameraRenderer(
            assetManager = InstrumentationRegistry.getInstrumentation().targetContext.assets,
            requestRender = {},
            resultExecutor = executor,
        ).also { renderer = it; it.onViewAttached() }

    private fun startContext(renderer: CameraRenderer) {
        egl = CaptureEglSession.create()
        renderer.onSurfaceCreated(null, null)
        renderer.onSurfaceChanged(null, 1, 1)
    }

    private fun newRequest(): SurfaceRequest {
        // SurfaceRequest의 종료 계약만 시험하므로 CameraInternal 메서드는 사용하지 않는다.
        val camera = Proxy.newProxyInstance(
            CameraInternal::class.java.classLoader,
            arrayOf(CameraInternal::class.java),
        ) { _, method, _ -> error("Unexpected CameraInternal call: ${method.name}") } as CameraInternal
        return SurfaceRequest(Size(32, 32), camera) {}.also { requests += it }
    }

    private data class Resources(val surface: Surface, val texture: SurfaceTexture, val id: Int)

    // native 객체의 실제 release/glIsTexture 결과를 확인하기 위한 테스트 전용 관찰이다.
    private fun resources(renderer: CameraRenderer): Resources {
        val binding = CameraRenderer::class.java.getDeclaredField("activeBinding")
            .apply { isAccessible = true }.get(renderer)!!
        fun field(name: String): Any = binding.javaClass.getDeclaredField(name)
            .apply { isAccessible = true }.get(binding)!!
        return Resources(field("surface") as Surface, field("surfaceTexture") as SurfaceTexture,
            field("textureId") as Int)
    }

    private fun provide(renderer: CameraRenderer): Pair<SurfaceRequest, Resources> {
        val request = newRequest()
        renderer.onSurfaceRequest(request)
        renderer.onDrawFrame(null)
        assertTrue(request.deferrableSurface.surface.get(2, TimeUnit.SECONDS).isValid)
        return request to resources(renderer)
    }

    @After
    fun cleanup() {
        renderer?.close()
        requests.forEach { it.deferrableSurface.close() }
        egl?.close()
        renderer?.onViewDetached()
    }

    @Test
    fun requestBeforeContextWaitsForRenderCallback() {
        val renderer = newRenderer()
        val request = newRequest()
        renderer.onSurfaceRequest(request)
        assertFalse(request.deferrableSurface.surface.isDone)

        startContext(renderer)
        renderer.onDrawFrame(null)
        assertTrue(request.deferrableSurface.surface.get(2, TimeUnit.SECONDS).isValid)
    }

    @Test
    fun completionWithoutCurrentContextReleasesAndroidResourcesAndDefersGlDelete() {
        val renderer = newRenderer()
        startContext(renderer)
        val (request, resources) = provide(renderer)
        assertTrue(GLES30.glIsTexture(resources.id))
        // native SurfaceTexture가 Context를 실제로 사용한 뒤 current를 해제한다.
        val canvas = resources.surface.lockCanvas(null)
        canvas.drawColor(Color.RED)
        resources.surface.unlockCanvasAndPost(canvas)
        resources.texture.updateTexImage()
        val display = EGL14.eglGetCurrentDisplay()
        assertTrue(EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT))

        // retirement를 거치지 않은 ACTIVE 상태에서도 완료를 처리해야 한다.
        request.deferrableSurface.close()
        assertFalse(resources.surface.isValid)
        assertTrue(resources.texture.isReleased)
        assertEquals(EGL14.EGL_NO_CONTEXT, EGL14.eglGetCurrentContext())

        egl!!.makeCurrent()
        assertTrue(GLES30.glIsTexture(resources.id))
        renderer.onDrawFrame(null) // active preview가 없어도 삭제를 처리한다.
        assertFalse(GLES30.glIsTexture(resources.id))
    }

    @Test
    fun repeatedReplacementDeletesOldTexturesWithoutReleasingNewBinding() {
        val renderer = newRenderer()
        startContext(renderer)
        var previous = provide(renderer)
        repeat(20) {
            val next = provide(renderer)
            previous.first.deferrableSurface.close()
            assertTrue(previous.second.texture.isReleased)
            assertFalse(next.second.texture.isReleased)
            renderer.onDrawFrame(null)
            assertFalse(GLES30.glIsTexture(previous.second.id))
            assertTrue(GLES30.glIsTexture(next.second.id))
            previous = next
        }
    }

    @Test
    fun lateOldCompletionAfterReattachCannotDeleteTextureInNewContext() {
        val renderer = newRenderer()
        startContext(renderer)
        val (oldRequest, old) = provide(renderer)
        egl!!.close()
        egl = null
        renderer.onViewDetached()
        assertFalse(old.texture.isReleased) // CameraX 완료 전 조기 해제 금지.

        val nextRequest = newRequest()
        renderer.onSurfaceRequest(nextRequest)
        assertFalse(nextRequest.deferrableSurface.surface.isDone)
        renderer.onViewAttached()
        startContext(renderer)
        renderer.onDrawFrame(null)
        val next = resources(renderer)

        // 새 Context에서 같은 숫자 ID가 유효한 상황을 명시적으로 만든다.
        if (!GLES30.glIsTexture(old.id)) {
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, old.id)
        }
        assertTrue(GLES30.glIsTexture(old.id))
        oldRequest.deferrableSurface.close()
        renderer.onDrawFrame(null)
        assertTrue(old.texture.isReleased)
        assertFalse(next.texture.isReleased)
        assertTrue(GLES30.glIsTexture(old.id))
        assertTrue(GLES30.glIsTexture(next.id))
    }

    @Test
    fun completionAfterFinalCloseAndGlThreadShutdownStillReleasesResources() {
        val renderer = newRenderer()
        startContext(renderer)
        val (request, resources) = provide(renderer)
        request.deferrableSurface.incrementUseCount()
        renderer.close()
        egl!!.close()
        egl = null
        renderer.onViewDetached()
        assertFalse(resources.texture.isReleased)

        request.deferrableSurface.close()
        assertFalse(resources.texture.isReleased) // close 요청만으로는 CameraX 사용 완료가 아니다.
        request.deferrableSurface.decrementUseCount()
        assertTrue(resources.texture.isReleased)
        assertFalse(resources.surface.isValid)
        renderer.close() // 최종 폐기는 중복 호출돼도 안전하다.

        val rejected = newRequest()
        renderer.onSurfaceRequest(rejected)
        assertTrue(rejected.deferrableSurface.surface.isDone)
    }

    @Test
    fun cancellationBeforeContextDoesNotSupplySurface() {
        val renderer = newRenderer()
        val request = newRequest()
        renderer.onSurfaceRequest(request)
        val surfaceFuture = request.deferrableSurface.surface
        request.deferrableSurface.close()
        assertTrue(surfaceFuture.isCancelled)
        startContext(renderer)
        renderer.onDrawFrame(null)
        assertTrue(surfaceFuture.isCancelled)
    }

    @Test
    fun completionOnAnotherThreadIsSerializedWithFrameConsumption() {
        val callbacks = Executors.newSingleThreadExecutor()
        try {
            val renderer = newRenderer(callbacks)
            startContext(renderer)
            repeat(20) {
                val (request, resources) = provide(renderer)
                val canvas = resources.surface.lockCanvas(null)
                canvas.drawColor(Color.RED)
                resources.surface.unlockCanvasAndPost(canvas)
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                request.deferrableSurface.close()
                repeat(5) { renderer.onDrawFrame(null) }
                callbacks.submit {}.get(3, TimeUnit.SECONDS)
                assertTrue(resources.texture.isReleased)
                renderer.onDrawFrame(null)
                assertFalse(GLES30.glIsTexture(resources.id))
            }
            renderer.close()
        } finally {
            callbacks.shutdown()
            assertTrue(callbacks.awaitTermination(3, TimeUnit.SECONDS))
        }
    }
}
