package com.sseo.feelcamera.ui

import androidx.annotation.StringRes
import com.sseo.feelcamera.R
import com.sseo.feelcamera.camera.filter.FilmPreset
import com.sseo.feelcamera.data.settings.ShutterSoundLevel

@get:StringRes
val FilmPreset.labelRes: Int
    get() = when (this) {
        FilmPreset.WARM -> R.string.film_warm
        FilmPreset.DUSK -> R.string.film_dusk
    }

@get:StringRes
val ShutterSoundLevel.labelRes: Int
    get() = when (this) {
        ShutterSoundLevel.OFF -> R.string.sound_off
        ShutterSoundLevel.LOW -> R.string.sound_low
        ShutterSoundLevel.MEDIUM -> R.string.sound_medium
        ShutterSoundLevel.HIGH -> R.string.sound_high
    }
