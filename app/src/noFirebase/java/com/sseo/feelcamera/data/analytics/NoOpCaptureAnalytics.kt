package com.sseo.feelcamera.data.analytics

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

class NoOpCaptureAnalytics @Inject constructor() : CaptureAnalytics {
    override fun photoSaved(event: PhotoSavedEvent) = Unit
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureAnalyticsModule {
    @Binds
    abstract fun bindCaptureAnalytics(implementation: NoOpCaptureAnalytics): CaptureAnalytics
}
