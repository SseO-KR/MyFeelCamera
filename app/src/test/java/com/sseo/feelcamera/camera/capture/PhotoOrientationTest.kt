package com.sseo.feelcamera.camera.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PhotoOrientationTest {
    @Test
    fun classifiesSavedBitmapDimensions() {
        assertEquals(PhotoOrientation.PORTRAIT, PhotoOrientation.fromDimensions(3000, 4000))
        assertEquals(PhotoOrientation.LANDSCAPE, PhotoOrientation.fromDimensions(4000, 3000))
        assertEquals(PhotoOrientation.SQUARE, PhotoOrientation.fromDimensions(3000, 3000))
    }

    @Test
    fun rejectsInvalidDimensions() {
        assertThrows(IllegalArgumentException::class.java) {
            PhotoOrientation.fromDimensions(0, 3000)
        }
    }
}
