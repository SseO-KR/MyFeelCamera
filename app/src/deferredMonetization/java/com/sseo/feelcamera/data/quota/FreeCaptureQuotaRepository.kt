package com.sseo.feelcamera.data.quota

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

const val FREE_CAPTURE_LIMIT = 24

private val Context.freeCaptureDataStore by preferencesDataStore(name = "free_capture_quota")

class FreeCaptureLimitReachedException : IllegalStateException("Free capture limit reached")

@Singleton
class FreeCaptureQuotaRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val usedShotsKey = intPreferencesKey("used_shots")

    val usedShots: Flow<Int> = context.freeCaptureDataStore.data.map { preferences ->
        (preferences[usedShotsKey] ?: 0).coerceIn(0, FREE_CAPTURE_LIMIT)
    }

    suspend fun recordSavedPhoto(): Int {
        val updatedPreferences = context.freeCaptureDataStore.edit { preferences ->
            val usedShots = (preferences[usedShotsKey] ?: 0).coerceAtLeast(0)
            if (usedShots >= FREE_CAPTURE_LIMIT) {
                throw FreeCaptureLimitReachedException()
            }
            preferences[usedShotsKey] = usedShots + 1
        }

        return checkNotNull(updatedPreferences[usedShotsKey])
    }
}
