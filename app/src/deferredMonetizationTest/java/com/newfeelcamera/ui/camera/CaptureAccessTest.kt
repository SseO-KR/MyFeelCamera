package com.newfeelcamera.ui.camera

import com.newfeelcamera.data.quota.FREE_CAPTURE_LIMIT
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureAccessTest {
    @Test
    fun unlimitedPurchaseAllowsCaptureAfterFreeLimitIsExhausted() {
        assertTrue(canCapture(true, true, FREE_CAPTURE_LIMIT))
        assertTrue(canCapture(true, false, FREE_CAPTURE_LIMIT))
    }

    @Test
    fun freeCaptureRequiresLoadedQuotaBelowLimit() {
        assertTrue(canCapture(false, true, FREE_CAPTURE_LIMIT - 1))
        assertFalse(canCapture(false, true, FREE_CAPTURE_LIMIT))
        assertFalse(canCapture(false, false, 0))
    }
}
