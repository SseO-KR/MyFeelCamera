package com.sseo.feelcamera.data.analytics

import com.sseo.feelcamera.camera.capture.PhotoOrientation
import com.sseo.feelcamera.camera.filter.FilmPreset

data class PhotoSavedEvent(
    val film: FilmPreset,
    val orientation: PhotoOrientation,
    val flashEnabled: Boolean,
    val flashAvailable: Boolean,
)

interface CaptureAnalytics {
    fun photoSaved(event: PhotoSavedEvent)
}
