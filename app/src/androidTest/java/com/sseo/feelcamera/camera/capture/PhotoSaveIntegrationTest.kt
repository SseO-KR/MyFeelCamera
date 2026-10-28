package com.sseo.feelcamera.camera.capture

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sseo.feelcamera.camera.filter.FilmPreset
import com.sseo.feelcamera.testing.FailureArtifactsRule
import com.sseo.feelcamera.testing.TestImages
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoSaveIntegrationTest {
    @get:Rule val artifacts = FailureArtifactsRule()
    private val resolver get() = TestImages.context.contentResolver
    private val created = mutableListOf<Uri>()

    @After fun cleanup() { created.forEach { resolver.delete(it, null, null) } }

    @Test fun bothFilmsSurviveJpegSaveWithCorrectMetadataAndOrientation() = runBlocking {
        val input = TestImages.input()
        try {
            CaptureProcessor(TestImages.context).use { processor ->
                FilmPreset.entries.forEach { film ->
                    listOf(0, 90).forEach { rotation ->
                        val saved = processor.processAndSave(input.asInput(film, rotation))
                        created += saved.uri
                        val actual = resolver.openInputStream(saved.uri).use { requireNotNull(BitmapFactory.decodeStream(it)) }
                        val reference = TestImages.reference(film)
                        val oriented = TestImages.geometry(reference, android.graphics.Rect(0, 0, input.width, input.height), rotation)
                        val expected = jpegRoundTrip(oriented)
                        try {
                            TestImages.assertMatches("saved-$film-$rotation", expected, actual, 1.5, 14)
                            assertEquals(PhotoOrientation.fromDimensions(actual.width, actual.height), saved.orientation)
                        } finally { actual.recycle(); reference.recycle(); oriented.recycle(); expected.recycle() }
                        resolver.openInputStream(saved.uri)!!.use {
                            assertEquals("JPEG SOI marker", 0xff, it.read())
                            assertEquals("JPEG SOI marker", 0xd8, it.read())
                        }
                        resolver.query(saved.uri, arrayOf(MediaStore.Images.Media.MIME_TYPE,
                            MediaStore.Images.Media.RELATIVE_PATH, MediaStore.Images.Media.IS_PENDING),
                            null, null, null)!!.use {
                            assertTrue(it.moveToFirst())
                            assertEquals("image/jpeg", it.getString(0))
                            assertEquals("Pictures/FeelCamera/", it.getString(1))
                            assertEquals(0, it.getInt(2))
                        }
                    }
                }
            }
        } finally { input.recycle() }
    }

    @Test fun consecutiveSavesCreateDifferentUrisAndKeepEarlierPhotosReadable() = runBlocking {
        val input = TestImages.input()
        try {
            CaptureProcessor(TestImages.context).use { processor ->
                repeat(3) { created += processor.processAndSave(input.asInput(FilmPreset.WARM)).uri }
            }
            assertEquals(3, created.toSet().size)
            created.forEach { uri -> resolver.openInputStream(uri)!!.use { assertTrue(it.read() >= 0) } }
        } finally { input.recycle() }
    }

    @Test fun processingFailureDoesNotPreventNextSuccessfulSave() = runBlocking {
        val input = TestImages.input()
        try {
            CaptureProcessor(TestImages.context).use { processor ->
                try {
                    processor.processAndSave(input.asInput(FilmPreset.WARM, rotation = 45))
                    fail("Invalid rotation must fail")
                } catch (_: CaptureException.ProcessingFailed) { }
                created += processor.processAndSave(input.asInput(FilmPreset.DUSK)).uri
            }
            resolver.openInputStream(created.single())!!.close()
        } finally { input.recycle() }
    }

    @Test fun writeAndPublishFailuresRemoveIncompleteRowsAndAllowRetry() {
        val bitmap = TestImages.reference(FilmPreset.WARM)
        val provider = FailingMediaProvider(resolver)
        provider.attachInfo(TestImages.context, ProviderInfo().apply { authority = "media" })
        val writer = MediaStorePhotoWriter(ContentResolver.wrap(provider))
        try {
            for (failure in listOf("write", "publish", "publish-zero")) {
                provider.failure = failure
                assertThrows(CaptureException.SaveFailed::class.java) { writer.save(bitmap) }
                val uri = requireNotNull(provider.lastInserted)
                created += uri // cleanup even if the deletion assertion fails
                resolver.query(uri, arrayOf(MediaStore.Images.Media._ID), null, null, null)!!.use {
                    assertFalse("Incomplete $failure row was left behind", it.moveToFirst())
                }
                // 이미 삭제된 URI를 다시 delete하면 Android 37에서 SecurityException이 발생한다.
                created.remove(uri)
            }
            provider.failure = null
            created += writer.save(bitmap)
            resolver.openInputStream(created.last())!!.close()
        } finally { bitmap.recycle() }
    }

    private fun jpegRoundTrip(source: Bitmap): Bitmap {
        val bytes = ByteArrayOutputStream().use {
            assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 95, it))
            it.toByteArray()
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}

/** 실패 지점만 주입하고 insert/write/delete는 실제 MediaStore에 위임한다. */
private class FailingMediaProvider(private val real: ContentResolver) : ContentProvider() {
    var failure: String? = null
    var lastInserted: Uri? = null
    override fun onCreate() = true
    override fun getType(uri: Uri): String? = real.getType(uri)
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor? =
        real.query(uri, projection, selection, selectionArgs, sortOrder)
    override fun insert(uri: Uri, values: ContentValues?): Uri? = real.insert(uri, values).also { lastInserted = it }
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) =
        real.delete(uri, selection, selectionArgs)
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        if (failure == "publish") throw IllegalStateException("Injected publish failure")
        if (failure == "publish-zero") return 0
        return real.update(uri, values, selection, selectionArgs)
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (failure == "write") throw java.io.FileNotFoundException("Injected output failure")
        return real.openFileDescriptor(uri, mode)
    }
}
