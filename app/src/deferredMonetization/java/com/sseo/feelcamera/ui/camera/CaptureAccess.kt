package com.sseo.feelcamera.ui.camera

import com.sseo.feelcamera.data.quota.FREE_CAPTURE_LIMIT

internal fun canCapture(
    isUnlimitedActive: Boolean,
    isQuotaLoaded: Boolean,
    capturedPhotoCount: Int,
): Boolean = isUnlimitedActive ||
    (isQuotaLoaded && capturedPhotoCount < FREE_CAPTURE_LIMIT)
