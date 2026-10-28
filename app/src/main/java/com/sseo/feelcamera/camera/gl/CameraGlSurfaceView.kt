package com.sseo.feelcamera.camera.gl

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import androidx.camera.core.Preview
import androidx.core.content.ContextCompat
import com.sseo.feelcamera.camera.filter.FilmPreset

class CameraGlSurfaceView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
) : GLSurfaceView(context, attributeSet) {

    private val renderer = CameraRenderer(
        assetManager = context.assets,
        requestRender = ::requestRender,
        resultExecutor = ContextCompat.getMainExecutor(context),
    )

    val surfaceProvider: Preview.SurfaceProvider = CameraSurfaceProvider(
        renderer = renderer,
    )

    internal val renderedFrame: RenderedPreviewFrame?
        get() = renderer.renderedFrame

    init {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true

        setRenderer(renderer)
        // RENDERMODE_CONTINUOUSLY는 프레임이 없어도 GPU가 낭비되기 때문에, 카메라 프레임이 도착할 때만 렌더링하도록 설정
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setFilmPreset(film: FilmPreset) {
        renderer.setFilmPreset(film)
    }

    fun setDisplayRotation(rotation: Int) {
        renderer.setDisplayRotation(rotation)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        renderer.onViewAttached()
    }

    override fun onDetachedFromWindow() {
        // superclass가 GL Thread 종료/EGL 해제를 기다린다. Renderer lock 밖에서 실행한다.
        super.onDetachedFromWindow()
        renderer.onViewDetached()
    }

    fun release() {
        renderer.close()
    }
}
