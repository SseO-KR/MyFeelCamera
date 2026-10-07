package com.newfeelcamera.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.newfeelcamera.camera.filter.FilmPreset
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
)

@Singleton
class AppSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val startFilmKey = stringPreferencesKey("start_film")
    private val showGridKey = booleanPreferencesKey("show_grid")
    private val shutterSoundLevelKey = stringPreferencesKey("shutter_sound_level")

    val settings: Flow<AppSettings> = context.appSettingsDataStore.data
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
            )
        }

    suspend fun setStartFilm(film: FilmPreset) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[startFilmKey] = film.name
        }
    }

    suspend fun setShowGrid(showGrid: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[showGridKey] = showGrid
        }
    }

    suspend fun setShutterSoundLevel(level: ShutterSoundLevel) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[shutterSoundLevelKey] = level.name
        }
    }
}
