package com.sseo.feelcamera.data.analytics

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

class NoOpTelemetryCollectionController @Inject constructor() : TelemetryCollectionController {
    override val canRecordAnalyticsEvents: Boolean = false
    override fun applyConsent(consent: TelemetryConsent) = Unit
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TelemetryCollectionModule {
    @Binds
    abstract fun bindController(implementation: NoOpTelemetryCollectionController): TelemetryCollectionController
}
