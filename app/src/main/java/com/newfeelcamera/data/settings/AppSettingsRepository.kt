package com.newfeelcamera.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.newfeelcamera.camera.filter.FilmPreset
import com.newfeelcamera.data.analytics.TelemetryConsent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings")

data class AppSettings(
    val startFilm: FilmPreset = FilmPreset.WARM,
    val showGrid: Boolean = true,
    val shutterSoundLevel: ShutterSoundLevel = ShutterSoundLevel.MEDIUM,
    val hasSeenCameraPermissionIntro: Boolean = false,
    val hasRequestedCameraPermission: Boolean = false,
    val telemetryConsent: TelemetryConsent = TelemetryConsent(),
)

@Singleton
class AppSettingsRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.appSettingsDataStore)

    private val startFilmKey = stringPreferencesKey("start_film")
    private val showGridKey = booleanPreferencesKey("show_grid")
    private val shutterSoundLevelKey = stringPreferencesKey("shutter_sound_level")
    private val cameraPermissionIntroSeenKey = booleanPreferencesKey("camera_permission_intro_seen")
    private val cameraPermissionRequestedKey = booleanPreferencesKey("camera_permission_requested")
    private val telemetryChoiceMadeKey = booleanPreferencesKey("telemetry_choice_made")
    private val analyticsEnabledKey = booleanPreferencesKey("analytics_enabled")
    private val crashReportingEnabledKey = booleanPreferencesKey("crash_reporting_enabled")

    val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            AppSettings(
                startFilm = FilmPreset.entries.firstOrNull {
                    it.name == preferences[startFilmKey]
                } ?: FilmPreset.WARM,
                showGrid = preferences[showGridKey] ?: true,
                shutterSoundLevel = ShutterSoundLevel.entries.firstOrNull {
                    it.name == preferences[shutterSoundLevelKey]
                } ?: ShutterSoundLevel.MEDIUM,
                hasSeenCameraPermissionIntro = preferences[cameraPermissionIntroSeenKey] ?: false,
                hasRequestedCameraPermission = preferences[cameraPermissionRequestedKey] ?: false,
                telemetryConsent = TelemetryConsent(
                    hasMadeChoice = preferences[telemetryChoiceMadeKey] ?: false,
                    analyticsEnabled = preferences[analyticsEnabledKey] ?: false,
                    crashReportingEnabled = preferences[crashReportingEnabledKey] ?: false,
                ),
            )
        }

    suspend fun setStartFilm(film: FilmPreset) {
        dataStore.edit { preferences ->
            preferences[startFilmKey] = film.name
        }
    }

    suspend fun setShowGrid(showGrid: Boolean) {
        dataStore.edit { preferences ->
            preferences[showGridKey] = showGrid
        }
    }

    suspend fun setShutterSoundLevel(level: ShutterSoundLevel) {
        dataStore.edit { preferences ->
            preferences[shutterSoundLevelKey] = level.name
        }
    }

    suspend fun saveCameraPermissionState(hasRequestedPermission: Boolean) {
        dataStore.edit { preferences ->
            preferences[cameraPermissionIntroSeenKey] = true
            preferences[cameraPermissionRequestedKey] = hasRequestedPermission
        }
    }

    suspend fun setTelemetryConsent(analyticsEnabled: Boolean, crashReportingEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[telemetryChoiceMadeKey] = true
            preferences[this.analyticsEnabledKey] = analyticsEnabled
            preferences[this.crashReportingEnabledKey] = crashReportingEnabled
        }
    }
}
