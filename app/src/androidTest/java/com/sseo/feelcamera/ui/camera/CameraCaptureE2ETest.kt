package com.sseo.feelcamera.ui.camera

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.sseo.feelcamera.camera.filter.FilmPreset
import com.sseo.feelcamera.testing.CameraE2ESupport
import com.sseo.feelcamera.testing.ExtendedCameraTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraCaptureE2ETest : CameraE2ESupport() {
    @get:Rule val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test fun warmAndDuskCaptureSaveTheSelectedFilmAndUpdateThumbnail() {
        waitForCamera(FilmPreset.WARM)
        assertPreviewFilm(FilmPreset.WARM)
        takePhoto(FilmPreset.WARM)
        selectFilm(FilmPreset.DUSK)
        assertPreviewFilm(FilmPreset.DUSK)
        takePhoto(FilmPreset.DUSK)
    }

    @Test fun switchingImmediatelyBeforeShutterUsesNewFilmAndCanSwitchBack() {
        waitForCamera(FilmPreset.WARM)
        selectFilm(FilmPreset.DUSK, waitForFrame = false)
        takePhoto(FilmPreset.DUSK)
        selectFilm(FilmPreset.WARM)
        takePhoto(FilmPreset.WARM)
    }

    @ExtendedCameraTest
    @Test fun tenSequentialCapturesRemainAvailableAndCreateTenPhotos() {
        waitForCamera(FilmPreset.WARM)
        val before = photos()
        repeat(10) { index ->
            val film = if (index % 2 == 0) FilmPreset.WARM else FilmPreset.DUSK
            selectFilm(film)
            takePhoto(film)
        }
        assertEquals(10, (photos() - before).size)
    }
}
