package com.newfeelcamera.ui.camera

import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CameraNotice(
    val id: Long,
    @field:StringRes val messageRes: Int,
)

internal class CameraNoticeStore {
    // 화면이 일시 중지된 동안 알림이 여러 번 오면 가장 최근 알림만 보관한다.
    private val _pending = MutableStateFlow<CameraNotice?>(null)
    val pending = _pending.asStateFlow()
    private var nextId = 0L

    fun post(@StringRes messageRes: Int) {
        _pending.value = CameraNotice(++nextId, messageRes)
    }

    fun acknowledge(id: Long) {
        _pending.update { current ->
            if (current?.id == id) null else current
        }
    }
}
