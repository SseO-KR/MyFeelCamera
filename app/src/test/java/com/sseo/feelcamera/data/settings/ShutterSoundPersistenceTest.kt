package com.sseo.feelcamera.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ShutterSoundPersistenceTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `new installations keep the default audible shutter`() = runBlocking {
        val job = SupervisorJob()
        try {
            val repository = AppSettingsRepository(dataStore("new.preferences_pb", job))
            assertEquals(ShutterSoundLevel.MEDIUM, repository.settings.first().shutterSoundLevel)
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun `mute survives reopening and restores each previous volume without changing other settings`() = runBlocking {
        for (level in listOf(ShutterSoundLevel.LOW, ShutterSoundLevel.MEDIUM, ShutterSoundLevel.HIGH)) {
            val filename = "${level.name}.preferences_pb"
            val firstJob = SupervisorJob()
            try {
                val repository = AppSettingsRepository(dataStore(filename, firstJob))
                repository.setShowGrid(false)
                repository.setShutterSoundLevel(level)
                repository.setShutterSoundMuted(true)
                assertEquals(ShutterSoundLevel.OFF, repository.settings.first().shutterSoundLevel)
            } finally {
                firstJob.cancelAndJoin()
            }

            val secondJob = SupervisorJob()
            try {
                val repository = AppSettingsRepository(dataStore(filename, secondJob))
                assertEquals(ShutterSoundLevel.OFF, repository.settings.first().shutterSoundLevel)
                repository.setShutterSoundMuted(true)
                repository.setShutterSoundMuted(false)
                assertEquals(level, repository.settings.first().shutterSoundLevel)
            } finally {
                secondJob.cancelAndJoin()
            }

            val thirdJob = SupervisorJob()
            try {
                val settings = AppSettingsRepository(dataStore(filename, thirdJob)).settings.first()
                assertEquals(level, settings.shutterSoundLevel)
                assertFalse(settings.showGrid)
            } finally {
                thirdJob.cancelAndJoin()
            }
        }
    }

    @Test
    fun `legacy off setting stays muted and unmuting falls back to medium`() = runBlocking {
        val job = SupervisorJob()
        try {
            val store = dataStore("legacy.preferences_pb", job)
            store.edit { it[stringPreferencesKey("shutter_sound_level")] = "OFF" }
            val repository = AppSettingsRepository(store)

            assertEquals(ShutterSoundLevel.OFF, repository.settings.first().shutterSoundLevel)
            repository.setShutterSoundMuted(false)
            assertEquals(ShutterSoundLevel.MEDIUM, repository.settings.first().shutterSoundLevel)
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun `unmuting an audible legacy setting preserves its volume`() = runBlocking {
        val job = SupervisorJob()
        try {
            val store = dataStore("legacy-audible.preferences_pb", job)
            store.edit { it[stringPreferencesKey("shutter_sound_level")] = "HIGH" }
            val repository = AppSettingsRepository(store)

            repository.setShutterSoundMuted(false)
            assertEquals(ShutterSoundLevel.HIGH, repository.settings.first().shutterSoundLevel)
            repository.setShutterSoundMuted(true)
            repository.setShutterSoundMuted(false)
            assertEquals(ShutterSoundLevel.HIGH, repository.settings.first().shutterSoundLevel)
        } finally {
            job.cancelAndJoin()
        }
    }

    private fun dataStore(filename: String, job: Job) = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(job + Dispatchers.IO),
        produceFile = { temporaryFolder.root.resolve(filename) },
    )
}
