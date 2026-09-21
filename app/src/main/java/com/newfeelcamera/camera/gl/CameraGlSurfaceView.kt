package com.newfeelcamera.camera.gl

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import androidx.camera.core.Preview
import androidx.core.content.ContextCompat
import com.newfeelcamera.camera.filter.FilmPreset

class CameraGlSurfaceView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
) : GLSurfaceView(context, attributeSet) {

    private val renderer = CameraRenderer(
        assetManager = context.assets,
        requestRender = ::requestRender,
        queueOnGlThread = ::queueEvent,
        resultExecutor = ContextCompat.getMainExecutor(context),
    )

    val surfaceProvider: Preview.SurfaceProvider = CameraSurfaceProvider(
        queueOnGlThread = ::queueEvent,
        renderer = renderer,
    )

    init {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true

        setRenderer(renderer)
        // RENDERMODE_CONTINUOUSLY는 프레임이 없어도 GPU가 낭비되기 때문에, 카메라 프레임이 도착할 때만 렌더링하도록 설정
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setFilmPreset(film: FilmPreset) {
        queueEvent {
            renderer.setFilmPreset(film)
        }
    }
}
