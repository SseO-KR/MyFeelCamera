package com.newfeelcamera.data.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.newfeelcamera.camera.capture.PhotoOrientation
import com.newfeelcamera.camera.filter.FilmPreset
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

class FirebaseCaptureAnalytics @Inject constructor(
    @ApplicationContext context: Context,
) : CaptureAnalytics {
    private val analytics by lazy { FirebaseAnalytics.getInstance(context) }

    override fun photoSaved(event: PhotoSavedEvent) {
        val parameters = Bundle().apply {
            putString("film", when (event.film) {
                FilmPreset.WARM -> "warm"
                FilmPreset.DUSK -> "dusk"
            })
            putString("orientation", when (event.orientation) {
                PhotoOrientation.PORTRAIT -> "portrait"
                PhotoOrientation.LANDSCAPE -> "landscape"
                PhotoOrientation.SQUARE -> "square"
            })
            putString("flash_enabled", if (event.flashEnabled) "on" else "off")
            putString("flash_available", if (event.flashAvailable) "yes" else "no")
        }
        analytics.logEvent("photo_saved", parameters)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureAnalyticsModule {
    @Binds
    abstract fun bindCaptureAnalytics(implementation: FirebaseCaptureAnalytics): CaptureAnalytics
}
