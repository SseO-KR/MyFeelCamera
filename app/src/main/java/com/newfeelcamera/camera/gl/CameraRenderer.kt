package com.newfeelcamera.camera.gl

import android.content.res.AssetManager
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import androidx.camera.core.SurfaceRequest
import com.newfeelcamera.camera.filter.FilmPreset
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
    private val assetManager: AssetManager,
    private val requestRender: () -> Unit,
    private val queueOnGlThread: (Runnable) -> Unit,
    private val resultExecutor: Executor,
) : GLSurfaceView.Renderer {

    private data class SurfaceBinding(
        val request: SurfaceRequest,
        val surface: Surface,
        val surfaceTexture: SurfaceTexture,
        val glContextGeneration: Int,
    )

    private val frameAvailable = AtomicBoolean(false)
    private val textureTransform = FloatArray(16)

    private var glContextGeneration = 0
    private var oesTextureId = 0
    private var selectedFilm = FilmPreset.WARM
    private var lutTextureIds: Map<FilmPreset, Int> = emptyMap()
    private var inputSurfaceTexture: SurfaceTexture? = null
    private var cameraTransformSnapshot: CameraTransformSnapshot? = null
    private var shouldLogTextureTransform = false

    private var pendingRequest: SurfaceRequest? = null
    private var activeBinding: SurfaceBinding? = null
    private val retiredBindings = mutableListOf<SurfaceBinding>()

    private var program = 0
    private var positionLocation = 0
    private var texCoordLocation = 0
    private var textureTransformLocation = 0
    private var cameraTextureLocation = 0

    private var lutTextureLocation = -1
    private var lutStrengthLocation = -1
    private var outputAspectRatioLocation = -1

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(FULLSCREEN_QUAD.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(FULLSCREEN_QUAD)
            position(0)
        }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        glContextGeneration += 1

        Log.d(
            TAG,
            "onSurfaceCreated generation=$glContextGeneration thread=${Thread.currentThread().name}",
        )

        // EGL context 재생성됐다면 기존 SurfaceRequest는 새 SurfaceTexture를 사용할 수 없으므로 invalidate
        retireActiveBinding()

        inputSurfaceTexture?.release()
        inputSurfaceTexture = null
        oesTextureId = 0

        frameAvailable.store(false)
        Matrix.setIdentityM(textureTransform, 0)

        createInputSurfaceTexture()

        lutTextureIds = FilmPreset.entries.associateWith { film ->
            createLutTexture(assetPath = film.lutAssetPath)
        }

        program = createProgram(
            vertexSource = readShaderAsset(
                VERTEX_SHADER_ASSET_PATH,
            ),
            fragmentSource = readShaderAsset(
                PREVIEW_FRAGMENT_SHADER_ASSET_PATH,
            ),
        )

        positionLocation = GLES30.glGetAttribLocation(program, "aPosition")
        texCoordLocation = GLES30.glGetAttribLocation(program, "aTexCoord")

        textureTransformLocation =
            GLES30.glGetUniformLocation(program, "uTextureTransform")
        cameraTextureLocation =
            GLES30.glGetUniformLocation(program, "uSourceTexture")

        lutTextureLocation =
            GLES30.glGetUniformLocation(program, "uLut")
        lutStrengthLocation =
            GLES30.glGetUniformLocation(program, "uLutStrength")
        outputAspectRatioLocation =
            GLES30.glGetUniformLocation(program, "uOutputAspectRatio")

        val grainAmountLocation =
            GLES30.glGetUniformLocation(program, "uGrainAmount")
        val grainScaleLocation =
            GLES30.glGetUniformLocation(program, "uGrainScale")
        val vignetteStrengthLocation =
            GLES30.glGetUniformLocation(program, "uVignetteStrength")
        val vignetteColorLocation =
            GLES30.glGetUniformLocation(program, "uVignetteColor")

        check(positionLocation >= 0) {
            "aPosition attribute was not found"
        }
        check(texCoordLocation >= 0) {
            "aTexCoord attribute was not found"
        }
        check(textureTransformLocation >= 0) {
            "uTextureTransform uniform was not found"
        }
        check(cameraTextureLocation >= 0) {
            "uSourceTexture uniform was not found"
        }
        check(lutTextureLocation >= 0) {
            "uLut uniform was not found"
        }
        check(lutStrengthLocation >= 0) {
            "uLutStrength uniform was not found"
        }
        check(outputAspectRatioLocation >= 0) {
            "uOutputAspectRatio uniform was not found"
        }
        check(grainAmountLocation >= 0) {
            "uGrainAmount uniform was not found"
        }
        check(grainScaleLocation >= 0) {
            "uGrainScale uniform was not found"
        }
        check(vignetteStrengthLocation >= 0) {
            "uVignetteStrength uniform was not found"
        }
        check(vignetteColorLocation >= 0) {
            "uVignetteColor uniform was not found"
        }

        GLES30.glUseProgram(program)
        GLES30.glUniform1f(grainAmountLocation, 0f)
        GLES30.glUniform1f(grainScaleLocation, 1f)
        GLES30.glUniform1f(vignetteStrengthLocation, 0f)
        GLES30.glUniform3f(vignetteColorLocation, 0f, 0f, 0f)

        GLES30.glClearColor(0f, 0f, 0f, 1f)

        providePendingSurfaceIfPossible()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)

        if (height > 0) {
            GLES30.glUseProgram(program)
            GLES30.glUniform1f(
                outputAspectRatioLocation,
                width.toFloat() / height.toFloat(),
            )
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        val surfaceTexture = inputSurfaceTexture ?: return
        if (frameAvailable.compareAndSet(true, false)){
            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(textureTransform)

            if(shouldLogTextureTransform){
                Log.d(
                    TAG,
                    "surfaceTextureTransform=" +
                            textureTransform.contentToString(),
                )

                shouldLogTextureTransform = false
            }
        }

        drawCameraTexture()
    }

    // GL Thread에서 실행
    fun onSurfaceRequest(surfaceRequest: SurfaceRequest) {
        Log.d(
            TAG,
            "onSurfaceRequest request=${System.identityHashCode(surfaceRequest)} " +
                    "resolution=${surfaceRequest.resolution} " +
                    "generation=$glContextGeneration " +
                    "thread=${Thread.currentThread().name}",
        )

        pendingRequest?.let { request ->
            request.clearTransformationInfoListener()
            request.willNotProvideSurface()
        }

        retireActiveBinding()

        cameraTransformSnapshot = null
        pendingRequest = surfaceRequest

        if(inputSurfaceTexture == null){
            frameAvailable.store(false)
            createInputSurfaceTexture()
        }

        providePendingSurfaceIfPossible()
    }

    // GL thread에서 호출
    fun setFilmPreset(film: FilmPreset) {
        if (selectedFilm == film) return

        val previousFilm = selectedFilm
        selectedFilm = film

        Log.d(
            TAG,
            "Film changed " +
                    "previous=$previousFilm " +
                    "selected=$selectedFilm",
        )

        // RenderMode가 RENDERMODE_WHEN_DIRTY 이기 떄문에, 명시적인 렌더 요청이 있을 때에만 requestRender 호출
        requestRender()
    }

    fun onTransformationInfo(
        request: SurfaceRequest,
        snapshot: CameraTransformSnapshot,
    ) {
        val isCurrentRequest =
            pendingRequest === request ||
                    activeBinding?.request === request

        if (!isCurrentRequest) return

        cameraTransformSnapshot = snapshot
        shouldLogTextureTransform = true

        Log.d(
            TAG,
            "onTransformationInfo " +
                    "request=${System.identityHashCode(request)} " +
                    "cropRect=${snapshot.cropRect} " +
                    "rotationDegrees=${snapshot.rotationDegrees} " +
                    "hasCameraTransform=${snapshot.hasCameraTransform} " +
                    "isMirroring=${snapshot.isMirroring} " +
                    "thread=${Thread.currentThread().name}",
        )

        requestRender()
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
            glContextGeneration = glContextGeneration,
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
                releaseBinding(binding)
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

        binding.request.clearTransformationInfoListener()
        binding.request.invalidate()
    }

    private fun releaseBinding(binding: SurfaceBinding){
        binding.request.clearTransformationInfoListener()
        binding.surface.release()

        if (binding.glContextGeneration == glContextGeneration) {
            binding.surfaceTexture.detachFromGLContext()
        }

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
        GLES30.glUniform1i(
            cameraTextureLocation,
            0,
        )

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)

        val selectedLutTextureId = checkNotNull(lutTextureIds[selectedFilm]) {
            "LUT texture was not uploaded: film=$selectedFilm"
        }

        // LUT 텍스처를 세이더가 사용할 수 있게 연결
        // 실제 색상은 Fragment Shader에서 변경
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, selectedLutTextureId)
        GLES30.glUniform1i(
            lutTextureLocation,
            1,
        )
        GLES30.glUniform1f(
            lutStrengthLocation,
            LUT_STRENGTH,
        )

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

        // 지금까지 설정한 Shader, Texture, Uniform, Vertex 데이터를 이용해서 화면을 그리라고 명령.
        // GPU의 그래픽 파이프라인이 동작
        // 이때, Fragment Shader가 실행됨
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

    private fun readShaderAsset(assetPath: String): String {
        return assetManager.open(assetPath)
            .bufferedReader()
            .use { reader ->
                reader.readText()
            }
    }

    private fun readLutAsset(assetPath: String): ByteBuffer {
        val bytes = assetManager.open(assetPath).use { inputStream ->
            inputStream.readBytes()
        }

        check(bytes.size == LUT_BYTE_COUNT) {
            "Invalid LUT size: " +
                    "path=$assetPath, " +
                    "expected=$LUT_BYTE_COUNT, " +
                    "actual=${bytes.size}"
        }

        return ByteBuffer.allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
            .apply {
                put(bytes)
                flip()
            }
    }

    private fun createLutTexture(assetPath: String): Int {
        val lutBuffer = readLutAsset(assetPath)
        val textureIds = IntArray(1)

        GLES30.glGenTextures(
            1,
            textureIds,
            0,
        )

        val textureId = textureIds[0]

        check(textureId != 0) {
            "Failed to create LUT texture: path=$assetPath"
        }

        GLES30.glBindTexture(
            GLES30.GL_TEXTURE_3D,
            textureId,
        )

        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_3D,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_LINEAR,
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_3D,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_LINEAR,
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_3D,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE,
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_3D,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE,
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_3D,
            GLES30.GL_TEXTURE_WRAP_R,
            GLES30.GL_CLAMP_TO_EDGE,
        )

        GLES30.glPixelStorei(
            GLES30.GL_UNPACK_ALIGNMENT,
            1,
        )

        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D,
            0,
            GLES30.GL_RGB8,
            LUT_SIZE,
            LUT_SIZE,
            LUT_SIZE,
            0,
            GLES30.GL_RGB,
            GLES30.GL_UNSIGNED_BYTE,
            lutBuffer,
        )

        GLES30.glPixelStorei(
            GLES30.GL_UNPACK_ALIGNMENT,
            4,
        )

        GLES30.glBindTexture(
            GLES30.GL_TEXTURE_3D,
            0,
        )

        val error = GLES30.glGetError()
        check(error == GLES30.GL_NO_ERROR) {
            "Failed to upload LUT texture: " +
                    "path=$assetPath, " +
                    "error=0x${error.toString(16)}"
        }

        Log.d(
            TAG,
            "LUT texture uploaded " +
                    "path=$assetPath " +
                    "textureId=$textureId " +
                    "size=${LUT_SIZE}x${LUT_SIZE}x$LUT_SIZE",
        )

        return textureId
    }

    private companion object {
        private const val STRIDE_BYTES = 4 * 4

        private const val LUT_SIZE = 32
        private const val LUT_CHANNEL_COUNT = 3
        private const val LUT_BYTE_COUNT = LUT_SIZE * LUT_SIZE * LUT_SIZE * LUT_CHANNEL_COUNT


        // LUT 강도 (예: 1.0f이면 LUT 결과를 100%로 적용하겠다.)
        private const val LUT_STRENGTH = 1.0f

        private const val VERTEX_SHADER_ASSET_PATH =
            "shader/filter_vertex.glsl"

        private const val PREVIEW_FRAGMENT_SHADER_ASSET_PATH =
            "shader/filter_preview_fragment.glsl"

        private val FULLSCREEN_QUAD = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
            1f,  1f, 1f, 1f,
        )

        private const val TAG = "CameraRenderer"
    }
}
