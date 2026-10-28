package com.sseo.feelcamera.testing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import androidx.test.platform.app.InstrumentationRegistry
import com.sseo.feelcamera.camera.filter.FilmPreset
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import androidx.test.uiautomator.UiDevice

internal object TestImages {
    val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    val artifacts: File get() {
        val label = InstrumentationRegistry.getArguments().getString("artifactLabel", "local")
            .replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        return File(context.getExternalFilesDir(null), "test-artifacts/$label").apply { mkdirs() }
    }

    fun input(): Bitmap = asset("input")
    fun reference(film: FilmPreset): Bitmap = asset(film.name.lowercase())

    private fun asset(name: String): Bitmap =
        InstrumentationRegistry.getInstrumentation().context.assets.open("film/$name.png").use {
            requireNotNull(BitmapFactory.decodeStream(it))
        }

    fun save(name: String, bitmap: Bitmap) {
        File(artifacts, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun assertMatches(
        name: String, expected: Bitmap, actual: Bitmap,
        maxMeanError: Double = 0.8, maxChannelError: Int = 4, border: Int = 0,
    ) {
        if (expected.width != actual.width || expected.height != actual.height) {
            save("$name-expected", expected)
            save("$name-actual", actual)
        }
        assertEquals("$name width", expected.width, actual.width)
        assertEquals("$name height", expected.height, actual.height)
        var sum = 0L
        var maximum = 0
        var channels = 0
        val diff = Bitmap.createBitmap(actual.width, actual.height, Bitmap.Config.ARGB_8888)
        try {
            for (y in border until actual.height - border) for (x in border until actual.width - border) {
                val e = expected.getPixel(x, y)
                val a = actual.getPixel(x, y)
                val errors = intArrayOf(abs(Color.red(e) - Color.red(a)),
                    abs(Color.green(e) - Color.green(a)), abs(Color.blue(e) - Color.blue(a)))
                errors.forEach { sum += it; maximum = maxOf(maximum, it); channels++ }
                diff.setPixel(x, y, Color.rgb((errors[0] * 8).coerceAtMost(255),
                    (errors[1] * 8).coerceAtMost(255), (errors[2] * 8).coerceAtMost(255)))
            }
            check(channels > 0) { "Comparison region is empty" }
            val mean = sum.toDouble() / channels
            if (mean > maxMeanError || maximum > maxChannelError) {
                save("$name-expected", expected)
                save("$name-actual", actual)
                save("$name-diff-x8", diff)
                fail("$name: mean RGB error=$mean (limit=$maxMeanError), max=$maximum (limit=$maxChannelError)")
            }
        } finally {
            diff.recycle()
        }
    }

    /** Bitmap 좌표에서 직접 회전·크롭한다. GL texture 행렬을 재사용하지 않는다. */
    fun geometry(source: Bitmap, crop: Rect, rotation: Int): Bitmap {
        val swaps = rotation == 90 || rotation == 270
        val result = Bitmap.createBitmap(if (swaps) crop.height() else crop.width(),
            if (swaps) crop.width() else crop.height(), Bitmap.Config.ARGB_8888)
        for (y in 0 until result.height) for (x in 0 until result.width) {
            val (sx, sy) = when (rotation) {
                0 -> x to y
                90 -> y to crop.height() - 1 - x
                180 -> crop.width() - 1 - x to crop.height() - 1 - y
                270 -> crop.width() - 1 - y to x
                else -> error("Unsupported rotation: $rotation")
            }
            result.setPixel(x, y, source.getPixel(crop.left + sx, crop.top + sy))
        }
        return result
    }
}

class FailureArtifactsRule : TestWatcher() {
    override fun failed(e: Throwable, description: Description) {
        // 증거 수집 실패가 원래 테스트 실패를 가리지 않도록 한다.
        runCatching {
            val name = "${description.className.substringAfterLast('.')}-${description.methodName}"
                .replace(Regex("[^a-zA-Z0-9_.-]"), "_")
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .takeScreenshot(File(TestImages.artifacts, "$name-screen.png"))
        }
    }
}
