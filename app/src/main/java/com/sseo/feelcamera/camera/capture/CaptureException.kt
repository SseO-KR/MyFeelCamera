package com.sseo.feelcamera.camera.capture

import androidx.annotation.StringRes
import com.sseo.feelcamera.R

sealed class CaptureException(
    @field:StringRes val userMessageRes: Int,
    cause: Throwable? = null,
) : Exception(cause) {

    class CameraNotReady : CaptureException(
        userMessageRes = R.string.camera_not_ready,
    )

    class CameraCaptureFailed(
        cause: Throwable,
    ) : CaptureException(
        userMessageRes = R.string.camera_capture_failed,
        cause = cause,
    )

    class ProcessingFailed(
        cause: Throwable,
    ) : CaptureException(
        userMessageRes = R.string.camera_processing_failed,
        cause = cause,
    )

    class SaveFailed(
        cause: Throwable,
    ) : CaptureException(
        userMessageRes = R.string.camera_save_failed,
        cause = cause,
    )
}
