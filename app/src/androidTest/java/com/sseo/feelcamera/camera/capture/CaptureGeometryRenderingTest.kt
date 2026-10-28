package com.sseo.feelcamera.camera.capture

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sseo.feelcamera.camera.filter.FilmPreset
import com.sseo.feelcamera.testing.FailureArtifactsRule
import com.sseo.feelcamera.testing.TestImages
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureGeometryRenderingTest {
    @get:Rule val artifacts = FailureArtifactsRule()

    @Test fun allRotationsAndAsymmetricCropsPreservePixelsForBothFilms() {
        val input = TestImages.input()
        try {
            CaptureEglSession.create().use {
                CaptureGlRenderer(TestImages.context.assets).use { renderer ->
                    renderer.initialize()
                    FilmPreset.entries.forEach { film ->
                        val reference = TestImages.reference(film)
                        try {
                            listOf(Rect(0, 0, input.width, input.height), Rect(8, 4, 80, 52)).forEach { crop ->
                                listOf(0, 90, 180, 270).forEach { rotation ->
                                    val expected = TestImages.geometry(reference, crop, rotation)
                                    val actual = renderer.render(input.asInput(film, rotation, crop))
                                    try { TestImages.assertMatches("geometry-$film-$rotation-${crop.width()}", expected, actual) }
                                    finally { expected.recycle(); actual.recycle() }
                                }
                            }
                        } finally { reference.recycle() }
                    }
                }
            }
        } finally { input.recycle() }
    }

    @Test fun invalidGeometryIsRejectedAndNextRenderStillWorks() {
        val input = TestImages.input()
        try {
            CaptureEglSession.create().use {
                CaptureGlRenderer(TestImages.context.assets).use { renderer ->
                    renderer.initialize()
                    listOf(input.asInput(FilmPreset.WARM, 45),
                        input.asInput(FilmPreset.WARM, crop = Rect(-1, 0, 30, 30))).forEach { invalid ->
                        org.junit.Assert.assertThrows(IllegalStateException::class.java) { renderer.render(invalid) }
                    }
                    val expected = TestImages.reference(FilmPreset.DUSK)
                    val actual = renderer.render(input.asInput(FilmPreset.DUSK))
                    try { TestImages.assertMatches("geometry-after-failure", expected, actual) }
                    finally { expected.recycle(); actual.recycle() }
                }
            }
        } finally { input.recycle() }
    }
}
