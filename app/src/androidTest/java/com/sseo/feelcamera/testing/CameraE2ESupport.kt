package com.sseo.feelcamera.testing

import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.sseo.feelcamera.MainActivity
import com.sseo.feelcamera.camera.filter.FilmPreset
import com.sseo.feelcamera.camera.gl.CameraGlSurfaceView
import com.sseo.feelcamera.data.settings.AppSettingsRepository
import com.sseo.feelcamera.data.settings.ShutterSoundLevel
import com.sseo.feelcamera.ui.camera.LatestPhotoUriKey
import com.sseo.feelcamera.ui.labelRes
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestName
import kotlin.math.abs

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExtendedCameraTest

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ProcessRestartTest

/** Activity와 실제 CameraX를 사용한다. 카메라·렌더러·저장 구현을 대체하지 않는다. */
abstract class CameraE2ESupport {
    @get:Rule(order = 0) val compose = createEmptyComposeRule()
    @get:Rule(order = 1) val artifacts = FailureArtifactsRule()
    @get:Rule(order = 2) val testName = TestName()
    protected lateinit var scenario: ActivityScenario<MainActivity>
    protected val context get() = TestImages.context
    protected val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    protected open val resetSettings = true
    private var initialPhotos: Set<Uri>? = null

    @Before fun openApp() {
        assertEquals("E2E requires the fixed camera input documented in docs/testing.md",
            "true", InstrumentationRegistry.getArguments().getString("fixtureCamera"))
        if (resetSettings) resetAppSettings()
        initialPhotos = photos()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    protected fun resetAppSettings() = runBlocking {
        AppSettingsRepository(context).apply {
            setStartFilm(FilmPreset.WARM)
            setShowGrid(true)
            setShutterSoundLevel(ShutterSoundLevel.MEDIUM)
            setTelemetryConsent(false, false)
            saveCameraPermissionState(hasRequestedPermission = false)
        }
    }

    @After fun closeApp() {
        if (::scenario.isInitialized) {
            runCatching { device.takeScreenshot(File(TestImages.artifacts, "${testName.methodName}-end-screen.png")) }
            scenario.close()
        }
        // 테스트 시작 전에 존재했던 사진은 삭제하지 않는다.
        initialPhotos?.let { before ->
            (photos() - before).forEach { context.contentResolver.delete(it, null, null) }
        }
    }

    protected fun waitForCamera(film: FilmPreset) {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("shutter").fetchSemanticsNodes().singleOrNull()
                ?.config?.contains(SemanticsProperties.Disabled) == false
        }
        waitForFilmFrame(film)
    }

    protected fun waitForFilmFrame(film: FilmPreset, afterSequence: Long = -1) {
        compose.waitUntil(20_000) {
            preview()?.renderedFrame?.let { it.film == film && it.sequence > afterSequence } == true
        }
    }

    protected fun assertPreviewFilm(film: FilmPreset) {
        val expected = expectedCameraRgb(film)
        var actual: Bitmap? = null
        try {
            compose.waitUntil(15_000) {
                actual?.recycle()
                actual = null
                val view = preview() ?: return@waitUntil false
                if (view.width <= 0 || view.height <= 0 || !view.holder.surface.isValid) return@waitUntil false
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                actual = bitmap
                val done = CountDownLatch(1)
                var status = PixelCopy.ERROR_SOURCE_NO_DATA
                PixelCopy.request(view, bitmap, { status = it; done.countDown() }, Handler(Looper.getMainLooper()))
                if (!done.await(3, TimeUnit.SECONDS) || status != PixelCopy.SUCCESS) return@waitUntil false
                val means = meanRgb(bitmap)
                (0..2).all { abs(means[it] - expected[it]) <= 8 }
            }
        } catch (error: Throwable) {
            actual?.let { TestImages.save("e2e-preview-$film-actual", it) }
            throw error
        } finally { actual?.recycle() }
    }

    protected fun selectFilm(film: FilmPreset, waitForFrame: Boolean = true) {
        val expected = context.getString(film.labelRes)
        val dial = compose.onNodeWithTag("film_dial")
        val current = dial.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        if (current != expected) dial.performClick()
        dial.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected))
        if (waitForFrame) waitForFilmFrame(film)
    }

    protected fun takePhoto(film: FilmPreset): Uri {
        val before = photos()
        compose.onNodeWithTag("shutter").assertIsEnabled().performClick()
        var added = emptySet<Uri>()
        compose.waitUntil(30_000) { added = photos() - before; added.isNotEmpty() }
        assertEquals("One shutter must save one photo", 1, added.size)
        val uri = added.single()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("shutter").fetchSemanticsNodes().singleOrNull()
                ?.config?.contains(SemanticsProperties.Disabled) == false
        }
        compose.onNodeWithTag("gallery_thumbnail", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(LatestPhotoUriKey, uri.toString()))
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("gallery_image", useUnmergedTree = true).fetchSemanticsNodes()
                .singleOrNull()?.config?.get(LatestPhotoUriKey) == uri.toString()
        }
        assertSavedFilm(uri, film)
        return uri
    }

    protected fun photos(): Set<Uri> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        return context.contentResolver.query(collection, arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.OWNER_PACKAGE_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ? AND ${MediaStore.Images.Media.IS_PENDING} = 0",
            arrayOf(context.packageName, "Pictures/FeelCamera/"), null)!!.use { cursor ->
            buildSet { while (cursor.moveToNext()) add(ContentUris.withAppendedId(collection, cursor.getLong(0))) }
        }
    }

    protected fun preview(): CameraGlSurfaceView? {
        val found = AtomicReference<CameraGlSurfaceView?>()
        scenario.onActivity { found.set(findPreview(it.window.decorView)) }
        return found.get()
    }

    private fun findPreview(view: View): CameraGlSurfaceView? {
        if (view is CameraGlSurfaceView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findPreview(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    protected fun assertSavedFilm(uri: Uri, film: FilmPreset) {
        val resolver = context.contentResolver
        val bitmap = resolver.openInputStream(uri).use { requireNotNull(BitmapFactory.decodeStream(it)) }
        try {
            assertTrue("Invalid saved dimensions", bitmap.width > 0 && bitmap.height > 0)
            val reference = expectedCameraRgb(film)
            val means = meanRgb(bitmap)
            val name = "e2e-${film.name}-${uri.lastPathSegment}"
            resolver.openInputStream(uri)!!.use { source -> File(TestImages.artifacts, "$name.jpg").outputStream().use { source.copyTo(it) } }
            for (channel in 0..2) {
                assertTrue("$film channel=$channel expected=${reference[channel]} actual=$means; check fixture camera input",
                    abs(means[channel] - reference[channel]) <= 8)
            }
            resolver.query(uri, arrayOf(MediaStore.Images.Media.IS_PENDING, MediaStore.Images.Media.MIME_TYPE),
                null, null, null)!!.use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)); assertEquals("image/jpeg", it.getString(1))
            }
        } finally { bitmap.recycle() }
    }

    private fun expectedCameraRgb(film: FilmPreset): List<Int> {
        val channels = InstrumentationRegistry.getInstrumentation().context.assets
            .open("film/references.json").bufferedReader().use { JSONObject(it.readText()) }
            .getJSONObject("camera_expected_rgb").getJSONArray(film.name.lowercase())
        return (0..2).map { channels.getInt(it) }
    }

    private fun meanRgb(bitmap: Bitmap): List<Double> {
        val sums = LongArray(3)
        var count = 0
        // 가상 카메라의 해상도·회전·가장자리 처리에 영향받지 않는 중앙 영역.
        for (y in bitmap.height * 3 / 8 until bitmap.height * 5 / 8 step 4) {
            for (x in bitmap.width * 3 / 8 until bitmap.width * 5 / 8 step 4) {
                val pixel = bitmap.getPixel(x, y)
                sums[0] += Color.red(pixel); sums[1] += Color.green(pixel); sums[2] += Color.blue(pixel)
                count++
            }
        }
        assertTrue("Empty comparison area", count > 0)
        return sums.map { it.toDouble() / count }
    }
}
