package com.newfeelcamera.ui.camera

import android.net.Uri
import android.view.Surface
import com.newfeelcamera.camera.filter.FilmPreset
import com.newfeelcamera.data.settings.ShutterSoundLevel

data class CameraUiState(
    val isSettingsLoaded: Boolean = false,
    val hasCameraPermission: Boolean = false,
    val isQuotaLoaded: Boolean = false,
    val isUnlimitedActive: Boolean = false,
    val isCapturing: Boolean = false,
    val capturedPhotoCount: Int = 0,
    val latestPhotoUri: Uri? = null,
    val lastSavedPhotoUri: Uri? = null,
    val captureErrorMessage: String? = null,
    val deviceTargetRotation: Int = Surface.ROTATION_0,
    val selectedFilm: FilmPreset = FilmPreset.WARM,
    val showGrid: Boolean = true,
    val shutterSoundLevel: ShutterSoundLevel = ShutterSoundLevel.MEDIUM,
    val zoomRatio: Float = 1f,
    val minZoomRatio: Float = 1f,
    val maxZoomRatio: Float = 1f,
    val hasFlashUnit: Boolean = false,
    val isFlashEnabled: Boolean = false,
)
