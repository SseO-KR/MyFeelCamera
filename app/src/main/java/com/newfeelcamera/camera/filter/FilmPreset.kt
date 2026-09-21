package com.newfeelcamera.camera.filter

enum class FilmPreset(
    val displayName: String,
    val lutAssetPath: String,
) {
    WARM(
        displayName = "WARM",
        lutAssetPath = "lut/film_warm.bin",
    ),
    DUSK(
        displayName = "DUSK",
        lutAssetPath = "lut/film_dusk.bin",
    ),
}