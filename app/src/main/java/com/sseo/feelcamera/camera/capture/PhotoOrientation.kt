package com.sseo.feelcamera.camera.capture

enum class PhotoOrientation {
    PORTRAIT,
    LANDSCAPE,
    SQUARE;

    companion object {
        fun fromDimensions(width: Int, height: Int): PhotoOrientation {
            require(width > 0 && height > 0) { "Photo dimensions must be positive." }

            return when {
                width < height -> PORTRAIT
                width > height -> LANDSCAPE
                else -> SQUARE
            }
        }
    }
}
