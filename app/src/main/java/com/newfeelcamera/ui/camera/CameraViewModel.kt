package com.newfeelcamera.ui.camera

import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.newfeelcamera.camera.CameraController
import com.newfeelcamera.camera.filter.FilmPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState = _uiState.asStateFlow()

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
        if (_uiState.value.isCapturing) return

        if (!_uiState.value.hasCameraPermission) {
            _uiState.update {
                it.copy(captureErrorMessage = "카메라 권한이 필요합니다.")
            }
            return
        }

        _uiState.update {
            it.copy(
                isCapturing = true,
                captureErrorMessage = null,
            )
        }

        cameraController.takePhoto(
            onPhotoSaved = { uri ->
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        capturedPhotoCount = it.capturedPhotoCount + 1,
                        latestPhotoUri = uri,
                    )
                }
            },
            onError = { error ->
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        captureErrorMessage = error.message ?: "사진 저장에 실패했습니다.",
                    )
                }
            },
        )
    }
}
