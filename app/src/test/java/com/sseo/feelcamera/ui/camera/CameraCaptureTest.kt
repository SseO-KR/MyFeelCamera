package com.sseo.feelcamera.ui.camera

import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.sseo.feelcamera.R
import com.sseo.feelcamera.camera.CameraController
import com.sseo.feelcamera.camera.ShutterSoundPlayer
import com.sseo.feelcamera.camera.capture.CaptureException
import com.sseo.feelcamera.camera.capture.PhotoOrientation
import com.sseo.feelcamera.camera.capture.SavedPhoto
import com.sseo.feelcamera.camera.filter.FilmPreset
import com.sseo.feelcamera.data.analytics.CaptureAnalytics
import com.sseo.feelcamera.data.gallery.RecentPhotoRepository
import com.sseo.feelcamera.data.settings.AppSettingsRepository
import com.sseo.feelcamera.data.settings.ShutterSoundLevel
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class CameraCaptureTest {
    @get:Rule val temp = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()
    private lateinit var scope: CoroutineScope
    private lateinit var store: AppSettingsRepository
    private lateinit var controller: CameraController
    private lateinit var sound: ShutterSoundPlayer
    private lateinit var analytics: CaptureAnalytics
    private lateinit var vm: CameraViewModel
    private lateinit var logs: org.mockito.MockedStatic<Log>

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        logs = Mockito.mockStatic(Log::class.java)
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        store = AppSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) {
            File(temp.root, "settings.preferences_pb")
        })
        controller = mock(CameraController::class.java)
        sound = mock(ShutterSoundPlayer::class.java)
        analytics = mock(CaptureAnalytics::class.java)
        vm = CameraViewModel(controller, sound, store, mock(RecentPhotoRepository::class.java), analytics)
    }

    @After fun cleanup() {
        // CameraViewModel가 시작한 Main scope도 취소해 다음 테스트로 작업이 새지 않게 한다.
        androidx.lifecycle.ViewModelStore().apply { put("camera", vm); clear() }
        scope.cancel()
        Dispatchers.resetMain()
        logs.close()
    }

    private suspend fun ready() {
        // 실제 bind 구현 대신 카메라 준비 결과만 제어한다.
        vm.onIntent(CameraAction.CameraPermissionChanged(true))
        vm.bindCamera(mock(androidx.lifecycle.LifecycleOwner::class.java),
            mock(androidx.camera.core.Preview.SurfaceProvider::class.java))
    }

    @Test fun shutterSnapshotsFilmAndSoundAndIgnoresDuplicateCapture() = runTest(dispatcher) {
        store.setShutterSoundLevel(ShutterSoundLevel.LOW)
        advanceUntilIdle()
        ready()
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val capturedFilms = mutableListOf<FilmPreset>()
        val uri = mock(Uri::class.java)
        // Mockito의 suspend 호출은 Continuation을 통해 지연 완료시킨다.
        doAnswer { invocation ->
            val film = invocation.getArgument<FilmPreset>(0)
            val onShutter = invocation.getArgument<() -> Unit>(1)
            @Suppress("UNCHECKED_CAST")
            val continuation = invocation.rawArguments.last() as kotlin.coroutines.Continuation<SavedPhoto>
            scope.launch {
                capturedFilms += film
                entered.complete(Unit)
                finish.await()
                onShutter()
                continuation.resumeWith(Result.success(SavedPhoto(uri, PhotoOrientation.PORTRAIT)))
            }
            kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
        }.`when`(controller).takePhoto(anyFilm(), anyCallback())

        vm.onIntent(CameraAction.FilmSelected(FilmPreset.DUSK))
        vm.onIntent(CameraAction.ShutterClicked)
        runCurrent()
        assertTrue(entered.isCompleted)
        assertTrue(vm.uiState.value.isCapturing)
        vm.onIntent(CameraAction.FilmSelected(FilmPreset.WARM))
        vm.onIntent(CameraAction.ShutterClicked)
        store.setShutterSoundLevel(ShutterSoundLevel.HIGH)
        finish.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(FilmPreset.DUSK), capturedFilms)
        assertEquals(uri, vm.uiState.value.lastSavedPhotoUri)
        assertFalse(vm.uiState.value.isCapturing)
        verify(sound).play(ShutterSoundLevel.LOW)
        verify(analytics).photoSaved(com.sseo.feelcamera.data.analytics.PhotoSavedEvent(
            FilmPreset.DUSK, PhotoOrientation.PORTRAIT, false, false))
    }

    @Test fun everyFailureReleasesCaptureStateAndAllowsNextCapture() = runTest(dispatcher) {
        advanceUntilIdle()
        ready()
        val errors = listOf(CaptureException.CameraCaptureFailed(Exception()),
            CaptureException.ProcessingFailed(Exception()), CaptureException.SaveFailed(Exception()))
        val uri = mock(Uri::class.java)
        errors.forEach { error ->
            doAnswer { throw error }.`when`(controller).takePhoto(anyFilm(), anyCallback())
            vm.onIntent(CameraAction.ShutterClicked)
            advanceUntilIdle()
            assertFalse(vm.uiState.value.isCapturing)
            assertEquals(error.userMessageRes, vm.pendingNotice.value?.messageRes)
            doReturn(SavedPhoto(uri, PhotoOrientation.LANDSCAPE)).`when`(controller).takePhoto(anyFilm(), anyCallback())
            vm.onIntent(CameraAction.ShutterClicked)
            advanceUntilIdle()
            assertEquals(uri, vm.uiState.value.lastSavedPhotoUri)
            assertEquals(R.string.photo_saved, vm.pendingNotice.value?.messageRes)
        }
    }

    @Test fun unrelatedSettingsChangesKeepManuallySelectedFilm() = runTest(dispatcher) {
        advanceUntilIdle()
        vm.onIntent(CameraAction.FilmSelected(FilmPreset.DUSK))
        store.setShowGrid(false)
        store.setShutterSoundLevel(ShutterSoundLevel.HIGH)
        advanceUntilIdle()
        assertEquals(FilmPreset.DUSK, vm.uiState.value.selectedFilm)
        assertFalse(vm.uiState.value.showGrid)
        assertEquals(ShutterSoundLevel.HIGH, vm.uiState.value.shutterSoundLevel)
        store.setStartFilm(FilmPreset.DUSK)
        advanceUntilIdle()
        store.setStartFilm(FilmPreset.WARM)
        advanceUntilIdle()
        assertEquals(FilmPreset.WARM, vm.uiState.value.selectedFilm)
    }

    private fun anyCallback(): () -> Unit {
        return any<() -> Unit>() ?: {}
    }

    private fun anyFilm(): FilmPreset {
        return any<FilmPreset>() ?: FilmPreset.WARM
    }
}
