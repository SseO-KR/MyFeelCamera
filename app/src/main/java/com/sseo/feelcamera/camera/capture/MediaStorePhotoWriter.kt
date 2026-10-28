package com.sseo.feelcamera.camera.capture

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CancellationException

/** JPEG 작성과 MediaStore 공개를 한 작업으로 처리한다. 실패한 항목은 삭제한다. */
internal class MediaStorePhotoWriter(private val contentResolver: ContentResolver) {
    fun save(
        bitmap: Bitmap,
    ): Uri {
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
                "Pictures/FeelCamera",
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

    private companion object {
        const val TAG = "MediaStorePhotoWriter"
        const val JPEG_QUALITY = 95
    }
}
