package com.sseo.feelcamera.camera.filter

enum class FilmPreset(
    val lutAssetPath: String,
) {
    WARM(
        lutAssetPath = "lut/film_warm.bin",
    ),
    DUSK(
        lutAssetPath = "lut/film_dusk.bin",
    ),
}
