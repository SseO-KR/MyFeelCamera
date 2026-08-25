package com.newfeelcamera.ui.camera.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Surface
import androidx.camera.core.SurfaceRequest
import kotlinx.coroutines.Runnable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executor
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class CameraRenderer(
    private val requestRender: () -> Unit,
    private val queueOnGlThread: (Runnable) -> Unit,
    private val resultExecutor: Executor
) : GLSurfaceView.Renderer {

    private data class SurfaceBinding(
        val request: SurfaceRequest,
        val surface: Surface,
        val surfaceTexture: SurfaceTexture
    )

    private val frameAvailable = AtomicBoolean(false)
    private val textureTransform = FloatArray(16)

    private var oesTextureId = 0
    private var inputSurfaceTexture: SurfaceTexture? = null
    private var pendingRequest: SurfaceRequest? = null
    private var activeBinding: SurfaceBinding? = null
    private val retiredBindings = mutableListOf<SurfaceBinding>()

    private var program = 0
    private var positionLocation = 0
    private var texCoordLocation = 0
    private var textureTransformLocation = 0
    private var cameraTextureLocation = 0

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(FULLSCREEN_QUAD.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(FULLSCREEN_QUAD)
            position(0)
        }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // EGL context 재생성됐다면 기존 SurfaceRequest는 새 SurfaceTexture를 사용할 수 없으므로 invalidate
        retireActiveBinding()

        inputSurfaceTexture?.release()
        inputSurfaceTexture = null
        oesTextureId = 0

        frameAvailable.store(false)
        Matrix.setIdentityM(textureTransform, 0)

        createInputSurfaceTexture()

        program = createProgram(
            vertexSource = VERTEX_SHADER,
            fragmentSource = FRAGMENT_SHADER,
        )

        positionLocation = GLES30.glGetAttribLocation(program, "aPosition")
        texCoordLocation = GLES30.glGetAttribLocation(program, "aTexCoord")
        textureTransformLocation = GLES30.glGetUniformLocation(program, "uTexTransform")
        cameraTextureLocation = GLES30.glGetUniformLocation(program, "uCameraTexture")

        GLES30.glClearColor(0f, 0f, 0f, 1f)

        providePendingSurfaceIfPossible()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        val surfaceTexture = inputSurfaceTexture ?: return
        if (frameAvailable.compareAndSet(true, false)){
            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(textureTransform)
        }

        drawCameraTexture()
    }

    // GL Thread에서 실행
    fun onSurfaceRequest(surfaceRequest: SurfaceRequest) {
        pendingRequest?.willNotProvideSurface()

        retireActiveBinding()

        pendingRequest = surfaceRequest
        providePendingSurfaceIfPossible()
    }

    private fun createInputSurfaceTexture() {
        val textureIds = IntArray(1)

        GLES30.glGenTextures(1, textureIds, 0)
        oesTextureId = textureIds[0]

        GLES30.glBindTexture(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            oesTextureId,
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_LINEAR,
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_LINEAR,
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE,
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE,
        )

        inputSurfaceTexture = SurfaceTexture(oesTextureId).also { surfaceTexture ->
            surfaceTexture.setOnFrameAvailableListener {
                frameAvailable.store(true)

                // 콜백은 GL 스레드가 아닐 수 있음
                requestRender()
            }
        }
    }

    private fun providePendingSurfaceIfPossible(){
        val request = pendingRequest ?: return
        val surfaceTexture = inputSurfaceTexture ?: return

        val resolution = request.resolution

        surfaceTexture.setDefaultBufferSize(
            resolution.width,
            resolution.height,
        )

        val surface = Surface(surfaceTexture)
        val binding = SurfaceBinding(
            request = request,
            surface = surface,
            surfaceTexture = surfaceTexture,
        )

        pendingRequest = null
        activeBinding = binding

        request.provideSurface(
            surface,
            resultExecutor,
        ) {
            // CameraX 가 더 이상 이 Surface를 쓰지 않는 시점
            // GL 관련 상태 변경은 다시 GL 쓰레드에서 처리한다.
            queueOnGlThread {
                Runnable {
                    releaseBinding(binding)
                }
            }
        }
    }

    private fun retireActiveBinding() {
        val binding = activeBinding ?: return

        activeBinding = null
        retiredBindings += binding

        if(inputSurfaceTexture === binding.surfaceTexture){
            inputSurfaceTexture = null
            oesTextureId = 0
        }

        binding.request.invalidate()
    }

    private fun releaseBinding(binding: SurfaceBinding){
        binding.surface.release()
        binding.surfaceTexture.release()

        retiredBindings.remove(binding)

        if (activeBinding === binding){
            activeBinding = null
            inputSurfaceTexture = null
            oesTextureId = 0
        }
    }

    private fun drawCameraTexture() {
        GLES30.glUseProgram(program)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            oesTextureId,
        )
        GLES30.glUniform1i(cameraTextureLocation, 0)
        GLES30.glUniformMatrix4fv(
            textureTransformLocation,
            1,
            false,
            textureTransform,
            0,
        )

        vertexBuffer.position(0)
        GLES30.glEnableVertexAttribArray(positionLocation)
        GLES30.glVertexAttribPointer(
            positionLocation,
            2,
            GLES30.GL_FLOAT,
            false,
            STRIDE_BYTES,
            vertexBuffer,
        )

        vertexBuffer.position(2)
        GLES30.glEnableVertexAttribArray(texCoordLocation)
        GLES30.glVertexAttribPointer(
            texCoordLocation,
            2,
            GLES30.GL_FLOAT,
            false,
            STRIDE_BYTES,
            vertexBuffer,
        )

        GLES30.glDrawArrays(
            GLES30.GL_TRIANGLE_STRIP,
            0,
            4,
        )

        GLES30.glDisableVertexAttribArray(positionLocation)
        GLES30.glDisableVertexAttribArray(texCoordLocation)
    }

    private fun createProgram(
        vertexSource: String,
        fragmentSource: String,
    ): Int {
        val vertexShader = compileShader(
            type = GLES30.GL_VERTEX_SHADER,
            source = vertexSource,
        )
        val fragmentShader = compileShader(
            type = GLES30.GL_FRAGMENT_SHADER,
            source = fragmentSource,
        )

        return GLES30.glCreateProgram().also { program ->
            GLES30.glAttachShader(program, vertexShader)
            GLES30.glAttachShader(program, fragmentShader)
            GLES30.glLinkProgram(program)

            val linkStatus = IntArray(1)
            GLES30.glGetProgramiv(
                program,
                GLES30.GL_LINK_STATUS,
                linkStatus,
                0,
            )

            check(linkStatus[0] == GLES30.GL_TRUE) {
                GLES30.glGetProgramInfoLog(program)
            }

            GLES30.glDeleteShader(vertexShader)
            GLES30.glDeleteShader(fragmentShader)
        }
    }

    private fun compileShader(
        type: Int,
        source: String,
    ): Int {
        return GLES30.glCreateShader(type).also { shader ->
            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)

            val compileStatus = IntArray(1)
            GLES30.glGetShaderiv(
                shader,
                GLES30.GL_COMPILE_STATUS,
                compileStatus,
                0,
            )

            check(compileStatus[0] == GLES30.GL_TRUE){
                GLES30.glGetShaderInfoLog(shader)
            }
        }
    }

    private companion object {
        private const val STRIDE_BYTES = 4 * 4

        private val FULLSCREEN_QUAD = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
            1f,  1f, 1f, 1f,
        )

        private val VERTEX_SHADER = """
            #version 300 es

            layout(location = 0) in vec2 aPosition;
            layout(location = 1) in vec2 aTexCoord;

            uniform mat4 uTexTransform;

            out vec2 vTexCoord;

            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vTexCoord = (uTexTransform * vec4(aTexCoord, 0.0, 1.0)).xy;
            }
        """.trimIndent()

        private val FRAGMENT_SHADER = """
            #version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require

            precision mediump float;

            in vec2 vTexCoord;

            uniform samplerExternalOES uCameraTexture;

            out vec4 outColor;

            void main() {
                outColor = texture(uCameraTexture, vTexCoord);
            }
        """.trimIndent()
    }
}