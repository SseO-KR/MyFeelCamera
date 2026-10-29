package com.sseo.feelcamera.ui.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.content.ContentUris
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import com.sseo.feelcamera.MainActivity
import com.sseo.feelcamera.R
import com.sseo.feelcamera.camera.filter.FilmPreset
import com.sseo.feelcamera.camera.gl.CameraGlSurfaceView
import com.sseo.feelcamera.testing.FailureArtifactsRule
import com.sseo.feelcamera.testing.TestImages
import com.sseo.feelcamera.ui.labelRes
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real camera against the emulator's virtual scene, without fixed-color assumptions. */
@RunWith(AndroidJUnit4::class)
class CameraSceneCaptureE2ETest {
    @get:Rule(order = 0) val compose = createEmptyComposeRule()
    @get:Rule(order = 1) val failures = FailureArtifactsRule()
    @get:Rule(order = 2) val cameraPermission: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.CAMERA)
    private val context get() = TestImages.context
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val captures = JSONArray()
    private val checkpoints = JSONArray()
    private var initialPhotos = emptySet<Uri>()

    @Test fun actualSceneCapturesFilmsVolumeKeysAndRecoversAfterNavigation() {
        initialPhotos = photos()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForShutter()
        selectFilm(FilmPreset.WARM)
        checkpoint("first-warm-frame")
        takePhoto("warm-screen", FilmPreset.WARM)
        selectFilm(FilmPreset.DUSK)
        takePhoto("dusk-screen", FilmPreset.DUSK)
        selectFilm(FilmPreset.WARM)
        takePhoto("warm-roundtrip", FilmPreset.WARM)
        takePhoto("volume-up", FilmPreset.WARM, KeyEvent.KEYCODE_VOLUME_UP)
        takePhoto("volume-down", FilmPreset.WARM, KeyEvent.KEYCODE_VOLUME_DOWN)

        val beforeTen = photos()
        repeat(10) { index ->
            val film = if (index % 2 == 0) FilmPreset.WARM else FilmPreset.DUSK
            selectFilm(film)
            takePhoto("sequential-${index + 1}", film)
        }
        assertEquals("Ten shutter actions must create ten photos", 10, (photos() - beforeTen).size)
        checkpoint("ten-sequential-captures")

        selectFilm(FilmPreset.DUSK)
        val frameBeforeSettings = preview()?.renderedFrame
        compose.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
        compose.onNodeWithTag("settings_back").assertExists().performClick()
        waitForFrame(FilmPreset.DUSK, frameBeforeSettings)
        takePhoto("after-settings", FilmPreset.DUSK)
        checkpoint("settings-roundtrip")

        val frameBeforePause = preview()?.renderedFrame
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        waitForFrame(FilmPreset.DUSK, frameBeforePause)
        takePhoto("after-stop-resume", FilmPreset.DUSK)
        checkpoint("activity-stop-resume")
        assertEquals("All seventeen successful actions must remain saved", 17, (photos() - initialPhotos).size)
    }

    @After fun closeAndRecord() {
        if (::scenario.isInitialized) {
            device.takeScreenshot(File(TestImages.artifacts, "end-screen.png"))
            scenario.close()
        }
        File(TestImages.artifacts, "actual-scene-results.json").writeText(
            JSONObject().put("initialPhotos", initialPhotos.size).put("captures", captures)
                .put("checkpoints", checkpoints).put("fixedGrayAssertions", false).toString(2),
        )
        // Keep newly captured JPEGs. Never clear settings or delete existing photos.
    }

    private fun checkpoint(name: String) {
        checkpoints.put(JSONObject().put("name", name).put("timeMs", System.currentTimeMillis()))
        File(TestImages.artifacts, "actual-scene-results.json").writeText(
            JSONObject().put("initialPhotos", initialPhotos.size).put("captures", captures)
                .put("checkpoints", checkpoints).put("fixedGrayAssertions", false).toString(2),
        )
    }

    private fun waitForShutter() {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("shutter").fetchSemanticsNodes().singleOrNull()
                ?.config?.contains(SemanticsProperties.Disabled) == false
        }
    }

    private fun waitForFrame(film: FilmPreset, afterFrame: Any? = null) {
        waitForShutter()
        compose.waitUntil(20_000) {
            preview()?.renderedFrame?.let { it.film == film && it !== afterFrame } == true
        }
    }

    private fun selectFilm(film: FilmPreset) {
        val dial = compose.onNodeWithTag("film_dial")
        val expected = context.getString(film.labelRes)
        val previousFrame = preview()?.renderedFrame
        if (dial.fetchSemanticsNode().config[SemanticsProperties.StateDescription] != expected) dial.performClick()
        dial.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected))
        waitForFrame(film, previousFrame)
    }

    private fun takePhoto(label: String, film: FilmPreset, keyCode: Int? = null) {
        waitForFrame(film)
        savePreview(label)
        val before = photos()
        if (keyCode == null) compose.onNodeWithTag("shutter").assertIsEnabled().performClick()
        else dispatchVolumeShutter(keyCode)
        var added = emptySet<Uri>()
        compose.waitUntil(30_000) { added = photos() - before; added.isNotEmpty() }
        assertEquals("One shutter must create one JPEG", 1, added.size)
        val uri = added.single()
        waitForShutter()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("gallery_image", useUnmergedTree = true).fetchSemanticsNodes()
                .singleOrNull()?.config?.get(LatestPhotoUriKey) == uri.toString()
        }
        val file = File(TestImages.artifacts, "$label.jpg")
        context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
        try {
            assertTrue("Invalid JPEG dimensions", bitmap.width > 0 && bitmap.height > 0)
            context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.IS_PENDING, MediaStore.Images.Media.MIME_TYPE), null, null, null)!!.use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)); assertEquals("image/jpeg", it.getString(1))
            }
            captures.put(JSONObject().put("label", label).put("film", film.name).put("uri", uri.toString())
                .put("width", bitmap.width).put("height", bitmap.height).put("bytes", file.length())
                .put("exifOrientation", ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0)))
            checkpoint("saved-$label")
        } finally { bitmap.recycle() }
    }

    private fun dispatchVolumeShutter(keyCode: Int) {
        var downHandled = false
        var upHandled = false
        scenario.onActivity { activity ->
            downHandled = activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            upHandled = activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
        assertTrue("Activity must route the volume key down event to the camera shutter", downHandled)
        assertTrue("Activity must consume the volume key up event", upHandled)
    }

    private fun savePreview(label: String) {
        if (label != "warm-screen" && label != "dusk-screen") return
        val view = requireNotNull(preview())
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            val latch = CountDownLatch(1)
            var status = PixelCopy.ERROR_SOURCE_NO_DATA
            PixelCopy.request(view, bitmap, { status = it; latch.countDown() }, Handler(Looper.getMainLooper()))
            assertTrue("PixelCopy timed out", latch.await(5, TimeUnit.SECONDS))
            assertEquals("Preview must be readable", PixelCopy.SUCCESS, status)
            TestImages.save("$label-preview", bitmap)
            device.takeScreenshot(File(TestImages.artifacts, "$label-screen.png"))
        } finally { bitmap.recycle() }
    }

    private fun photos(): Set<Uri> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        return context.contentResolver.query(collection, arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.OWNER_PACKAGE_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ? AND ${MediaStore.Images.Media.IS_PENDING} = 0",
            arrayOf(context.packageName, "Pictures/FeelCamera/"), null)!!.use { cursor ->
            buildSet { while (cursor.moveToNext()) add(ContentUris.withAppendedId(collection, cursor.getLong(0))) }
        }
    }

    private fun preview(): CameraGlSurfaceView? {
        val result = AtomicReference<CameraGlSurfaceView?>()
        scenario.onActivity { result.set(findPreview(it.window.decorView)) }
        return result.get()
    }

    private fun findPreview(view: View): CameraGlSurfaceView? {
        if (view is CameraGlSurfaceView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findPreview(view.getChildAt(index))?.let { return it }
        return null
    }
}
