package com.sseo.feelcamera.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sseo.feelcamera.R
import com.sseo.feelcamera.camera.filter.FilmPreset
import com.sseo.feelcamera.data.settings.ShutterSoundLevel
import com.sseo.feelcamera.testing.FailureArtifactsRule
import com.sseo.feelcamera.testing.TestImages
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsUiTest {
    @get:Rule(order = 0) val compose = createComposeRule()
    @get:Rule(order = 1) val artifacts = FailureArtifactsRule()
    private var state by mutableStateOf(SettingsUiState(isLoading = false, mustPlayShutterSound = false))
    private var writes = 0
    private var backRequests = 0

    private fun show() {
        compose.setContent {
            SettingsScreen(uiState = state,
                onStartFilmSelected = { writes++; state = state.copy(startFilm = it) },
                onGridChanged = { writes++; state = state.copy(showGrid = it) },
                onShutterSoundLevelSelected = { writes++; state = state.copy(shutterSoundLevel = it) },
                onAnalyticsChanged = {}, onCrashReportingChanged = {}, onPrivacyPolicyClick = {},
                onBackClick = { backRequests++ })
        }
    }

    @Test fun filmGridAndSoundSelectionsUpdateIndependently() {
        show()
        compose.onNodeWithTag("start_film_WARM").assertIsSelected()
        compose.onNodeWithTag("start_film_DUSK").performClick().assertIsSelected()
        compose.onNodeWithTag("settings_grid").performScrollTo().assertIsOn().performClick().assertIsOff()
        compose.onNodeWithTag("shutter_sound_LOW").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("start_film_DUSK").performScrollTo().assertIsSelected()
        compose.runOnIdle {
            assertEquals(FilmPreset.DUSK, state.startFilm)
            assertEquals(ShutterSoundLevel.LOW, state.shutterSoundLevel)
            assertEquals(false, state.showGrid)
            assertEquals(3, writes)
        }
    }

    @Test fun loadingHidesEditableControlsAndSavingBlocksWritesAndBack() {
        state = state.copy(isLoading = true)
        show()
        compose.onNodeWithTag("start_film_DUSK").assertDoesNotExist()
        compose.onNodeWithText(TestImages.context.getString(R.string.settings_loading)).assertIsDisplayed()
        compose.runOnIdle { state = state.copy(isLoading = false, isSaving = true) }
        compose.onNodeWithTag("start_film_DUSK").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("settings_grid").performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithTag("shutter_sound_LOW").performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithTag("settings_back").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(0, writes); assertEquals(0, backRequests) }
        compose.runOnIdle { state = state.copy(isSaving = false) }
        compose.onNodeWithTag("settings_back").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, backRequests) }
    }

    @Test fun mandatoryShutterSoundAndSaveErrorAreExplained() {
        state = state.copy(mustPlayShutterSound = true, errorMessageRes = R.string.settings_save_failed)
        show()
        compose.onNodeWithTag("shutter_sound_OFF").assertDoesNotExist()
        compose.onNodeWithText(TestImages.context.getString(R.string.shutter_sound_required)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(TestImages.context.getString(R.string.settings_save_failed)).assertIsDisplayed()
    }
}
