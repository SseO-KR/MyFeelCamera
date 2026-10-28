package com.sseo.feelcamera.ui.camera

import android.net.Uri
import android.view.Surface
import com.sseo.feelcamera.camera.filter.FilmPreset
import com.sseo.feelcamera.data.settings.ShutterSoundLevel

data class CameraUiState(
    val isSettingsLoaded: Boolean = false,
    val hasCameraPermission: Boolean = false,
    val hasSeenCameraPermissionIntro: Boolean = false,
    val hasRequestedCameraPermission: Boolean = false,
    val isCameraReady: Boolean = false,
    val isCameraInitializing: Boolean = false,
    val cameraInitializationFailed: Boolean = false,
    val isCapturing: Boolean = false,
    val latestPhotoUri: Uri? = null,
    val lastSavedPhotoUri: Uri? = null,
    val deviceTargetRotation: Int = Surface.ROTATION_0,
    val selectedFilm: FilmPreset = FilmPreset.WARM,
    val showGrid: Boolean = true,
    val shutterSoundLevel: ShutterSoundLevel = ShutterSoundLevel.MEDIUM,
    val zoomRatio: Float = 1f,
    val minZoomRatio: Float = 1f,
    val maxZoomRatio: Float = 1f,
    val hasFlashUnit: Boolean = false,
    val isFlashEnabled: Boolean = false,
) {
    val canCapture: Boolean
        get() = isSettingsLoaded && hasCameraPermission && isCameraReady && !isCapturing
}
