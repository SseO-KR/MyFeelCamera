package com.newfeelcamera.ui.camera

import android.net.Uri

data class CameraUiState(
    val hasCameraPermission: Boolean = false,
    val isCapturing: Boolean = false,
    val capturedPhotoCount: Int = 0,
    val latestPhotoUri: Uri? = null,
    val captureErrorMessage: String? = null,
)