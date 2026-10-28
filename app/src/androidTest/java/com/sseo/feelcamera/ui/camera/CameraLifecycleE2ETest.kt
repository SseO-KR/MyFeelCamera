package com.sseo.feelcamera.ui.camera

import android.Manifest
import android.view.Surface
import androidx.compose.ui.test.*
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.sseo.feelcamera.R
import com.sseo.feelcamera.camera.filter.FilmPreset
import com.sseo.feelcamera.testing.CameraE2ESupport
import com.sseo.feelcamera.testing.ExtendedCameraTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExtendedCameraTest
@RunWith(AndroidJUnit4::class)
class CameraLifecycleE2ETest : CameraE2ESupport() {
    @get:Rule val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test fun stoppedActivityResumesWithSelectedFilmAndCanCapture() {
        waitForCamera(FilmPreset.WARM)
        selectFilm(FilmPreset.DUSK)
        val sequence = requireNotNull(preview()?.renderedFrame).sequence
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        waitForCamera(FilmPreset.DUSK)
        waitForFilmFrame(FilmPreset.DUSK, sequence)
        takePhoto(FilmPreset.DUSK)
    }

    @Test fun returningFromSettingsKeepsFilmAndAppliesGridChange() {
        waitForCamera(FilmPreset.WARM)
        selectFilm(FilmPreset.DUSK)
        compose.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
        compose.onNodeWithTag("settings_grid").performScrollTo().assertIsOn().performClick()
        compose.waitUntil(5_000) {
            compose.onNodeWithTag("settings_back").fetchSemanticsNode().config
                .contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not()
        }
        compose.onNodeWithTag("settings_back").performClick()
        waitForCamera(FilmPreset.DUSK)
        compose.onNodeWithTag("camera_grid", useUnmergedTree = true).assertDoesNotExist()
        takePhoto(FilmPreset.DUSK)
    }

    @Test fun displayRotationKeepsPreviewRenderingAndCaptureAvailable() {
        waitForCamera(FilmPreset.WARM)
        selectFilm(FilmPreset.DUSK)
        try {
            device.setOrientationLeft()
            compose.waitUntil(10_000) { preview()?.display?.rotation == Surface.ROTATION_90 }
            val sequence = requireNotNull(preview()?.renderedFrame).sequence
            waitForFilmFrame(FilmPreset.DUSK, sequence)
            takePhoto(FilmPreset.DUSK)
            device.setOrientationNatural()
            compose.waitUntil(10_000) { preview()?.display?.rotation == Surface.ROTATION_0 }
            val next = requireNotNull(preview()?.renderedFrame).sequence
            waitForFilmFrame(FilmPreset.DUSK, next)
            takePhoto(FilmPreset.DUSK)
        } finally { device.unfreezeRotation() }
    }
}
