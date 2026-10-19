package com.newfeelcamera.ui.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraUiStateTest {
    private val readyState = CameraUiState(
        isSettingsLoaded = true,
        hasCameraPermission = true,
        isCameraReady = true,
    )

    @Test
    fun `capture requires both settings and a successfully bound camera`() {
        assertFalse(CameraUiState().canCapture)
        assertFalse(readyState.copy(isSettingsLoaded = false).canCapture)
        assertFalse(readyState.copy(isCameraReady = false).canCapture)
        assertTrue(readyState.canCapture)
    }

    @Test
    fun `revoked permission and an ongoing capture block the shutter`() {
        assertFalse(readyState.copy(hasCameraPermission = false).canCapture)
        assertFalse(readyState.copy(isCapturing = true).canCapture)
    }
}
