package com.sseo.feelcamera.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.sseo.feelcamera.camera.filter.FilmPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CameraSettingsPersistenceTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun filmGridAndSoundSurviveClosingAndReopeningTheStore() = runBlocking {
        val file = temporaryFolder.root.resolve("camera.preferences_pb")
        val firstJob = SupervisorJob()
        try {
            val repository = AppSettingsRepository(PreferenceDataStoreFactory.create(
                scope = CoroutineScope(firstJob + Dispatchers.IO), produceFile = { file }))
            repository.setStartFilm(FilmPreset.DUSK)
            repository.setShowGrid(false)
            repository.setShutterSoundLevel(ShutterSoundLevel.LOW)
        } finally { firstJob.cancelAndJoin() }

        val secondJob = SupervisorJob()
        try {
            val reopened = AppSettingsRepository(PreferenceDataStoreFactory.create(
                scope = CoroutineScope(secondJob + Dispatchers.IO), produceFile = { file })).settings.first()
            assertEquals(FilmPreset.DUSK, reopened.startFilm)
            assertFalse(reopened.showGrid)
            assertEquals(ShutterSoundLevel.LOW, reopened.shutterSoundLevel)
        } finally { secondJob.cancelAndJoin() }
    }
}
