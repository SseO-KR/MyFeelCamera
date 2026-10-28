package com.sseo.feelcamera.application

import android.app.Application
import android.util.Log
import com.sseo.feelcamera.data.analytics.TelemetryCollectionController
import com.sseo.feelcamera.data.settings.AppSettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FeelCameraApp : Application() {
    @Inject lateinit var settingsRepository: AppSettingsRepository
    @Inject lateinit var telemetryController: TelemetryCollectionController

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            settingsRepository.settings.map { it.telemetryConsent }.distinctUntilChanged().collect { consent ->
                try {
                    telemetryController.applyConsent(consent)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    Log.e("FeelCameraApp", "Unable to apply monitoring preferences", error)
                }
            }
        }
    }
}
