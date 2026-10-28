package com.sseo.feelcamera.ui.camera

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sseo.feelcamera.R
import com.sseo.feelcamera.camera.filter.FilmPreset
import com.sseo.feelcamera.testing.FailureArtifactsRule
import com.sseo.feelcamera.testing.TestImages
import com.sseo.feelcamera.ui.labelRes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraScreenUiTest {
    @get:Rule(order = 0) val compose = createComposeRule()
    @get:Rule(order = 1) val artifacts = FailureArtifactsRule()
    private var state by mutableStateOf(CameraUiState(isSettingsLoaded = true))
    private var shutterRequests = 0
    private var recoveryRequests = 0
    @Volatile private var bindRequests = 0
    private var flashRequests = 0
    private var zoomInRequests = 0
    private var zoomOutRequests = 0

    private fun show(permissionRecovery: CameraPermissionRecovery = CameraPermissionRecovery.RequestPermission,
        requestInFlight: Boolean = false) {
        compose.setContent {
            CameraScreen(uiState = state,
                onBindCamera = { _, _ -> bindRequests++ },
                permissionRecovery = permissionRecovery,
                permissionRequestInFlight = requestInFlight,
                onRecoverCameraPermission = { recoveryRequests++ },
                onDisplayRotationChanged = {},
                onFilmSelected = { state = state.copy(selectedFilm = it) },
                onZoomInClick = { zoomInRequests++ }, onZoomOutClick = { zoomOutRequests++ },
                onFlashClick = { flashRequests++; state = state.copy(isFlashEnabled = !state.isFlashEnabled) },
                onGalleryClick = {},
                onShutterClick = { shutterRequests++; state = state.copy(isCapturing = true) },
                onSettingsClick = {})
        }
    }

    private fun assertFilm(film: FilmPreset) {
        compose.onNodeWithTag("film_dial").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, TestImages.context.getString(film.labelRes)))
    }

    @Test fun configuredFilmAndRoundTripSelectionAreDisplayed() {
        state = state.copy(selectedFilm = FilmPreset.DUSK)
        show()
        assertFilm(FilmPreset.DUSK)
        compose.onNodeWithTag("film_dial").performClick()
        assertFilm(FilmPreset.WARM)
        compose.onNodeWithTag("film_dial").performClick()
        assertFilm(FilmPreset.DUSK)
    }

    @Test fun shutterFollowsLoadingPermissionCameraAndCaptureState() {
        state = state.copy(isSettingsLoaded = false)
        show()
        compose.onNodeWithTag("shutter").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(isSettingsLoaded = true) }
        compose.onNodeWithTag("shutter").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(hasCameraPermission = true, isCameraInitializing = true) }
        compose.onNodeWithTag("shutter").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(isCameraReady = true, isCameraInitializing = false) }
        compose.onNodeWithTag("shutter").assertIsEnabled().performClick()
        compose.onNodeWithTag("shutter").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(1, shutterRequests); state = state.copy(isCapturing = false) }
        compose.onNodeWithTag("shutter").assertIsEnabled()
        compose.runOnIdle { state = state.copy(hasCameraPermission = false) }
        compose.onNodeWithTag("shutter").assertIsNotEnabled()
    }

    @Test fun gridVisibilityFollowsSettingsWithoutResettingFilm() {
        state = state.copy(selectedFilm = FilmPreset.DUSK)
        show()
        compose.onNodeWithTag("camera_grid", useUnmergedTree = true).assertExists()
        compose.runOnIdle { state = state.copy(showGrid = false) }
        compose.onNodeWithTag("camera_grid", useUnmergedTree = true).assertDoesNotExist()
        assertFilm(FilmPreset.DUSK)
        compose.runOnIdle { state = state.copy(showGrid = true) }
        compose.onNodeWithTag("camera_grid", useUnmergedTree = true).assertExists()
    }

    @Test fun zoomBoundsAndFlashAvailabilityBlockInvalidActions() {
        state = state.copy(minZoomRatio = 1f, maxZoomRatio = 2f)
        show()
        val zoomIn = TestImages.context.getString(R.string.zoom_in)
        val zoomOut = TestImages.context.getString(R.string.zoom_out)
        compose.onNodeWithContentDescription(zoomOut).assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription(zoomIn).assertIsEnabled().performClick()
        compose.onNodeWithTag("flash").assertIsNotEnabled().performClick()
        compose.runOnIdle { state = state.copy(zoomRatio = 2f, hasFlashUnit = true) }
        compose.onNodeWithContentDescription(zoomIn).assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription(zoomOut).assertIsEnabled().performClick()
        compose.onNodeWithTag("flash").assertIsEnabled().performClick().assertIsOn()
        compose.runOnIdle { state = state.copy(isCapturing = true) }
        compose.onNodeWithTag("flash").assertIsNotEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, zoomInRequests); assertEquals(1, zoomOutRequests); assertEquals(1, flashRequests)
        }
    }

    @Test fun permissionRecoveryShowsSettingsActionAndInvokesCallback() {
        show(CameraPermissionRecovery.OpenAppSettings)
        compose.onNodeWithText(TestImages.context.getString(R.string.camera_permission_settings_required)).assertIsDisplayed()
        compose.onNodeWithTag("camera_recovery").performClick()
        compose.runOnIdle { assertEquals(1, recoveryRequests) }
    }

    @Test fun permissionRequestInFlightDisablesRecovery() {
        show(requestInFlight = true)
        compose.onNodeWithTag("camera_recovery").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(0, recoveryRequests) }
    }

    @Test fun initializationFailureOffersRetryAndRebindsPreview() {
        state = state.copy(hasCameraPermission = true, cameraInitializationFailed = true)
        show()
        compose.waitUntil(5_000) { bindRequests > 0 }
        val before = bindRequests
        compose.onNodeWithText(TestImages.context.getString(R.string.camera_initialization_failed)).assertIsDisplayed()
        compose.onNodeWithTag("camera_recovery").performClick()
        compose.waitUntil(5_000) { bindRequests > before }
    }
}
