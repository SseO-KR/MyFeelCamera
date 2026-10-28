package com.sseo.feelcamera.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraNoticeStoreTest {
    @Test
    fun `same message posted twice gets a new pending notice`() {
        val notices = CameraNoticeStore()

        notices.post(1)
        val first = notices.pending.value!!
        notices.acknowledge(first.id)
        notices.post(1)
        val second = notices.pending.value!!

        assertEquals(first.messageRes, second.messageRes)
        assertEquals(first.id + 1, second.id)
    }

    @Test
    fun `acknowledging an older notice keeps the newer one`() {
        val notices = CameraNoticeStore()

        notices.post(1)
        val firstId = notices.pending.value!!.id
        notices.post(2)
        val latest = notices.pending.value

        notices.acknowledge(firstId)
        assertEquals(latest, notices.pending.value)

        notices.acknowledge(latest!!.id)
        assertNull(notices.pending.value)
    }
}
