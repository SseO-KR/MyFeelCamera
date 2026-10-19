package com.newfeelcamera.ui.camera

enum class CameraPermissionRecovery {
    RequestPermission,
    OpenAppSettings,
}

internal fun cameraPermissionRecovery(
    hasRequestedPermission: Boolean,
    shouldShowRationale: Boolean,
): CameraPermissionRecovery =
    if (hasRequestedPermission && !shouldShowRationale) {
        CameraPermissionRecovery.OpenAppSettings
    } else {
        CameraPermissionRecovery.RequestPermission
    }
