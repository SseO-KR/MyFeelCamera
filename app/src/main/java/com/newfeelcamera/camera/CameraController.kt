package com.newfeelcamera.camera

import android.content.Context
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner

class CameraController(
    private val context: Context,
) {

    // TODO(P0-CAMERA-01 / Roadmap Step 1):
    // ProcessCameraProvider를 얻고 Preview UseCase를 lifecycleOwner에 bind한다.
    //
    // 학습 포인트:
    // - ProcessCameraProvider.getInstance(context) — ListenableFuture를 반환하는 비동기 API
    // - Preview UseCase 생성과 surfaceProvider 연결
    // - bindToLifecycle(lifecycleOwner, cameraSelector, preview)
    // - 후면 카메라 전용이므로 CameraSelector.DEFAULT_BACK_CAMERA를 직접 사용한다
    //   (00-product-requirements.md §2 — 전면 카메라는 scope 밖, 감싸는 추상화를 만들지 않는다)
    //
    // 관련 문서:
    // - 02-camera-pipeline.md §1
    // - 01-architecture.md §2, §4
    //
    // 주의:
    // 이 함수를 suspend로 만들지, 콜백/Executor 기반으로 만들지는 정하지 않았다.
    // ProcessCameraProvider.getInstance()의 비동기 처리 방식을 직접 학습하며 선택한다.
    fun bind(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
    ) {
        TODO()
    }
}
