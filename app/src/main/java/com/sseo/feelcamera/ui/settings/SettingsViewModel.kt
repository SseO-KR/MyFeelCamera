package com.sseo.feelcamera.ui.settings

import android.util.Log
import androidx.annotation.StringRes
import androidx.camera.core.CameraInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sseo.feelcamera.R
import com.sseo.feelcamera.camera.filter.FilmPreset
import com.sseo.feelcamera.data.settings.AppSettingsRepository
import com.sseo.feelcamera.data.settings.ShutterSoundLevel
import com.sseo.feelcamera.data.analytics.TelemetryConsent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isLoading: Boolean = true,
    val isStartFilmSaving: Boolean = false,
    val isGridSaving: Boolean = false,
    val isShutterSoundSaving: Boolean = false,
    val isTelemetrySaving: Boolean = false,
    val startFilm: FilmPreset = FilmPreset.WARM,
    val showGrid: Boolean = true,
    val shutterSoundLevel: ShutterSoundLevel = ShutterSoundLevel.MEDIUM,
    val usesSystemShutterSound: Boolean = CameraInfo.mustPlayShutterSound(),
    val telemetryConsent: TelemetryConsent = TelemetryConsent(),
    @field:StringRes val errorMessageRes: Int? = null,
) {
    val isSaving: Boolean
        get() = isStartFilmSaving || isGridSaving || isShutterSoundSaving || isTelemetrySaving

    val isShutterSoundMuted: Boolean
        get() = shutterSoundLevel == ShutterSoundLevel.OFF
}

@HiltViewModel
class SettingsViewModel internal constructor(
    private val settingsRepository: AppSettingsRepository,
    usesSystemShutterSound: Boolean,
) : ViewModel() {
    @Inject
    constructor(settingsRepository: AppSettingsRepository) : this(
        settingsRepository,
        CameraInfo.mustPlayShutterSound(),
    )

    private val _uiState = MutableStateFlow(SettingsUiState(usesSystemShutterSound = usesSystemShutterSound))
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        startFilm = settings.startFilm,
                        showGrid = settings.showGrid,
                        shutterSoundLevel = settings.shutterSoundLevel,
                        telemetryConsent = settings.telemetryConsent,
                    )
                }
            }
        }
    }

    fun setStartFilm(film: FilmPreset) {
        val current = _uiState.value
        if (current.isLoading || current.isStartFilmSaving || current.startFilm == film) return

        _uiState.update { it.copy(isStartFilmSaving = true, errorMessageRes = null) }

        viewModelScope.launch {
            try {
                settingsRepository.setStartFilm(film)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                showWriteError(error)
            } finally {
                _uiState.update { it.copy(isStartFilmSaving = false) }
            }
        }
    }

    fun setShowGrid(showGrid: Boolean) {
        val current = _uiState.value
        if (current.isLoading || current.isGridSaving || current.showGrid == showGrid) return
        _uiState.update { it.copy(isGridSaving = true, errorMessageRes = null) }
        viewModelScope.launch {
            try {
                settingsRepository.setShowGrid(showGrid)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                showWriteError(error)
            } finally {
                _uiState.update { it.copy(isGridSaving = false) }
            }
        }
    }

    fun setShutterSoundLevel(level: ShutterSoundLevel) {
        val current = _uiState.value
        if (current.usesSystemShutterSound || current.isShutterSoundMuted ||
            current.shutterSoundLevel == level
        ) return
        saveShutterSoundSetting { settingsRepository.setShutterSoundLevel(level) }
    }

    fun setShutterSoundMuted(muted: Boolean) {
        if (_uiState.value.isShutterSoundMuted == muted) return
        saveShutterSoundSetting { settingsRepository.setShutterSoundMuted(muted) }
    }

    private fun saveShutterSoundSetting(save: suspend () -> Unit) {
        val current = _uiState.value
        if (current.isLoading || current.isShutterSoundSaving) return
        _uiState.update { it.copy(isShutterSoundSaving = true, errorMessageRes = null) }
        viewModelScope.launch {
            try {
                save()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                showWriteError(error)
            } finally {
                _uiState.update { it.copy(isShutterSoundSaving = false) }
            }
        }
    }

    fun setTelemetryConsent(analyticsEnabled: Boolean, crashReportingEnabled: Boolean) {
        val current = _uiState.value
        if (current.isLoading || current.isTelemetrySaving) return
        _uiState.update { it.copy(isTelemetrySaving = true, errorMessageRes = null) }
        viewModelScope.launch {
            try {
                settingsRepository.setTelemetryConsent(analyticsEnabled, crashReportingEnabled)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                showWriteError(error)
            } finally {
                _uiState.update { it.copy(isTelemetrySaving = false) }
            }
        }
    }

    private fun showWriteError(error: Exception) {
        Log.e(TAG, "Unable to save camera settings", error)
        _uiState.update {
            it.copy(errorMessageRes = R.string.settings_save_failed)
        }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}
