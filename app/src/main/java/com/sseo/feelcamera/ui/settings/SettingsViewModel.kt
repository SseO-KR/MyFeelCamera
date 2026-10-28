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
    val isSaving: Boolean = false,
    val startFilm: FilmPreset = FilmPreset.WARM,
    val showGrid: Boolean = true,
    val shutterSoundLevel: ShutterSoundLevel = ShutterSoundLevel.MEDIUM,
    val usesSystemShutterSound: Boolean = CameraInfo.mustPlayShutterSound(),
    val telemetryConsent: TelemetryConsent = TelemetryConsent(),
    @field:StringRes val errorMessageRes: Int? = null,
) {
    val isShutterSoundMuted: Boolean
        get() = shutterSoundLevel == ShutterSoundLevel.OFF
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: AppSettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
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
        if (current.isLoading || current.isSaving || current.startFilm == film) return

        _uiState.update { it.copy(isSaving = true, errorMessageRes = null) }

        viewModelScope.launch {
            try {
                settingsRepository.setStartFilm(film)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                showWriteError(error)
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun setShowGrid(showGrid: Boolean) {
        val current = _uiState.value
        if (current.isLoading || current.isSaving || current.showGrid == showGrid) return
        _uiState.update { it.copy(isSaving = true, errorMessageRes = null) }
        viewModelScope.launch {
            try {
                settingsRepository.setShowGrid(showGrid)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                showWriteError(error)
            } finally {
                _uiState.update { it.copy(isSaving = false) }
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
        if (current.isLoading || current.isSaving) return
        _uiState.update { it.copy(isSaving = true, errorMessageRes = null) }
        viewModelScope.launch {
            try {
                save()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                showWriteError(error)
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun setTelemetryConsent(analyticsEnabled: Boolean, crashReportingEnabled: Boolean) {
        val current = _uiState.value
        if (current.isLoading || current.isSaving) return
        _uiState.update { it.copy(isSaving = true, errorMessageRes = null) }
        viewModelScope.launch {
            try {
                settingsRepository.setTelemetryConsent(analyticsEnabled, crashReportingEnabled)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                showWriteError(error)
            } finally {
                _uiState.update { it.copy(isSaving = false) }
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
