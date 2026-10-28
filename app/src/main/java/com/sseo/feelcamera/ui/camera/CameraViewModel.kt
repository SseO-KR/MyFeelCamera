package com.sseo.feelcamera.ui.camera

import android.util.Log
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sseo.feelcamera.R
import com.sseo.feelcamera.camera.CameraController
import com.sseo.feelcamera.camera.ShutterSoundPlayer
import com.sseo.feelcamera.camera.capture.CaptureException
import com.sseo.feelcamera.camera.filter.FilmPreset
import com.sseo.feelcamera.data.analytics.CaptureAnalytics
import com.sseo.feelcamera.data.analytics.PhotoSavedEvent
import com.sseo.feelcamera.data.gallery.RecentPhotoRepository
import com.sseo.feelcamera.data.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

sealed interface CameraAction {
    data class CameraPermissionChanged(
        val granted: Boolean,
    ) : CameraAction
    data class DisplayRotationChanged(  // 현재 화면이 어느 방향으로 표시되는가
        val rotation: Int,
    ) : CameraAction
    data class DeviceOrientationChanged( // 물리 방향을 CameraX target rotation으로 양자화한 값
        val targetRotation: Int,
    ) : CameraAction
    data class FilmSelected(
        val film: FilmPreset,
    ) : CameraAction
    data object ZoomInClicked : CameraAction
    data object ZoomOutClicked : CameraAction
    data object FlashClicked : CameraAction
    data object ShutterClicked : CameraAction
}

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val cameraController: CameraController,
    private val shutterSoundPlayer: ShutterSoundPlayer,
    private val settingsRepository: AppSettingsRepository,
    private val recentPhotoRepository: RecentPhotoRepository,
    private val captureAnalytics: CaptureAnalytics,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState = _uiState.asStateFlow()
    private val notices = CameraNoticeStore()
    val pendingNotice = notices.pending
    private var latestPhotoRefreshJob: Job? = null

    fun noticeShown(id: Long) {
        notices.acknowledge(id)
    }

    init {
        viewModelScope.launch {
            var lastStartFilm: FilmPreset? = null
            settingsRepository.settings.collect { settings ->
                val startFilmChanged = lastStartFilm != settings.startFilm
                lastStartFilm = settings.startFilm

                _uiState.update { current ->
                    current.copy(
                        isSettingsLoaded = true,
                        selectedFilm = if (startFilmChanged) settings.startFilm else current.selectedFilm,
                        showGrid = settings.showGrid,
                        shutterSoundLevel = settings.shutterSoundLevel,
                        hasSeenCameraPermissionIntro = settings.hasSeenCameraPermissionIntro,
                        hasRequestedCameraPermission = settings.hasRequestedCameraPermission,
                    )
                }
            }
        }

    }

    fun saveCameraPermissionState(hasRequestedPermission: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.saveCameraPermissionState(hasRequestedPermission)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: IOException) {
                Log.w(TAG, "Unable to save camera permission state", error)
                notices.post(R.string.settings_save_failed)
            }
        }
    }

    fun onIntent(intent: CameraAction){
        when (intent){
            is CameraAction.CameraPermissionChanged -> {
                if (!intent.granted) cameraController.unbind()
                _uiState.update {
                    it.copy(
                        hasCameraPermission = intent.granted,
                        isCameraReady = it.isCameraReady && intent.granted,
                        cameraInitializationFailed = it.cameraInitializationFailed && intent.granted,
                    )
                }
            }
            is CameraAction.DisplayRotationChanged -> {
                cameraController.updatePreviewTargetRotation(
                    intent.rotation,
                )
            }
            is CameraAction.DeviceOrientationChanged -> {
                _uiState.update {
                    it.copy(deviceTargetRotation = intent.targetRotation)
                }
                cameraController.updateImageCaptureTargetRotation(
                    intent.targetRotation,
                )
            }
            is CameraAction.FilmSelected -> {
                _uiState.update { currentState ->
                    currentState.copy(
                        selectedFilm = intent.film,
                    )
                }
            }
            CameraAction.ZoomInClicked -> cameraController.changeZoomBy(0.5f)
            CameraAction.ZoomOutClicked -> cameraController.changeZoomBy(-0.5f)
            CameraAction.FlashClicked -> {
                val current = _uiState.value
                if (current.hasFlashUnit && !current.isCapturing) {
                    val enabled = !current.isFlashEnabled
                    if (cameraController.setFlashEnabled(enabled)) {
                        _uiState.update { it.copy(isFlashEnabled = enabled) }
                    }
                }
            }
            CameraAction.ShutterClicked -> capturePhoto()
        }
    }

    suspend fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
    ) {
        _uiState.update {
            it.copy(isCameraReady = false, isCameraInitializing = true, cameraInitializationFailed = false)
        }
        try {
            cameraController.bind(
                lifecycleOwner = lifecycleOwner,
                surfaceProvider = surfaceProvider,
                onCameraStateChanged = ::onCameraStateChanged,
            )
            _uiState.update { it.copy(isCameraReady = true) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.e(TAG, "Unable to initialize camera", error)
            cameraController.unbind()
            _uiState.update { it.copy(isCameraReady = false, cameraInitializationFailed = true) }
        } finally {
            _uiState.update { it.copy(isCameraInitializing = false) }
        }
    }

    private fun onCameraStateChanged(zoomState: ZoomState, hasFlashUnit: Boolean) {
        _uiState.update { current ->
            current.copy(
                zoomRatio = zoomState.zoomRatio,
                minZoomRatio = zoomState.minZoomRatio,
                maxZoomRatio = zoomState.maxZoomRatio,
                hasFlashUnit = hasFlashUnit,
                isFlashEnabled = current.isFlashEnabled && hasFlashUnit,
            )
        }
    }

    fun refreshLatestPhoto() {
        latestPhotoRefreshJob?.cancel()
        latestPhotoRefreshJob = viewModelScope.launch {
            val savedAtStart = _uiState.value.lastSavedPhotoUri
            try {
                val uri = recentPhotoRepository.latestPhotoUri()
                _uiState.update { current ->
                    if (current.lastSavedPhotoUri == savedAtStart) {
                        current.copy(latestPhotoUri = uri)
                    } else {
                        current
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.w(TAG, "Unable to load latest photo", error)
            }
        }
    }

    private fun capturePhoto() {
        val stateAtShutter = _uiState.value

        if (!stateAtShutter.isSettingsLoaded) return
        if (stateAtShutter.isCapturing) return

        if (!stateAtShutter.hasCameraPermission) {
            notices.post(R.string.camera_permission_required)
            return
        }

        if (!stateAtShutter.isCameraReady) {
            notices.post(R.string.camera_not_ready)
            return
        }

        val filmAtShutter = stateAtShutter.selectedFilm
        val flashEnabledAtShutter = stateAtShutter.hasFlashUnit && stateAtShutter.isFlashEnabled
        val flashAvailableAtShutter = stateAtShutter.hasFlashUnit
        val shutterSoundLevelAtShutter = stateAtShutter.shutterSoundLevel

        _uiState.update { it.copy(isCapturing = true) }

        viewModelScope.launch {
            try {
                val savedPhoto = cameraController.takePhoto(
                    film = filmAtShutter,
                    onShutter = { shutterSoundPlayer.play(shutterSoundLevelAtShutter) },
                )
                val uri = savedPhoto.uri

                try {
                    captureAnalytics.photoSaved(
                        PhotoSavedEvent(
                            film = filmAtShutter,
                            orientation = savedPhoto.orientation,
                            flashEnabled = flashEnabledAtShutter,
                            flashAvailable = flashAvailableAtShutter,
                        )
                    )
                } catch (error: Exception) {
                    Log.w(TAG, "Photo saved but analytics event failed", error)
                }

                _uiState.update {
                    it.copy(
                        latestPhotoUri = uri,
                        lastSavedPhotoUri = uri,
                    )
                }
                notices.post(R.string.photo_saved)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                handleCaptureFailure(error)
            } finally {
                _uiState.update {
                    it.copy(isCapturing = false)
                }
            }
        }
    }

    private fun handleCaptureFailure(error: Exception) {
        Log.e(TAG, "Capture failed", error)

        val errorMessageRes = when (error) {
            is CaptureException -> error.userMessageRes
            else -> R.string.camera_unknown_error
        }

        notices.post(errorMessageRes)
    }

    override fun onCleared() {
        cameraController.close()
        shutterSoundPlayer.close()
    }

    private companion object {
        const val TAG = "CameraViewModel"
    }
}
