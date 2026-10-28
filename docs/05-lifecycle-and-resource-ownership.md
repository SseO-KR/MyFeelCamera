# 05. Lifecycle & Resource Ownership

이 문서는 `00` §9 / `01` §3이 정의한 8개 레이어의 소유권과, 한 레이어의 파괴/재생성이 다른 레이어로 어떻게 전파되는지를 다룬다. `01` §4(SurfaceProvider 인스턴스 lifetime), `02` §2(`onFrameAvailable` → GL 스레드 전달), `04` §6/§7(layout mode 전환과 Surface/EGL의 상호작용)이 이 문서로 넘긴 질문에 답한다.

표기: **(Product Requirement)** · **(Android/CameraX/EGL API Contract)** · **(Verify First)** · **(Later)**.

## 1. 8개 레이어 재확인

```
Composition
Android View
Surface
EGL Context
SurfaceTexture
CameraX SurfaceRequest
CameraX UseCase
Camera device
```

이 목록과 "재생성 자체를 막기보다 재생성됐을 때 정상 복구하는 것을 우선한다"는 원칙은 `00` §9/§10, `01` §3에서 이미 확정했다 — 여기서 다시 판단하지 않는다.

## 2. 레이어별 소유권과 파괴 조건

| 레이어 | 소유자 | 무엇이 파괴/재생성을 유발하는가 |
|---|---|---|
| Composition | Compose runtime | 호스트(Activity의 `setContent` 루트 등)가 종료될 때. 일반 recomposition은 파괴가 아니다 |
| Android View (Preview용) | `AndroidView`의 `factory` | `factory`는 그 호출부가 Composition에 처음 들어올 때 1회 실행되고, 이후 recomposition에는 재사용된다. 그 호출부가 구조적으로 제거됐다가 다시 들어오면(예: `when` 분기 변경) `factory`가 다시 실행돼 새 View가 생긴다. **(Android/Compose API Contract)** |
| Surface (View가 그리는 대상) | Android 윈도우 시스템 | View 객체 자체와 별개의 lifetime을 가진다 — 같은 View 인스턴스가 살아있어도, 윈도우 가시성 변화(예: 백그라운드 전환) 등으로 Surface가 파괴됐다가 나중에 다시 만들어질 수 있다. 이게 `surfaceCreated`/`surfaceChanged`/`surfaceDestroyed`가 View 자체의 attach/detach와 별도로 존재하는 이유다. **(Android API Contract)** |
| EGL Context | `GLSurfaceView`의 EGL 관리 코드. Renderer는 생성 후 `onSurfaceCreated`로 통보받는다 | 출력 EGL Surface가 사라지면 current 연결이 해제될 수 있다. Context 보존 여부는 별개이며 `preserveEGLContextOnPause(true)`도 보존을 보장하지 않는다. GL Thread 종료/Context loss 시 재생성이 필요하다. **(Android/EGL API Contract)** |
| SurfaceTexture / OES 텍스처 | Renderer가 `SurfaceBinding`으로 관리한다. OES 텍스처는 생성한 EGL Context에 속한다 | Context 소멸과 `SurfaceTexture.release()`는 별개다. 현재 구현은 Context 재생성 시 새 SurfaceTexture를 만들고, 이전 SurfaceTexture는 CameraX 사용 완료와 Renderer 접근 종료 후 해제한다. **(Implementation Decision, Android/OpenGL API Contract)** |
| CameraX SurfaceRequest | CameraX | 이전에 공급한 Surface가 무효화되면(예: 그 Surface가 더 이상 유효한 렌더 대상이 아니게 됨) CameraX가 같은 `SurfaceProvider`에 새 요청을 보낸다. 현재 앱의 무효화·새 요청 처리 경로는 §4에서 다룬다. **(Android/CameraX API Contract / Implementation Decision — `01` §4와 이 문서 §4)** |
| CameraX UseCase | 바인딩한 `LifecycleOwner` | `LifecycleOwner`가 destroy되면 자동 unbind된다. 그 외엔 앱이 명시적으로 `unbind()`하거나 `bindToLifecycle()`을 다시 호출할 때만 바뀐다. **(Android/CameraX API Contract — CameraX의 핵심 기능)** |
| Camera device | OS/CameraX 내부 세션 관리 | 어떤 UseCase도 그 카메라를 필요로 하지 않게 되면 닫힌다. 앱이 직접 열고 닫지 않는다 |

## 3. 이벤트 전파 매트릭스

| 이벤트 | 강제로 유발하는 것 | 강제하지 않는 것 |
|---|---|---|
| Recomposition (구조 변경 없음) | 없음 | View/Surface/EGL/SurfaceTexture/CameraX 전부 그대로 |
| Portrait Window가 유지된 상태의 물리적 기기 회전 | UI content rotation state 갱신, `ImageCapture.targetRotation` 갱신 | Window 크기, PreviewSlot, View/Surface/EGL/SurfaceTexture, `Preview.targetRotation`은 그대로 |
| 실제 Window 크기/display rotation 변경 | Compose remeasure, adaptive 배치, `Preview.targetRotation` 갱신 | 구조적으로 같은 `AndroidView`의 factory 재실행이나 CameraX 전체 rebind를 강제하지 않는다 |
| `AndroidView` 노드가 구조적으로 제거됐다 다시 추가됨(예: `04` §6의 `Column`↔`Row` 전환이 실제로 이렇게 동작한다면) | View 재생성(factory 재실행) → 새 Surface → EGL Context 재생성 → SurfaceTexture 재생성 → CameraX에 새 Surface 재공급 필요 | 새 View의 provider를 연결해야 한다. View 재생성과 UseCase·ViewModel의 수명은 구분한다 |
| View는 살아있는데 Surface만 파괴됨(예: 백그라운드 전환) | 출력 EGL Surface/current 연결을 잃을 수 있다. Context까지 소멸한 경우에만 새 GL 자원/Surface 재공급이 필요하다 | View, Composition과 보존된 EGL Context의 파괴를 강제하지 않는다 |
| CameraX UseCase를 명시적으로 다시 bind | 새 `SurfaceRequest` 발생 → Surface 재공급 필요 | View/EGL/SurfaceTexture를 강제로 재생성하지 않는다 — 현재 Renderer는 요청별 새 SurfaceBinding을 공급한다 |
| `LifecycleOwner` destroy | CameraX 자동 unbind → 남은 UseCase가 없으면 Camera device도 닫힘 | — |
| 구성 변경에 따른 Activity 재생성 | Composition·View·프리뷰 GL 자원이 재생성되고 새 LifecycleOwner/provider에 카메라를 연결해야 한다 | ViewModelStore가 유지되면 CameraViewModel과 그 안의 Controller·CaptureProcessor는 유지된다 |
| 프로세스 종료 후 재시작 | 인메모리 객체와 coroutine은 유지되지 않으며 앱 초기화부터 다시 시작한다 | DataStore 설정과 MediaStore에 저장을 마친 사진은 보존된다. 진행 중 저장의 완료는 보장하지 않는다 |

## 4. SurfaceProvider 소유권 — GL View와 Renderer

초기에는 수명이 긴 `CameraController`가 provider를 소유하는 방안을 검토했다. 실제로는 SurfaceTexture·OES 자원을 만들고 복구하는 렌더링 계층이 최신 자원을 알고 있으므로, GL View가 provider를 소유하도록 정했다. Controller가 렌더러 내부 자원을 조회하는 결합을 피하려는 선택이다. **(Implementation Decision)**

- `CameraGlSurfaceView`는 Renderer와 `CameraSurfaceProvider(renderer)`를 만들고, Controller는 전달받은 `Preview.SurfaceProvider`에만 의존한다.
- provider는 GL View와 함께 생성된다. View 재생성 뒤에는 새 provider를 연결하며, provider가 ViewModel 수명 동안 같은 인스턴스일 필요는 없다.
- Renderer는 SurfaceRequest를 보관하고 GL 콜백에서 SurfaceBinding을 만든다. 기존 pending 요청을 교체할 때는 `willNotProvideSurface()`로 끝내고, 사용 중 binding은 `invalidate()`로 새 요청을 유도한다.
- 퇴역한 binding의 Surface·SurfaceTexture는 CameraX 사용 완료 뒤 해제한다. GL texture 삭제는 별도 관리하며, 늦은 콜백도 원래 binding에만 적용한다(§8).

구체 코드: [CameraGlSurfaceView](../app/src/main/java/com/sseo/feelcamera/camera/gl/CameraGlSurfaceView.kt) · [CameraRenderer](../app/src/main/java/com/sseo/feelcamera/camera/gl/CameraRenderer.kt)

## 5. `onFrameAvailable` → GL 스레드 전달

- `SurfaceTexture.OnFrameAvailableListener.onFrameAvailable()`은 임의의 스레드(대개 GL 렌더 스레드가 아님)에서 호출된다. **(Android API Contract)**
- `updateTexImage()`는 그 텍스처를 소유한 EGL Context가 current인 스레드에서만 호출 가능하다(`02` §2에서 이미 확정). 따라서 "새 프레임이 왔다"는 신호를 GL 스레드로 안전하게 넘기는 절차가 필요하다 — 일반적인 패턴은 `GLSurfaceView.requestRender()`(임의 스레드에서 호출 가능하도록 문서화돼 있다)로 GL 스레드에 렌더 요청을 걸고, 실제 `updateTexImage()` 호출은 GL 스레드에서 실행되는 렌더 콜백 안에서 수행하는 것이다. **(Android/OpenGL API Contract — 일반적으로 쓰이는 패턴)**
- 현재 Renderer는 `resourceLock` 안에서 binding identity를 확인하고 `frameAvailable`을 갱신한다. `requestRender()`로 draw를 요청한 뒤 GL 스레드의 `onDrawFrame()`에서 프레임을 소비한다. **(Implementation Decision)**

## 6. Layout mode 전환과의 상호작용 (`04` §6/§7에 답함)

초기 `Column`↔`Row` 전환에서 `AndroidView` 재생성과 프리뷰 공백을 관찰했고, 현재는 하나의 `Box` 트리 안에서 배치만 바꾼다(`04` §6). 구조적 View 제거와 실제 Window 재측정은 §3 매트릭스에서 별도 이벤트다. 고정 트리는 불필요한 재생성을 줄이며, 시스템이 Surface/EGL을 재생성하는 경우에는 §8의 복구·해제 규칙을 따른다.

## 7. 추가 검증 범위

provider의 소유자와 요청 무효화·해제 경로는 §4·§8의 코드로 확정했다. 다른 제조사/GPU, 작은 창·멀티윈도우·폴더블에서 프리뷰와 촬영이 복구되는 범위는 추가 검증한다. 현재 검증 근거는 [검증 문서](portfolio/validation.md)에 모은다. **(Verify First)**

## 8. Preview binding 해제 규칙 (Step 11/12)

- `queueEvent()`는 GL Thread 실행만 보장하며 실행 시점의 current EGL Context를 보장하지 않는다. 기존 에뮬레이터 로그에서 사용 완료 콜백의 `detachFromGLContext()`가 `invalid current EGLDisplay`로 실패했다. Context 세대와 current 여부를 구분한다. **(Android API Contract, Observed: 2026-09-22)**
- `SurfaceBinding`은 request, Surface, SurfaceTexture, OES texture ID, 생성 Context 세대를 함께 보관한다. CameraX 사용 완료는 retirement 이전에도 도착할 수 있다. 완료 처리와 View 폐기 순서에 의존하지 않고 해제는 한 번만 수행한다. **(Implementation Decision)**
- Renderer의 Surface 요청/완료/프레임 콜백과 draw, detach, 최종 폐기 상태는 하나의 lock으로 직렬화한다. CameraX 완료 콜백은 종료될 수 있는 GL event queue를 거치지 않고, lock 안에서 해당 binding을 draw 대상에서 제외한 뒤 Surface/SurfaceTexture를 해제한다. CameraX가 사용하는 Surface는 View가 종료돼도 사용 완료 전에는 해제하지 않는다. **(Implementation Decision, CameraX API Contract)**
- OES 삭제는 Android 자원 해제와 분리한다. `detachFromGLContext()` 대신 `(textureId, generation)` 삭제 항목을 보관하고, 소유 Context가 current인 Renderer 콜백에서 `glDeleteTextures()`를 호출한다. 같은 Context의 일시적인 current 해제는 삭제 보류, 새 Context 세대 또는 GL Thread 종료로 확인된 Context 소멸은 이전 삭제 항목 폐기로 처리한다. 현재 Preview/Capture Context는 GL 자원을 공유하지 않는다. **(Implementation Decision, Android/OpenGL API Contract)**
- Surface 요청 수신 자체는 GL 명령을 실행하지 않는다. 요청을 보관하고 렌더를 요청한 뒤 `onSurfaceCreated`/`onDrawFrame`에서 OES/SurfaceTexture를 생성해 공급한다. 삭제 대기 처리는 `onDrawFrame`의 프리뷰 부재에 따른 조기 반환보다 먼저 수행한다. 늦은 옛 프레임/변환 콜백은 binding/request identity로 무시한다. **(Implementation Decision)**
- `CameraGlSurfaceView.onDetachedFromWindow()`는 superclass가 GL Thread 종료를 완료한 뒤 Renderer에 Context 소멸을 알린다. 일시 detach는 재연결을 허용하고, Compose `AndroidView.onRelease`는 최종 폐기를 알린다. 최종 폐기 이후의 Surface 요청은 거절하고, 미공급 요청도 `willNotProvideSurface()`로 끝낸다. 완료 콜백은 View 수명 이후에도 Android 자원을 해제한다. **(Implementation Decision, Android/Compose API Contract)**
- 검증: 같은 Context에서 반복 Surface 교체, pause 중 완료, Context 재생성 뒤 옛 완료 콜백, GL Thread 종료 뒤 완료, 초기 Context 준비 전 요청을 확인한다. Home/복귀·분할 화면 전환 후 프리뷰 및 촬영 복구도 확인한다. 반복 횟수/실기기 결과는 실제 검증 결과로 기록하며 코드만으로 완료로 간주하지 않는다. **(Verify First)**

2026-09-23 검증 결과: Android 에뮬레이터에서 `CameraRendererLifecycleTest` 7개 통과. 실제 EGL/OES/SurfaceRequest를 사용해 current 연결 해제 후 완료, 같은 Context의 20회 교체 및 옛 texture 삭제, 새 Context의 동일 숫자 ID 보호, 최종 폐기 후 CameraX 사용 종료, 초기 요청/취소, 별도 완료 스레드와 draw의 20회 교차 실행을 확인했다. 앱 UI에서는 Home→복귀 3회, 화면 꺼짐/켜짐 1회, 전체 화면→분할 화면→전체 화면 1회 및 분할 화면에서 사진 1장 저장을 확인했다. 동일 PID에서 `View detached → Renderer closed → Surface completed → Binding released(retired=0)`가 관찰됐으며 해당 실행에서 크래시는 없었다. **(Observed)**

이 결과는 재생성 방지나 모든 기기의 메모리 누수 부재를 뜻하지 않는다. 분할 화면 전환에서 View/EGL 재생성이 실제로 발생했고, 작은 창에서 프리뷰 일부가 패널에 가려지는 배치도 관찰됐다. 실기기 반복 검증과 작은 창의 Preview 크기/crop 조정은 남아 있다. **(Verify First)**

근거: [SurfaceTexture release/detach 계약](https://developer.android.com/reference/android/graphics/SurfaceTexture), [GLSurfaceView 구현](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/opengl/java/android/opengl/GLSurfaceView.java), [SurfaceRequest](https://developer.android.com/reference/androidx/camera/core/SurfaceRequest).

## 9. Capture offscreen GL Context 소유권

- Capture GL 처리기는 Preview `GLSurfaceView`와 별개의 단일 `ExecutorService`를 소유하고 이를 `ExecutorCoroutineDispatcher`로 감싼다. coroutine은 구조화된 촬영 흐름과 오류 전파를 담당하지만, 모든 EGL/GL 호출은 이 Dispatcher의 동일한 물리 스레드에서만 실행한다. `Dispatchers.IO`나 `limitedParallelism(1)`을 EGL 소유 스레드 대용으로 사용하지 않으며 Preview Context의 texture/program ID를 전달받지 않는다. **(Implementation Decision, Android/EGL/Kotlin Coroutines API Contract)**
- Capture 처리기는 별도의 fire-and-forget `CoroutineScope`를 만들지 않고 main-safe suspend 함수를 제공해 호출자의 구조화된 동시성에 참여한다. Camera destination에서는 `viewModelScope`가 호출 수명을 소유한다. ViewModel이 정리된 뒤에도 저장을 보장해야 한다면 작업 수명과 원본 보존 방식을 별도로 설계한다. 설정 화면 이동처럼 Camera destination이 back stack에 남는 경우는 ViewModel 정리와 구분한다. **(Implementation Decision)**
- Capture Context는 첫 촬영 처리 시 lazy 초기화하고 Camera destination/ViewModel 범위에서 재사용한다. ViewModel이 정리되면 새 작업을 막고, GL 자원을 그 전용 스레드에서 먼저 해제한 뒤 Dispatcher/Executor를 종료한다. **(Implementation Decision)**
- Capture Context loss의 감지·세션 폐기·재생성·재시도는 아직 구현하지 않았다. 현재 EGL/GL 오류는 촬영 오류로 전달하며, Preview의 Context 재생성 대응과 Capture의 복구 범위를 구분한다. **(Current Code / Later)**
- LUT/program처럼 작은 장기 자원과 촬영별 full-resolution 자원을 구분한다. 두 LUT와 program은 Context 동안 유지하지만 source texture, output texture/FBO attachment, readback buffer, decoded/output bitmap은 각 촬영의 성공·실패가 끝나는 즉시 해제하며 다음 촬영까지 보관하지 않는다. **(Implementation Decision)**
- `ImageProxy`는 이미지 변환·렌더링·저장을 감싼 `processAndSave()`의 바깥 `finally`에서 닫는다. GL 초기화·렌더링·MediaStore 저장 중 어느 단계가 실패해도 `ImageProxy`와 촬영별 GL/CPU 자원이 남지 않아야 한다. **(Android/CameraX API Contract, Implementation Decision)**

## 10. 이 문서에서 다루지 않는 것

- Camera sensor rotation, `targetRotation` → `02` §3
- LUT/셰이더 실행 경로 자체 → `03`
- Layout mode 판정 로직 자체 → `04`
- 구현 순서 → `06-implementation-roadmap.md`
