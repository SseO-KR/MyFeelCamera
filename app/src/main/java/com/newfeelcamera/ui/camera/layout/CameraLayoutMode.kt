package com.newfeelcamera.ui.camera.layout

import androidx.compose.ui.unit.Dp

sealed interface CameraLayoutMode {
    data object Portrait : CameraLayoutMode
    data object Landscape : CameraLayoutMode
}

fun cameraLayoutModeFor(
    width: Dp,
    height: Dp,
): CameraLayoutMode = if (width > height) {
    CameraLayoutMode.Landscape
} else {
    CameraLayoutMode.Portrait
}
