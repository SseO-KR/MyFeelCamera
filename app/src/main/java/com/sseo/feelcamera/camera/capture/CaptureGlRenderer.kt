package com.sseo.feelcamera.camera.capture

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import com.sseo.feelcamera.camera.filter.FilmPreset
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

private data class FrameResources(
    val sourceTextureId: Int,
    val outputTextureId: Int,
    val framebufferId: Int,
    val outputWidth: Int,
    val outputHeight: Int,
)

private data class OutputGeometry(
    val width: Int,
    val height: Int,
    val textureTransform: FloatArray,
)

internal class CaptureGlRenderer(
    private val assetManager: AssetManager,
) : Closeable {

    private var isInitialized = false

    private var program = 0
    private var lutTextureIds: Map<FilmPreset, Int> = emptyMap()

    private var positionLocation = -1
    private var texCoordLocation = -1
    private var textureTransformLocation = -1
    private var sourceTextureLocation = -1
    private var lutTextureLocation = -1
    private var lutStrengthLocation = -1
    private var outputAspectRatioLocation = -1

    private val vertexBuffer: FloatBuffer =
        ByteBuffer.allocateDirect(
            FULLSCREEN_QUAD.size * Float.SIZE_BYTES,
        )
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(FULLSCREEN_QUAD)
                position(0)
            }

    fun initialize() {
        check(!isInitialized) {
            "CaptureGlRenderer is already initialized."
        }

        // 반드시 CaptureEglSession.makeCurrent() 이후 실행해야 한다.
        val glVersion = GLES30.glGetString(GLES30.GL_VERSION)

        checkNotNull(glVersion) {
            "No current OpenGL context was found."
        }

        Log.d(
            TAG,
            "Initializing capture renderer " +
                    "glVersion=$glVersion " +
                    "thread=${Thread.currentThread().name}",
        )

        try {
            program = createProgram(
                vertexSource = readShaderAsset(
                    VERTEX_SHADER_ASSET_PATH,
                ),
                fragmentSource = readShaderAsset(
                    CAPTURE_FRAGMENT_SHADER_ASSET_PATH,
                ),
            )

            val uploadedLutTextures =
                mutableMapOf<FilmPreset, Int>()

            lutTextureIds = uploadedLutTextures

            FilmPreset.entries.forEach { film ->
                uploadedLutTextures[film] = createLutTexture(
                    assetPath = film.lutAssetPath,
                )
            }

            positionLocation =
                requireAttributeLocation("aPosition")
            texCoordLocation =
                requireAttributeLocation("aTexCoord")

            textureTransformLocation =
                requireUniformLocation("uTextureTransform")
            sourceTextureLocation =
                requireUniformLocation("uSourceTexture")
            lutTextureLocation =
                requireUniformLocation("uLut")
            lutStrengthLocation =
                requireUniformLocation("uLutStrength")
            outputAspectRatioLocation =
                requireUniformLocation("uOutputAspectRatio")

            val grainAmountLocation =
                requireUniformLocation("uGrainAmount")
            val grainScaleLocation =
                requireUniformLocation("uGrainScale")
            val vignetteStrengthLocation =
                requireUniformLocation("uVignetteStrength")
            val vignetteColorLocation =
                requireUniformLocation("uVignetteColor")

            GLES30.glUseProgram(program)

            // P0에서는 Grain과 Vignette를 적용하지 않는다.
            GLES30.glUniform1f(grainAmountLocation, 0f)
            GLES30.glUniform1f(grainScaleLocation, 1f)
            GLES30.glUniform1f(vignetteStrengthLocation, 0f)
            GLES30.glUniform3f(
                vignetteColorLocation,
                0f,
                0f,
                0f,
            )

            checkGlError("CaptureGlRenderer.initialize")

            isInitialized = true
        } catch (error: Throwable) {
            releaseGlResources()
            throw error
        }
    }

    override fun close() {
        releaseGlResources()
        isInitialized = false
    }

    private fun releaseGlResources() {
        if (lutTextureIds.isNotEmpty()) {
            val textureIds = lutTextureIds.values.toIntArray()

            GLES30.glDeleteTextures(
                textureIds.size,
                textureIds,
                0,
            )

            lutTextureIds = emptyMap()
        }

        if (program != 0) {
            GLES30.glDeleteProgram(program)
            program = 0
        }

        positionLocation = -1
        texCoordLocation = -1
        textureTransformLocation = -1
        sourceTextureLocation = -1
        lutTextureLocation = -1
        lutStrengthLocation = -1
        outputAspectRatioLocation = -1
    }

    private fun requireAttributeLocation(
        name: String,
    ): Int {
        val location = GLES30.glGetAttribLocation(
            program,
            name,
        )

        check(location >= 0) {
            "Shader attribute was not found: $name"
        }

        return location
    }

    private fun requireUniformLocation(
        name: String,
    ): Int {
        val location = GLES30.glGetUniformLocation(
            program,
            name,
        )

        check(location >= 0) {
            "Shader uniform was not found: $name"
        }

        return location
    }

    private fun checkGlError(
        operation: String,
    ) {
        val error = GLES30.glGetError()

        check(error == GLES30.GL_NO_ERROR) {
            "$operation failed: error=0x${error.toString(16)}"
        }
    }

    private fun readShaderAsset(
        assetPath: String,
    ): String {
        return assetManager.open(assetPath)
            .bufferedReader()
            .use { reader ->
                reader.readText()
            }
    }

    private fun createProgram(
        vertexSource: String,
        fragmentSource: String,
    ): Int {
        val vertexShader = compileShader(
            type = GLES30.GL_VERTEX_SHADER,
            source = vertexSource,
        )

        val fragmentShader = try {
            compileShader(
                type = GLES30.GL_FRAGMENT_SHADER,
                source = fragmentSource,
            )
        } catch (error: Throwable) {
            GLES30.glDeleteShader(vertexShader)
            throw error
        }

        val program = GLES30.glCreateProgram()

        if (program == 0) {
            GLES30.glDeleteShader(vertexShader)
            GLES30.glDeleteShader(fragmentShader)

            error("glCreateProgram failed.")
        }

        try {
            GLES30.glAttachShader(
                program,
                vertexShader,
            )
            GLES30.glAttachShader(
                program,
                fragmentShader,
            )

            GLES30.glLinkProgram(program)

            val linkStatus = IntArray(1)

            GLES30.glGetProgramiv(
                program,
                GLES30.GL_LINK_STATUS,
                linkStatus,
                0,
            )

            if (linkStatus[0] != GLES30.GL_TRUE) {
                val infoLog = GLES30.glGetProgramInfoLog(program)

                error(
                    "Shader program link failed: log=$infoLog",
                )
            }

            return program
        } catch (error: Throwable) {
            GLES30.glDeleteProgram(program)
            throw error
        } finally {
            GLES30.glDeleteShader(vertexShader)
            GLES30.glDeleteShader(fragmentShader)
        }
    }

    private fun compileShader(
        type: Int,
        source: String,
    ): Int {
        val shader = GLES30.glCreateShader(type)

        check(shader != 0) {
            "glCreateShader failed: type=$type"
        }

        GLES30.glShaderSource(
            shader,
            source,
        )
        GLES30.glCompileShader(shader)

        val compileStatus = IntArray(1)

        GLES30.glGetShaderiv(
            shader,
            GLES30.GL_COMPILE_STATUS,
            compileStatus,
            0,
        )

        if (compileStatus[0] != GLES30.GL_TRUE) {
            val infoLog = GLES30.glGetShaderInfoLog(shader)

            GLES30.glDeleteShader(shader)

            error(
                "Shader compilation failed: " +
                        "type=$type, log=$infoLog",
            )
        }

        return shader
    }

    private fun readLutAsset(
        assetPath: String,
    ): ByteBuffer {
        val bytes = assetManager.open(assetPath)
            .use { inputStream ->
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

    private fun createLutTexture(
        assetPath: String,
    ): Int {
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

        try {
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

            val error = GLES30.glGetError()

            check(error == GLES30.GL_NO_ERROR) {
                "Failed to upload LUT texture: " +
                        "path=$assetPath, " +
                        "error=0x${error.toString(16)}"
            }

            Log.d(
                TAG,
                "Capture LUT uploaded " +
                        "path=$assetPath " +
                        "textureId=$textureId " +
                        "size=${LUT_SIZE}x${LUT_SIZE}x$LUT_SIZE",
            )

            return textureId
        } catch (error: Throwable) {
            GLES30.glDeleteTextures(
                1,
                intArrayOf(textureId),
                0,
            )

            throw error
        } finally {
            // 다른 texture 업로드에 영향을 주지 않도록 기본값 복구
            GLES30.glPixelStorei(
                GLES30.GL_UNPACK_ALIGNMENT,
                4,
            )

            GLES30.glBindTexture(
                GLES30.GL_TEXTURE_3D,
                0,
            )
        }
    }

    private fun createFrameResources(
        sourceBitmap: Bitmap,
        outputWidth: Int,
        outputHeight: Int,
    ): FrameResources {
        check(!sourceBitmap.isRecycled) {
            "Source bitmap is already recycled."
        }
        check(sourceBitmap.width > 0 && sourceBitmap.height > 0) {
            "Source bitmap has an invalid size."
        }
        check(outputWidth > 0 && outputHeight > 0) {
            "Output size must be greater than zero."
        }

        var sourceTextureId = 0
        var outputTextureId = 0
        var framebufferId = 0

        try {
            // ImageProxy에서 변환한 원본 Bitmap을 올릴 texture
            sourceTextureId = createSourceTexture(
                bitmap = sourceBitmap,
            )

            // Fragment Shader 실행 결과를 받을 빈 texture
            outputTextureId = createOutputTexture(
                width = outputWidth,
                height = outputHeight,
            )

            // 화면 대신 output texture에 그리기 위한 framebuffer
            framebufferId = createFramebuffer(
                outputTextureId = outputTextureId,
            )

            checkGlError("createFrameResources")

            return FrameResources(
                sourceTextureId = sourceTextureId,
                outputTextureId = outputTextureId,
                framebufferId = framebufferId,
                outputWidth = outputWidth,
                outputHeight = outputHeight,
            )
        } catch (error: Throwable) {
            deleteFrameResources(
                sourceTextureId = sourceTextureId,
                outputTextureId = outputTextureId,
                framebufferId = framebufferId,
            )

            throw error
        } finally {
            GLES30.glBindTexture(
                GLES30.GL_TEXTURE_2D,
                0,
            )
            GLES30.glBindFramebuffer(
                GLES30.GL_FRAMEBUFFER,
                0,
            )
        }
    }

    private fun createSourceTexture(
        bitmap: Bitmap,
    ): Int {
        val textureIds = IntArray(1)

        GLES30.glGenTextures(
            1,
            textureIds,
            0,
        )

        val textureId = textureIds[0]

        check(textureId != 0) {
            "Failed to create source texture."
        }

        GLES30.glBindTexture(
            GLES30.GL_TEXTURE_2D,
            textureId,
        )

        setTextureParameters()

        GLUtils.texImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            bitmap,
            0,
        )

        return textureId
    }

    private fun createOutputTexture(
        width: Int,
        height: Int,
    ): Int {
        val textureIds = IntArray(1)

        GLES30.glGenTextures(
            1,
            textureIds,
            0,
        )

        val textureId = textureIds[0]

        check(textureId != 0) {
            "Failed to create output texture."
        }

        GLES30.glBindTexture(
            GLES30.GL_TEXTURE_2D,
            textureId,
        )

        setTextureParameters()

        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA8,
            width,
            height,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            null,
        )

        return textureId
    }

    private fun setTextureParameters() {
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_LINEAR,
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_LINEAR,
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE,
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE,
        )
    }

    private fun createFramebuffer(
        outputTextureId: Int,
    ): Int {
        val framebufferIds = IntArray(1)

        GLES30.glGenFramebuffers(
            1,
            framebufferIds,
            0,
        )

        val framebufferId = framebufferIds[0]

        check(framebufferId != 0) {
            "Failed to create framebuffer."
        }

        GLES30.glBindFramebuffer(
            GLES30.GL_FRAMEBUFFER,
            framebufferId,
        )

        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            outputTextureId,
            0,
        )

        val status = GLES30.glCheckFramebufferStatus(
            GLES30.GL_FRAMEBUFFER,
        )

        check(status == GLES30.GL_FRAMEBUFFER_COMPLETE) {
            "Framebuffer is incomplete: " +
                    "status=0x${status.toString(16)}"
        }

        return framebufferId
    }

    private fun releaseFrameResources(
        resources: FrameResources,
    ) {
        deleteFrameResources(
            sourceTextureId = resources.sourceTextureId,
            outputTextureId = resources.outputTextureId,
            framebufferId = resources.framebufferId,
        )
    }

    private fun deleteFrameResources(
        sourceTextureId: Int,
        outputTextureId: Int,
        framebufferId: Int,
    ) {
        if (framebufferId != 0) {
            GLES30.glDeleteFramebuffers(
                1,
                intArrayOf(framebufferId),
                0,
            )
        }

        val textureIds = intArrayOf(
            sourceTextureId,
            outputTextureId,
        ).filter { textureId ->
            textureId != 0
        }.toIntArray()

        if (textureIds.isNotEmpty()) {
            GLES30.glDeleteTextures(
                textureIds.size,
                textureIds,
                0,
            )
        }
    }

    private fun drawToFramebuffer(
        resources: FrameResources,
        film: FilmPreset,
        textureTransform: FloatArray,
    ) {
        check(isInitialized) {
            "CaptureGlRenderer is not initialized."
        }
        check(textureTransform.size >= 16) {
            "Texture transform must contain 16 values."
        }

        GLES30.glBindFramebuffer(
            GLES30.GL_FRAMEBUFFER,
            resources.framebufferId,
        )

        GLES30.glViewport(
            0,
            0,
            resources.outputWidth,
            resources.outputHeight,
        )

        GLES30.glClearColor(
            0f,
            0f,
            0f,
            1f,
        )
        GLES30.glClear(
            GLES30.GL_COLOR_BUFFER_BIT,
        )

        GLES30.glUseProgram(program)

        try {
            bindSourceTexture(
                textureId = resources.sourceTextureId,
            )

            bindLutTexture(
                film = film,
            )

            GLES30.glUniformMatrix4fv(
                textureTransformLocation,
                1,
                false,
                textureTransform,
                0,
            )

            GLES30.glUniform1f(
                outputAspectRatioLocation,
                resources.outputWidth.toFloat() /
                        resources.outputHeight.toFloat(),
            )

            bindVertexData()

            GLES30.glDrawArrays(
                GLES30.GL_TRIANGLE_STRIP,
                0,
                4,
            )

            checkGlError("drawToFramebuffer")
        } finally {
            GLES30.glDisableVertexAttribArray(
                positionLocation,
            )
            GLES30.glDisableVertexAttribArray(
                texCoordLocation,
            )

            GLES30.glActiveTexture(
                GLES30.GL_TEXTURE1,
            )
            GLES30.glBindTexture(
                GLES30.GL_TEXTURE_3D,
                0,
            )

            GLES30.glActiveTexture(
                GLES30.GL_TEXTURE0,
            )
            GLES30.glBindTexture(
                GLES30.GL_TEXTURE_2D,
                0,
            )

            GLES30.glBindFramebuffer(
                GLES30.GL_FRAMEBUFFER,
                0,
            )
        }
    }

    private fun bindSourceTexture(
        textureId: Int,
    ) {
        GLES30.glActiveTexture(
            GLES30.GL_TEXTURE0,
        )
        GLES30.glBindTexture(
            GLES30.GL_TEXTURE_2D,
            textureId,
        )

        GLES30.glUniform1i(
            sourceTextureLocation,
            0,
        )
    }

    private fun bindLutTexture(
        film: FilmPreset,
    ) {
        val textureId = checkNotNull(
            lutTextureIds[film],
        ) {
            "Capture LUT texture was not uploaded: film=$film"
        }

        GLES30.glActiveTexture(
            GLES30.GL_TEXTURE1,
        )
        GLES30.glBindTexture(
            GLES30.GL_TEXTURE_3D,
            textureId,
        )

        GLES30.glUniform1i(
            lutTextureLocation,
            1,
        )
        GLES30.glUniform1f(
            lutStrengthLocation,
            LUT_STRENGTH,
        )
    }

    private fun bindVertexData() {
        vertexBuffer.position(0)

        GLES30.glEnableVertexAttribArray(
            positionLocation,
        )
        GLES30.glVertexAttribPointer(
            positionLocation,
            2,
            GLES30.GL_FLOAT,
            false,
            STRIDE_BYTES,
            vertexBuffer,
        )

        vertexBuffer.position(2)

        GLES30.glEnableVertexAttribArray(
            texCoordLocation,
        )
        GLES30.glVertexAttribPointer(
            texCoordLocation,
            2,
            GLES30.GL_FLOAT,
            false,
            STRIDE_BYTES,
            vertexBuffer,
        )
    }

    private fun readFramebuffer(
        resources: FrameResources,
    ): Bitmap {
        val pixelByteCount = Math.multiplyExact(
            Math.multiplyExact(
                resources.outputWidth,
                resources.outputHeight,
            ),
            RGBA_CHANNEL_COUNT,
        )

        val pixelBuffer = ByteBuffer.allocateDirect(
            pixelByteCount,
        ).order(ByteOrder.nativeOrder())

        GLES30.glBindFramebuffer(
            GLES30.GL_FRAMEBUFFER,
            resources.framebufferId,
        )

        try {
            GLES30.glPixelStorei(
                GLES30.GL_PACK_ALIGNMENT,
                1,
            )

            GLES30.glReadPixels(
                0,
                0,
                resources.outputWidth,
                resources.outputHeight,
                GLES30.GL_RGBA,
                GLES30.GL_UNSIGNED_BYTE,
                pixelBuffer,
            )

            checkGlError("readFramebuffer")

            pixelBuffer.rewind()

            val bitmap = Bitmap.createBitmap(
                resources.outputWidth,
                resources.outputHeight,
                Bitmap.Config.ARGB_8888,
            )

            try {
                bitmap.copyPixelsFromBuffer(
                    pixelBuffer,
                )

                return bitmap
            } catch (error: Throwable) {
                bitmap.recycle()
                throw error
            }
        } finally {
            GLES30.glPixelStorei(
                GLES30.GL_PACK_ALIGNMENT,
                4,
            )

            GLES30.glBindFramebuffer(
                GLES30.GL_FRAMEBUFFER,
                0,
            )
        }
    }

    fun render(
        input: CaptureInput,
    ): Bitmap {
        check(isInitialized) {
            "CaptureGlRenderer is not initialized."
        }

        val geometry = createOutputGeometry(input)

        val resources = createFrameResources(
            sourceBitmap = input.bitmap,
            outputWidth = geometry.width,
            outputHeight = geometry.height,
        )

        return try {
            drawToFramebuffer(
                resources = resources,
                film = input.film,
                textureTransform = geometry.textureTransform,
            )

            readFramebuffer(
                resources = resources,
            )
        } finally {
            releaseFrameResources(resources)
        }
    }

    private fun createOutputGeometry(
        input: CaptureInput,
    ): OutputGeometry {
        val bitmapWidth = input.bitmap.width
        val bitmapHeight = input.bitmap.height
        val cropRect = input.cropRect

        check(
            cropRect.left >= 0 &&
                cropRect.top >= 0 &&
                cropRect.right <= bitmapWidth &&
                cropRect.bottom <= bitmapHeight &&
                cropRect.width() > 0 &&
                cropRect.height() > 0
        ) {
            "Invalid capture cropRect: " +
                "bitmap=${bitmapWidth}x$bitmapHeight, " +
                "cropRect=$cropRect"
        }

        val rotationDegrees = input.rotationDegrees

        check(
            rotationDegrees == 0 ||
                rotationDegrees == 90 ||
                rotationDegrees == 180 ||
                rotationDegrees == 270
        ) {
            "Unsupported capture rotation: $rotationDegrees"
        }

        // Shader는 output UV에서 source UV를 찾으므로,
        // 화면에 적용할 회전의 역방향으로 source 좌표를 계산한다.
        val rotationTransform = createRotationTransform(
            rotationDegrees = rotationDegrees,
        )

        val cropTransform = FloatArray(16).apply {
            Matrix.setIdentityM(this, 0)

            this[0] = cropRect.width().toFloat() / bitmapWidth.toFloat()
            this[5] = cropRect.height().toFloat() / bitmapHeight.toFloat()
            this[12] = cropRect.left.toFloat() / bitmapWidth.toFloat()
            this[13] = cropRect.top.toFloat() / bitmapHeight.toFloat()
        }

        // output UV → 회전된 crop-local UV → 전체 source texture UV
        val textureTransform = FloatArray(16)
        Matrix.multiplyMM(
            textureTransform,
            0,
            cropTransform,
            0,
            rotationTransform,
            0,
        )

        val swapsDimensions =
            rotationDegrees == 90 || rotationDegrees == 270

        val outputWidth = if (swapsDimensions) {
            cropRect.height()
        } else {
            cropRect.width()
        }
        val outputHeight = if (swapsDimensions) {
            cropRect.width()
        } else {
            cropRect.height()
        }

        Log.d(
            TAG,
            "Capture geometry " +
                "output=${outputWidth}x$outputHeight " +
                "rotationDegrees=$rotationDegrees " +
                "cropRect=$cropRect " +
                "textureTransform=${textureTransform.contentToString()}",
        )

        return OutputGeometry(
            width = outputWidth,
            height = outputHeight,
            textureTransform = textureTransform,
        )
    }

    private fun createRotationTransform(
        rotationDegrees: Int,
    ): FloatArray {
        return FloatArray(16).apply {
            Matrix.setIdentityM(this, 0)

            when (rotationDegrees) {
                0 -> Unit

                90 -> {
                    // source UV = (outputV, 1 - outputU)
                    this[0] = 0f
                    this[1] = -1f
                    this[4] = 1f
                    this[5] = 0f
                    this[12] = 0f
                    this[13] = 1f
                }

                180 -> {
                    // source UV = (1 - outputU, 1 - outputV)
                    this[0] = -1f
                    this[5] = -1f
                    this[12] = 1f
                    this[13] = 1f
                }

                270 -> {
                    // source UV = (1 - outputV, outputU)
                    this[0] = 0f
                    this[1] = 1f
                    this[4] = -1f
                    this[5] = 0f
                    this[12] = 1f
                    this[13] = 0f
                }
            }
        }
    }

    private companion object {
        const val TAG = "CaptureGlRenderer"

        const val VERTEX_SHADER_ASSET_PATH = "shader/filter_vertex.glsl"

        const val CAPTURE_FRAGMENT_SHADER_ASSET_PATH = "shader/filter_capture_fragment.glsl"

        const val LUT_SIZE = 32
        const val LUT_CHANNEL_COUNT = 3
        const val LUT_BYTE_COUNT = LUT_SIZE * LUT_SIZE * LUT_SIZE * LUT_CHANNEL_COUNT

        const val STRIDE_BYTES =
            4 * Float.SIZE_BYTES

        const val LUT_STRENGTH = 1.0f

        val FULLSCREEN_QUAD = floatArrayOf(
            // position x/y, texture u/v
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
            1f,  1f, 1f, 1f,
        )

        const val RGBA_CHANNEL_COUNT = 4
    }
}
