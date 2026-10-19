package com.newfeelcamera.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraPermissionRecoveryTest {
    @Test
    fun `first request uses the permission dialog even without a rationale`() {
        assertEquals(
            CameraPermissionRecovery.RequestPermission,
            cameraPermissionRecovery(hasRequestedPermission = false, shouldShowRationale = false),
        )
    }

    @Test
    fun `a denial that allows another request uses the permission dialog`() {
        assertEquals(
            CameraPermissionRecovery.RequestPermission,
            cameraPermissionRecovery(hasRequestedPermission = true, shouldShowRationale = true),
        )
    }

    @Test
    fun `a denial that suppresses the permission dialog opens app settings`() {
        assertEquals(
            CameraPermissionRecovery.OpenAppSettings,
            cameraPermissionRecovery(hasRequestedPermission = true, shouldShowRationale = false),
        )
    }
}
