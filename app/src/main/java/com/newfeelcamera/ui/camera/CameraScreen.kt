package com.newfeelcamera.ui.camera

import androidx.compose.runtime.Composable
import com.newfeelcamera.camera.CameraController

// TODO(P0-CAMERA-02 / Roadmap Step 1):
// PreviewView를 AndroidView로 호스팅하고, LocalLifecycleOwner와
// PreviewView.surfaceProvider를 cameraController.bind()에 전달해 연결한다.
//
// 학습 포인트:
// - AndroidView(factory = { ... }) — Compose와 View 상호운용
// - PreviewView(context)와 그 surfaceProvider 프로퍼티
// - LocalLifecycleOwner.current
// - factory가 언제 한 번만 실행되는지 (Compose 재구성과 View lifetime의 관계)
//
// 관련 문서:
// - 00-product-requirements.md §3, §8
// - 02-camera-pipeline.md §1, §2
// - 05-lifecycle-and-resource-ownership.md §2
//
// 주의:
// CameraController는 이 함수가 직접 생성하지 않는다 — MainActivity가 생성해서 전달한다.
@Composable
fun CameraScreen(
    cameraController: CameraController,
) {
    TODO()
}
