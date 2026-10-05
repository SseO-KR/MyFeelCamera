package com.newfeelcamera.ui.camera

import android.util.Log
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.newfeelcamera.camera.CameraController
import com.newfeelcamera.camera.capture.CaptureException
import com.newfeelcamera.camera.filter.FilmPreset
import com.newfeelcamera.data.quota.FREE_CAPTURE_LIMIT
import com.newfeelcamera.data.quota.FreeCaptureQuotaRepository
import com.newfeelcamera.data.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    data object ShutterClicked : CameraAction
}

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val cameraController: CameraController,
    private val settingsRepository: AppSettingsRepository,
    private val freeCaptureQuotaRepository: FreeCaptureQuotaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState = _uiState.asStateFlow()

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
                            captureErrorMessage = "무료 촬영 횟수를 확인하지 못했습니다.",
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
        )
    }

    private fun capturePhoto() {
        val stateAtShutter = _uiState.value

        if (!stateAtShutter.isSettingsLoaded) return
        if (stateAtShutter.isCapturing) return

        if (!stateAtShutter.isQuotaLoaded) {
            _uiState.update {
                it.copy(captureErrorMessage = "무료 촬영 횟수를 확인하지 못했습니다.")
            }
            return
        }

        if (stateAtShutter.capturedPhotoCount >= FREE_CAPTURE_LIMIT) {
            _uiState.update {
                it.copy(captureErrorMessage = "무료 촬영 24장을 모두 사용했습니다.")
            }
            return
        }

        if (!stateAtShutter.hasCameraPermission) {
            _uiState.update {
                it.copy(captureErrorMessage = "카메라 권한이 필요합니다.")
            }
            return
        }

        val filmAtShutter = stateAtShutter.selectedFilm

        _uiState.update {
            it.copy(
                isCapturing = true,
                captureErrorMessage = null,
            )
        }

        viewModelScope.launch {
            try {
                val uri = cameraController.takePhoto(
                    film = filmAtShutter,
                )

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
                            captureErrorMessage = "사진은 저장되었지만 촬영 횟수 기록에 실패했습니다.",
                        )
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        capturedPhotoCount = usedShots,
                        latestPhotoUri = uri,
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

        val errorMessage = when (error) {
            is CaptureException -> error.userMessage
            else -> "사진 처리 중 알 수 없는 오류가 발생했습니다."
        }

        _uiState.update {
            it.copy(captureErrorMessage = errorMessage)
        }
    }

    override fun onCleared() {
        cameraController.close()
    }

    private companion object {
        const val TAG = "CameraViewModel"
    }
}
