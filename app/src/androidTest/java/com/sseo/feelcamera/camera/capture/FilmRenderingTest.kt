package com.sseo.feelcamera.camera.capture

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sseo.feelcamera.camera.filter.FilmPreset
import com.sseo.feelcamera.testing.FailureArtifactsRule
import com.sseo.feelcamera.testing.PreviewFixture
import com.sseo.feelcamera.testing.TestImages
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilmRenderingTest {
    @get:Rule val artifacts = FailureArtifactsRule()

    @Test fun captureMatchesFrozenReferencesForBothFilms() {
        val input = TestImages.input()
        try {
            CaptureEglSession.create().use {
                CaptureGlRenderer(TestImages.context.assets).use { renderer ->
                    renderer.initialize()
                    FilmPreset.entries.forEach { film ->
                        val expected = TestImages.reference(film)
                        val actual = renderer.render(input.asInput(film))
                        try { TestImages.assertMatches("capture-$film", expected, actual) }
                        finally { expected.recycle(); actual.recycle() }
                    }
                }
            }
        } finally { input.recycle() }
    }

    @Test fun previewMatchesFrozenReferencesForBothFilms() {
        val input = TestImages.input()
        try {
            PreviewFixture(input).use { preview ->
                FilmPreset.entries.forEach { film ->
                    val expected = TestImages.reference(film)
                    val actual = preview.render(film)
                    try { TestImages.assertMatches("preview-$film", expected, actual, maxMeanError = 1.5, maxChannelError = 8) }
                    finally { expected.recycle(); actual.recycle() }
                }
            }
        } finally { input.recycle() }
    }

    @Test fun warmDuskWarmRestoresBothRenderersAndFilmsRemainDistinct() {
        val input = TestImages.input()
        val previews = mutableListOf<Bitmap>()
        val captures = mutableListOf<Bitmap>()
        val films = listOf(FilmPreset.WARM, FilmPreset.DUSK, FilmPreset.WARM)
        try {
            PreviewFixture(input).use { preview -> films.forEach { previews += preview.render(it) } }
            CaptureEglSession.create().use {
                CaptureGlRenderer(TestImages.context.assets).use { renderer ->
                    renderer.initialize()
                    films.forEach { captures += renderer.render(input.asInput(it)) }
                }
            }
            TestImages.assertMatches("preview-restored", previews.first(), previews.last())
            TestImages.assertMatches("capture-restored", captures.first(), captures.last())
            assertFalse("Both preview films look identical", previews[0].sameAs(previews[1]))
            assertFalse("Both capture films look identical", captures[0].sameAs(captures[1]))
            films.indices.forEach { index ->
                TestImages.assertMatches("preview-capture-$index", captures[index], previews[index],
                    maxMeanError = 1.5, maxChannelError = 8)
            }
        } finally {
            input.recycle()
            (previews + captures).forEach { it.recycle() }
        }
    }
}

internal fun Bitmap.asInput(film: FilmPreset, rotation: Int = 0, crop: Rect = Rect(0, 0, width, height)) =
    CaptureInput(this, crop, rotation, film)
