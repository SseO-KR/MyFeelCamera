package com.newfeelcamera.data.analytics

import com.newfeelcamera.camera.capture.PhotoOrientation
import com.newfeelcamera.camera.filter.FilmPreset

data class PhotoSavedEvent(
    val film: FilmPreset,
    val orientation: PhotoOrientation,
    val flashEnabled: Boolean,
    val flashAvailable: Boolean,
)

interface CaptureAnalytics {
    fun photoSaved(event: PhotoSavedEvent)
}
