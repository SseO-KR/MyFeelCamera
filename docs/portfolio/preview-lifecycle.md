# 프리뷰 생명주기와 자원 소유권

[프로젝트 소개로 돌아가기](../../README.md) · [이전: 필터 파이프라인](filter-pipeline.md) · [다음: 회전과 배치](rotation-and-layout.md)

> 화면이 없어지는 시점, CameraX가 Surface 사용을 끝내는 시점, GL 자원을 지울 수 있는 시점은 서로 달랐습니다. 이 차이를 기준으로 소유권과 해제 순서를 정리했습니다.

## 목차

- [발견한 문제](#발견한-문제)
- [SurfaceProvider 소유권을 다시 판단한 이유](#surfaceprovider-소유권을-다시-판단한-이유)
- [해제 시점을 분리한 방법](#해제-시점을-분리한-방법)
- [화면 재생성과 자원 복구를 나눠 다루기](#화면-재생성과-자원-복구를-나눠-다루기)
- [검증과 배운 점](#검증과-배운-점)

## 발견한 문제

초기에는 방향에 따라 `Column`과 `Row`로 화면 구조를 전환했습니다. 이때 하위 Preview View가 다시 만들어지며 영상에 공백이 발생했습니다. 또 Surface 사용 완료 콜백에서 GL 자원을 정리하는 과정에서는 `invalid current EGLDisplay` 오류가 관찰됐습니다. **(개발 중 관찰)**

두 문제는 같은 카메라 화면에서 보였지만 원인을 나눠 봐야 했습니다. 첫 번째는 Compose 트리와 View의 유지 문제였고, 두 번째는 CameraX·Surface·EGL의 수명이 다른 데서 생긴 문제였습니다.

## SurfaceProvider 소유권을 다시 판단한 이유

처음에는 “여러 Surface 요청을 받아야 하니 더 오래 사는 CameraController가 공급자를 소유하면 된다”고 판단했습니다. 하지만 SurfaceTexture와 OES 텍스처를 실제로 생성하는 쪽은 렌더링 계층이었습니다.

Controller가 공급의 세부 구현까지 소유하면 렌더러의 최신 텍스처 상태를 알아야 합니다. 수명이 길다는 이유로 정한 소유권이 오히려 두 계층을 강하게 연결한다는 점을 발견했습니다.

이후 Controller는 `Preview.SurfaceProvider`를 전달받아 CameraX에 연결하고, GL/View 쪽이 공급에 필요한 렌더링 자원을 관리하도록 정리했습니다. **객체가 얼마나 오래 사는지와 함께, 누가 자원을 생성하고 복구할 수 있는지를 기준으로 소유권을 판단했습니다.** **(설계 수정)**

관련 코드: [CameraController](../../app/src/main/java/com/newfeelcamera/camera/CameraController.kt) · [CameraSurfaceProvider](../../app/src/main/java/com/newfeelcamera/camera/gl/CameraSurfaceProvider.kt) · [CameraGlSurfaceView](../../app/src/main/java/com/newfeelcamera/camera/gl/CameraGlSurfaceView.kt)

## 해제 시점을 분리한 방법

`queueEvent()`에 넣었다는 사실만으로 실행 시점의 EGL Context가 유효하다고 판단하지 않았습니다. CameraX의 완료 통지와 GL Context의 상태를 각각 확인해야 했습니다.

| 상황 | 현재 구현의 처리 |
| --- | --- |
| 새 Surface로 교체 | 이전 binding을 그리기 대상에서 제외하고 CameraX 사용 종료를 기다림 |
| CameraX 사용 종료 | 해당 Surface·SurfaceTexture를 한 번만 해제 |
| OES 텍스처 삭제 필요 | 텍스처 ID와 생성 Context 세대를 함께 보관 |
| 소유 Context가 current인 렌더러 콜백 | 보류한 GL 삭제를 실행 |
| 새 Context 생성 | 이전 세대의 ID를 새 Context에 적용하지 않음 |
| View 최종 폐기 | 새 요청을 거절하고, 늦게 도착한 사용 완료 통지에서도 정리 가능하게 유지 |

Surface 요청·완료·프레임 콜백과 draw·폐기 상태는 공통 lock으로 직렬화했습니다. Android 자원의 해제와 GL 텍스처 삭제를 별도 작업으로 관리해, View 종료 뒤에 완료 콜백이 도착하는 경우도 다뤘습니다. **(현재 구현 결정)**

관련 코드: [CameraRenderer](../../app/src/main/java/com/newfeelcamera/camera/gl/CameraRenderer.kt)

## 화면 재생성과 자원 복구를 나눠 다루기

화면은 고정된 `Box` 트리 안에 Preview View를 두고 배치를 바꾸는 방식으로 수정했습니다. 방향별 부모 구조 교체가 Preview를 새로 만드는 원인을 줄이기 위한 선택입니다.

동시에 실제 View나 EGL Context가 재생성될 수 있다는 전제는 유지했습니다. 화면 구조를 안정화하는 것과, 재생성이 발생했을 때 자원을 다시 공급하는 것은 각각 확인해야 할 문제였습니다. Surface를 새로 공급하는 일이 곧바로 모든 CameraX UseCase의 재바인딩을 요구하도록 묶지도 않았습니다. **(설계 원칙)**

## 검증과 배운 점

2026-09-23 기록에서는 실제 EGL·OES·SurfaceRequest를 사용하는 생명주기 테스트 7개가 통과했습니다. 같은 Context에서 20회 교체, 새 Context에서 동일한 숫자의 텍스처 ID 보호, View 폐기 후 사용 완료, draw와 완료 콜백의 교차 실행 등을 확인했습니다. 홈 복귀와 분할 화면 왕복 후 사진 저장도 관찰했습니다. **(당시 검증 기록)**

이 결과는 모든 기기의 자원 누수 부재나 재생성 방지를 뜻하지 않습니다. 2026-10-08의 에뮬레이터 점검에서는 고정된 가상 장면만 사용할 수 있어, 화면 복귀 뒤 움직이는 프레임이 계속 갱신되는지는 당시 미검증으로 남겼습니다.

직접 확인한 실기기 환경은 [검증 기록](validation.md#실기기-검증-완료)에 정리했습니다.

이 과정을 통해 “카메라 생명주기”를 하나의 수명으로 다루는 대신 Composition, View, Surface, EGL, CameraX 각각의 생성·종료 조건을 구분하게 됐습니다. 소유권을 정할 때도 오래 살아 있는 객체보다 실제 자원 생성과 복구 책임을 먼저 살펴보게 됐습니다.

근거 문서: [자원 소유권 상세와 2026-09-23 결과](../05-lifecycle-and-resource-ownership.md) · [생명주기 테스트 코드](../../app/src/androidTest/java/com/newfeelcamera/camera/gl/CameraRendererLifecycleTest.kt) · [최근 검증 범위](validation.md)
