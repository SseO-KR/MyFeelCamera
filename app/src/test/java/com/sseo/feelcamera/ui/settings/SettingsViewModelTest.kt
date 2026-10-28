package com.sseo.feelcamera.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.viewModelScope
import com.sseo.feelcamera.camera.filter.FilmPreset
import com.sseo.feelcamera.data.settings.AppSettingsRepository
import com.sseo.feelcamera.data.settings.ShutterSoundLevel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: SettingsViewModel
    private lateinit var store: ControlledSettingsStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = ControlledSettingsStore()
        viewModel = SettingsViewModel(AppSettingsRepository(store), usesSystemShutterSound = false)
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `start film can be changed while mute is saving`() = runTest {
        runCurrent()
        viewModel.setShutterSoundMuted(true)
        runCurrent()

        val savingState = viewModel.uiState.value
        assertTrue(savingState.isShutterSoundSaving)
        assertFalse(savingState.isStartFilmSaving)
        assertFalse(savingState.isGridSaving)
        assertFalse(savingState.isTelemetrySaving)

        viewModel.setStartFilm(FilmPreset.DUSK)
        runCurrent()
        store.releaseAllWrites()
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(FilmPreset.DUSK, state.startFilm)
        assertEquals(ShutterSoundLevel.OFF, state.shutterSoundLevel)
        assertFalse(state.isSaving)
    }

    @Test
    fun `navigation remains blocked until all independent saves finish`() = runTest {
        runCurrent()
        viewModel.setShutterSoundMuted(true)
        runCurrent()
        viewModel.setStartFilm(FilmPreset.DUSK)
        runCurrent()

        assertTrue(viewModel.uiState.value.isShutterSoundSaving)
        assertTrue(viewModel.uiState.value.isStartFilmSaving)

        store.releaseNextWrite()
        runCurrent()

        val partiallySavedState = viewModel.uiState.value
        assertFalse(partiallySavedState.isShutterSoundSaving)
        assertTrue(partiallySavedState.isStartFilmSaving)
        assertTrue(partiallySavedState.isSaving)

        store.releaseNextWrite()
        runCurrent()
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(FilmPreset.DUSK, viewModel.uiState.value.startFilm)
        assertEquals(ShutterSoundLevel.OFF, viewModel.uiState.value.shutterSoundLevel)
    }

    @Test
    fun `mute and volume share a saving guard to prevent conflicting sound writes`() = runTest {
        runCurrent()
        viewModel.setShutterSoundMuted(true)
        runCurrent()

        viewModel.setShutterSoundMuted(true)
        viewModel.setShutterSoundLevel(ShutterSoundLevel.HIGH)
        runCurrent()
        store.releaseAllWrites()
        runCurrent()

        assertEquals(1, store.writeCount)
        assertEquals(ShutterSoundLevel.OFF, viewModel.uiState.value.shutterSoundLevel)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `grid and telemetry changes are accepted while mute is saving`() = runTest {
        runCurrent()
        viewModel.setShutterSoundMuted(true)
        runCurrent()
        viewModel.setShowGrid(false)
        viewModel.setTelemetryConsent(analyticsEnabled = true, crashReportingEnabled = false)
        runCurrent()

        assertTrue(viewModel.uiState.value.isGridSaving)
        assertTrue(viewModel.uiState.value.isTelemetrySaving)
        assertFalse(viewModel.uiState.value.isStartFilmSaving)

        store.releaseAllWrites()
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.showGrid)
        assertTrue(state.telemetryConsent.allowsAnalytics)
        assertFalse(state.telemetryConsent.allowsCrashReporting)
        assertEquals(ShutterSoundLevel.OFF, state.shutterSoundLevel)
        assertFalse(state.isSaving)
    }

    private class ControlledSettingsStore : DataStore<Preferences> {
        override val data = MutableStateFlow<Preferences>(emptyPreferences())
        private val mutex = Mutex()
        private val pendingWrites = ArrayDeque<CompletableDeferred<Unit>>()
        private var holdWrites = true
        var writeCount = 0
            private set

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            mutex.withLock {
                writeCount++
                if (holdWrites) {
                    val gate = CompletableDeferred<Unit>()
                    pendingWrites.addLast(gate)
                    gate.await()
                }
                transform(data.value).also { data.value = it }
            }

        fun releaseNextWrite() {
            pendingWrites.removeFirst().complete(Unit)
        }

        fun releaseAllWrites() {
            holdWrites = false
            while (pendingWrites.isNotEmpty()) pendingWrites.removeFirst().complete(Unit)
        }
    }
}
