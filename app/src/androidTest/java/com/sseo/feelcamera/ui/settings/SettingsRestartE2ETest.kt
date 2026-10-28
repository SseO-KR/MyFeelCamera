package com.sseo.feelcamera.ui.settings

import android.Manifest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.sseo.feelcamera.R
import com.sseo.feelcamera.camera.filter.FilmPreset
import com.sseo.feelcamera.testing.CameraE2ESupport
import com.sseo.feelcamera.testing.ProcessRestartTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 두 메서드를 별도 instrumentation 프로세스에서 순서대로 실행한다. CI 스크립트 참고. */
@ProcessRestartTest
@RunWith(AndroidJUnit4::class)
class SettingsRestartE2ETest : CameraE2ESupport() {
    @get:Rule val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    override val resetSettings = false

    @Test fun writeSettingsBeforeProcessRestart() {
        resetAppSettings()
        waitForCamera(FilmPreset.WARM)
        compose.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
        compose.onNodeWithTag("start_film_DUSK").performClick()
        waitForSettingsSave()
        compose.onNodeWithTag("settings_grid").performScrollTo().performClick().assertIsOff()
        waitForSettingsSave()
        compose.onNodeWithTag("shutter_sound_LOW").performScrollTo().performClick().assertIsSelected()
        waitForSettingsSave()
        compose.onNodeWithTag("settings_back").performClick()
        waitForCamera(FilmPreset.DUSK)
    }

    @Test fun readSettingsAfterProcessRestart() {
        waitForCamera(FilmPreset.DUSK)
        compose.onNodeWithTag("camera_grid", useUnmergedTree = true).assertDoesNotExist()
        takePhoto(FilmPreset.DUSK)
        compose.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
        compose.onNodeWithTag("start_film_DUSK").assertIsSelected()
        compose.onNodeWithTag("settings_grid").performScrollTo().assertIsOff()
        compose.onNodeWithTag("shutter_sound_LOW").performScrollTo().assertIsSelected()
    }

    private fun waitForSettingsSave() {
        compose.waitUntil(5_000) {
            !compose.onNodeWithTag("settings_back").fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
        }
    }
}
