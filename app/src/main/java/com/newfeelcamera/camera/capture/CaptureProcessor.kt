package com.newfeelcamera.camera.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.ImageProxy
import com.newfeelcamera.camera.filter.FilmPreset
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

class CaptureProcessor @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
) : Closeable {

    // EGL Context는 eglMakeCurrent()를 통해 현재 스레드에 바인딩된다.
    // Capture GL 작업이 여러 스레드로 이동하면 Context 바인딩을 별도로 관리해야 하므로,
    // 모든 EGL/GL 작업을 동일한 스레드에서 순차적으로 실행하기 위해 전용 단일 스레드를 사용한다.
    private val executorService: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(
                runnable,
                THREAD_NAME,
            )
        }

    private val captureDispatcher: ExecutorCoroutineDispatcher =
        executorService.asCoroutineDispatcher()

    private val isClosed = AtomicBoolean(false)

    private var eglSession: CaptureEglSession? = null
    private var glRenderer: CaptureGlRenderer? = null

    private fun getOrCreateEglSession(): CaptureEglSession {
        check(!isClosed.get()) {
            "CaptureProcessor is already closed."
        }

        eglSession?.let { return it }

        return CaptureEglSession.create()
            .also { session ->
                eglSession = session

                Log.d(
                    TAG,
                    "Capture EGL session created " +
                            "thread=${Thread.currentThread().name}",
                )
            }
    }

    suspend fun processAndSave(
        imageProxy: ImageProxy,
        film: FilmPreset,
    ): Uri {
        return try {
            check(!isClosed.get()) {
                "CaptureProcessor is already closed."
            }

            withContext(captureDispatcher) {
                check(!isClosed.get()) {
                    "CaptureProcessor is already closed."
                }

                try {
                    val input = imageProxy.toCaptureInput(film)

                    Log.d(
                        TAG,
                        "Capture input " +
                            "bitmap=${input.bitmap.width}x${input.bitmap.height} " +
                            "cropRect=${input.cropRect} " +
                            "rotationDegrees=${input.rotationDegrees} " +
                            "film=${input.film}",
                    )

                    try {
                        renderAndSave(input)
                    } finally {
                        input.bitmap.recycle()
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: CaptureException) {
                    throw error
                } catch (error: Exception) {
                    throw CaptureException.ProcessingFailed(error)
                }
            }
        } finally {
            imageProxy.close()
        }
    }

    private fun getOrCreateGlRenderer(): CaptureGlRenderer {
        glRenderer?.let { return it }

        return CaptureGlRenderer(
            assetManager = appContext.assets,
        ).also { renderer ->
            renderer.initialize()
            glRenderer = renderer
        }
    }

    private fun renderAndSave(
        input: CaptureInput,
    ): Uri {
        val session = getOrCreateEglSession()

        session.makeCurrent()

        val renderer = getOrCreateGlRenderer()
        val filteredBitmap = renderer.render(input)

        return try {
            saveBitmapToMediaStore(filteredBitmap)
        } finally {
            filteredBitmap.recycle()
        }
    }

    private fun saveBitmapToMediaStore(
        bitmap: Bitmap,
    ): Uri {
        val contentResolver = appContext.contentResolver

        val contentValues = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "NewFeelCamera_${System.currentTimeMillis()}.jpg",
            )
            put(
                MediaStore.Images.Media.MIME_TYPE,
                "image/jpeg",
            )
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/NewFeelCamera",
            )
            put(
                MediaStore.Images.Media.IS_PENDING,
                1,
            )
        }

        val uri = try {
            contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues,
            )
        } catch (error: Exception) {
            throw CaptureException.SaveFailed(error)
        } ?: throw CaptureException.SaveFailed(
            IllegalStateException(
                "MediaStore insert returned null.",
            ),
        )

        var isPublished = false

        try {
            val outputStream = contentResolver.openOutputStream(
                uri,
                "w",
            ) ?: error(
                "MediaStore output stream could not be opened.",
            )

            outputStream.use { stream ->
                val compressed = bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    JPEG_QUALITY,
                    stream,
                )

                check(compressed) {
                    "Bitmap JPEG compression failed."
                }
            }

            val publishedValues = ContentValues().apply {
                put(
                    MediaStore.Images.Media.IS_PENDING,
                    0,
                )
            }

            val updatedRows = contentResolver.update(
                uri,
                publishedValues,
                null,
                null,
            )

            check(updatedRows > 0) {
                "Failed to publish MediaStore image."
            }

            isPublished = true
            return uri
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: CaptureException) {
            throw error
        } catch (error: Exception) {
            throw CaptureException.SaveFailed(error)
        } finally {
            if (!isPublished) {
                runCatching {
                    contentResolver.delete(
                        uri,
                        null,
                        null,
                    )
                }.onFailure { cleanupError ->
                    Log.w(
                        TAG,
                        "Failed to delete incomplete MediaStore item: uri=$uri",
                        cleanupError,
                    )
                }
            }
        }
    }

    override fun close() {
        if (!isClosed.compareAndSet(false, true)) return

        // close()는 ViewModel.onCleared()를 통해 Main Thread에서 호출될 수 있다.
        // EGL 자원은 생성한 NewFeel-Capture 스레드에서 해제해야 하므로
        // 해당 Executor에 해제 작업을 먼저 등록한다.
        executorService.execute {
            try {
                eglSession?.makeCurrent()
                glRenderer?.close()
            } finally {
                glRenderer = null

                try {
                    eglSession?.close()
                } finally {
                    eglSession = null

                    Log.d(
                        TAG,
                        "Capture resources released " +
                            "thread=${Thread.currentThread().name}",
                    )
                }
            }
        }

        captureDispatcher.close()
    }

    private companion object {
        const val TAG = "CaptureProcessor"
        const val THREAD_NAME = "NewFeel-Capture"
        const val JPEG_QUALITY = 95
    }
}
