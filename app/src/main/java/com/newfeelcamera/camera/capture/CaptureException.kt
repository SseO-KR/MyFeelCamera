package com.newfeelcamera.camera.capture

sealed class CaptureException(
    val userMessage: String,
    cause: Throwable? = null,
) : Exception(userMessage, cause) {

    class CameraNotReady : CaptureException(
        userMessage = "카메라가 아직 준비되지 않았습니다.",
    )

    class CameraCaptureFailed(
        cause: Throwable,
    ) : CaptureException(
        userMessage = "사진 촬영에 실패했습니다.",
        cause = cause,
    )

    class ProcessingFailed(
        cause: Throwable,
    ) : CaptureException(
        userMessage = "사진 처리에 실패했습니다.",
        cause = cause,
    )

    class SaveFailed(
        cause: Throwable,
    ) : CaptureException(
        userMessage = "사진 저장에 실패했습니다.",
        cause = cause,
    )
}
