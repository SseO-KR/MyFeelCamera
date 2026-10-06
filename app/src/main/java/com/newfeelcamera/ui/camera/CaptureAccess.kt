package com.newfeelcamera.ui.camera

import com.newfeelcamera.data.quota.FREE_CAPTURE_LIMIT

internal fun canCapture(
    isUnlimitedActive: Boolean,
    isQuotaLoaded: Boolean,
    capturedPhotoCount: Int,
): Boolean = isUnlimitedActive ||
    (isQuotaLoaded && capturedPhotoCount < FREE_CAPTURE_LIMIT)
