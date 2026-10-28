package com.sseo.feelcamera.camera.capture

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import com.sseo.feelcamera.camera.filter.FilmPreset

data class CaptureInput(
    val bitmap: Bitmap,
    val cropRect: Rect,
    val rotationDegrees: Int,
    val film: FilmPreset,
)

fun ImageProxy.toCaptureInput(
    film: FilmPreset,
): CaptureInput {
    val cropRectSnapshot = Rect(cropRect)
    val rotationDegreesSnapshot = imageInfo.rotationDegrees

    return CaptureInput(
        bitmap = toBitmap(),
        cropRect = cropRectSnapshot,
        rotationDegrees = rotationDegreesSnapshot,
        film = film,
    )
}