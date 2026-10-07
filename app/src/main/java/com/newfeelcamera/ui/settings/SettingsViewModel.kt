package com.newfeelcamera.ui.settings

import android.util.Log
import androidx.camera.core.CameraInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.newfeelcamera.camera.filter.FilmPreset
import com.newfeelcamera.data.billing.BillingRepository
import com.newfeelcamera.data.settings.AppSettingsRepository
import com.newfeelcamera.data.settings.ShutterSoundLevel
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
    val mustPlayShutterSound: Boolean = CameraInfo.mustPlayShutterSound(),
    val errorMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: AppSettingsRepository,
    private val billingRepository: BillingRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    val purchaseRefreshState = billingRepository.refreshState
    val unlimitedAccessState = billingRepository.unlimitedAccessState
    val unlimitedProductState = billingRepository.productState
    val purchaseLaunchState = billingRepository.purchaseLaunchState

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        startFilm = settings.startFilm,
                        showGrid = settings.showGrid,
                        shutterSoundLevel = settings.shutterSoundLevel,
                    )
                }
            }
        }
    }

    fun setStartFilm(film: FilmPreset) {
        val current = _uiState.value
        if (current.isLoading || current.isSaving || current.startFilm == film) return

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }

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
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
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
        if (current.isLoading || current.isSaving || current.mustPlayShutterSound ||
            current.shutterSoundLevel == level
        ) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                settingsRepository.setShutterSoundLevel(level)
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
            it.copy(errorMessage = "설정을 저장하지 못했습니다. 다시 시도해 주세요.")
        }
    }

    fun retryPurchaseRefresh() {
        billingRepository.refreshPurchases()
    }

    fun retryProductDetails() {
        billingRepository.refreshProductDetails()
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}
