package com.sseo.feelcamera.camera.capture

import android.net.Uri

data class SavedPhoto(
    val uri: Uri,
    val orientation: PhotoOrientation,
)
