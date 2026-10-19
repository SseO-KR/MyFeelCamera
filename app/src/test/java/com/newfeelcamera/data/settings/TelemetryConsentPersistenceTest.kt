package com.newfeelcamera.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TelemetryConsentPersistenceTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `no monitoring consent exists on a new installation`() = runBlocking {
        val job = SupervisorJob()
        try {
            val repository = repository("new.preferences_pb", job)
            val consent = repository.settings.first().telemetryConsent
            assertFalse(consent.hasMadeChoice)
            assertFalse(consent.allowsAnalytics)
            assertFalse(consent.allowsCrashReporting)
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun `independent choices and withdrawal survive reopening the settings store`() = runBlocking {
        val filename = "persisted.preferences_pb"
        val firstJob = SupervisorJob()
        try {
            val repository = repository(filename, firstJob)
            repository.setShowGrid(false)
            repository.setTelemetryConsent(analyticsEnabled = true, crashReportingEnabled = false)
            val state = repository.settings.first()
            assertFalse(state.showGrid)
            assertTrue(state.telemetryConsent.hasMadeChoice)
            assertTrue(state.telemetryConsent.allowsAnalytics)
            assertFalse(state.telemetryConsent.allowsCrashReporting)
            repository.setTelemetryConsent(analyticsEnabled = false, crashReportingEnabled = true)
        } finally {
            firstJob.cancelAndJoin()
        }

        val secondJob = SupervisorJob()
        try {
            val repository = repository(filename, secondJob)
            val state = repository.settings.first()
            assertFalse(state.showGrid)
            assertFalse(state.telemetryConsent.allowsAnalytics)
            assertTrue(state.telemetryConsent.allowsCrashReporting)
            repository.setTelemetryConsent(analyticsEnabled = false, crashReportingEnabled = false)
        } finally {
            secondJob.cancelAndJoin()
        }

        val thirdJob = SupervisorJob()
        try {
            val consent = repository(filename, thirdJob).settings.first().telemetryConsent
            assertTrue(consent.hasMadeChoice)
            assertFalse(consent.allowsAnalytics)
            assertFalse(consent.allowsCrashReporting)
        } finally {
            thirdJob.cancelAndJoin()
        }
    }

    private fun repository(filename: String, job: Job): AppSettingsRepository = AppSettingsRepository(
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { temporaryFolder.root.resolve(filename) },
        ),
    )
}
