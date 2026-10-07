package com.newfeelcamera.ui.camera

import android.util.Log
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.newfeelcamera.R
import com.newfeelcamera.camera.CameraController
import com.newfeelcamera.camera.ShutterSoundPlayer
import com.newfeelcamera.camera.capture.CaptureException
import com.newfeelcamera.camera.filter.FilmPreset
import com.newfeelcamera.data.billing.BillingRepository
import com.newfeelcamera.data.billing.UnlimitedAccessState
import com.newfeelcamera.data.gallery.RecentPhotoRepository
import com.newfeelcamera.data.quota.FreeCaptureQuotaRepository
import com.newfeelcamera.data.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val freeCaptureQuotaRepository: FreeCaptureQuotaRepository,
    private val recentPhotoRepository: RecentPhotoRepository,
    billingRepository: BillingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState = _uiState.asStateFlow()
    private var latestPhotoRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            billingRepository.unlimitedAccessState.collect { accessState ->
                _uiState.update {
                    it.copy(isUnlimitedActive = accessState == UnlimitedAccessState.Active)
                }
            }
        }

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
                    )
                }
            }
        }

        viewModelScope.launch {
            freeCaptureQuotaRepository.usedShots
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.e(TAG, "Unable to read free capture count", error)
                    _uiState.update {
                        it.copy(
                            isQuotaLoaded = false,
                            captureErrorMessageRes = R.string.free_capture_count_failed,
                        )
                    }
                }
                .collect { usedShots ->
                    _uiState.update {
                        it.copy(
                            isQuotaLoaded = true,
                            capturedPhotoCount = usedShots,
                        )
                    }
                }
        }
    }

    fun onIntent(intent: CameraAction){
        when (intent){
            is CameraAction.CameraPermissionChanged -> {
                _uiState.value = _uiState.value.copy(
                    hasCameraPermission = intent.granted
                )
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

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
    ) {
        cameraController.bind(
            lifecycleOwner = lifecycleOwner,
            surfaceProvider = surfaceProvider,
            onCameraStateChanged = ::onCameraStateChanged,
        )
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

        if (!stateAtShutter.isUnlimitedActive && !stateAtShutter.isQuotaLoaded) {
            _uiState.update {
                it.copy(captureErrorMessageRes = R.string.free_capture_count_failed)
            }
            return
        }

        if (!canCapture(
                isUnlimitedActive = stateAtShutter.isUnlimitedActive,
                isQuotaLoaded = stateAtShutter.isQuotaLoaded,
                capturedPhotoCount = stateAtShutter.capturedPhotoCount,
            )
        ) {
            _uiState.update {
                it.copy(captureErrorMessageRes = R.string.free_capture_limit_used)
            }
            return
        }

        if (!stateAtShutter.hasCameraPermission) {
            _uiState.update {
                it.copy(captureErrorMessageRes = R.string.camera_permission_required)
            }
            return
        }

        val filmAtShutter = stateAtShutter.selectedFilm
        val shutterSoundLevelAtShutter = stateAtShutter.shutterSoundLevel

        _uiState.update {
            it.copy(
                isCapturing = true,
                captureErrorMessageRes = null,
            )
        }

        viewModelScope.launch {
            try {
                val uri = cameraController.takePhoto(
                    film = filmAtShutter,
                    onShutter = { shutterSoundPlayer.play(shutterSoundLevelAtShutter) },
                )

                if (stateAtShutter.isUnlimitedActive) {
                    _uiState.update { it.copy(latestPhotoUri = uri, lastSavedPhotoUri = uri) }
                    return@launch
                }

                val usedShots = try {
                    freeCaptureQuotaRepository.recordSavedPhoto()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    Log.e(TAG, "Photo saved but free capture count was not recorded: uri=$uri", error)
                    _uiState.update {
                        it.copy(
                            isQuotaLoaded = false,
                            latestPhotoUri = uri,
                            lastSavedPhotoUri = uri,
                            captureErrorMessageRes = R.string.photo_saved_count_failed,
                        )
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        capturedPhotoCount = usedShots,
                        latestPhotoUri = uri,
                        lastSavedPhotoUri = uri,
                    )
                }
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

        _uiState.update {
            it.copy(captureErrorMessageRes = errorMessageRes)
        }
    }

    override fun onCleared() {
        cameraController.close()
        shutterSoundPlayer.close()
    }

    private companion object {
        const val TAG = "CameraViewModel"
    }
}
