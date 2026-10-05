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
| CameraX SurfaceRequest | CameraX | 이전에 공급한 Surface가 무효화되면(예: 그 Surface가 더 이상 유효한 렌더 대상이 아니게 됨) CameraX가 같은 `SurfaceProvider`에 새 요청을 보낸다. 정확히 어떤 앱 측 신호가 "무효화"를 트리거하는지는 §7에서 다룬다. **(Android/CameraX API Contract — 일반 계약은 `01` §4에서 확인, 세부는 Verify First)** |
| CameraX UseCase | 바인딩한 `LifecycleOwner` | `LifecycleOwner`가 destroy되면 자동 unbind된다. 그 외엔 앱이 명시적으로 `unbind()`하거나 `bindToLifecycle()`을 다시 호출할 때만 바뀐다. **(Android/CameraX API Contract — CameraX의 핵심 기능)** |
| Camera device | OS/CameraX 내부 세션 관리 | 어떤 UseCase도 그 카메라를 필요로 하지 않게 되면 닫힌다. 앱이 직접 열고 닫지 않는다 |

## 3. 이벤트 전파 매트릭스

| 이벤트 | 강제로 유발하는 것 | 강제하지 않는 것 |
|---|---|---|
| Recomposition (구조 변경 없음) | 없음 | View/Surface/EGL/SurfaceTexture/CameraX 전부 그대로 |
| Portrait Window가 유지된 상태의 물리적 기기 회전 | UI content rotation state 갱신, `ImageCapture.targetRotation` 갱신 | Window 크기, PreviewSlot, View/Surface/EGL/SurfaceTexture, `Preview.targetRotation`은 그대로 |
| 실제 Window 크기/display rotation 변경 | Compose remeasure, adaptive 배치, `Preview.targetRotation` 갱신 | 구조적으로 같은 `AndroidView`의 factory 재실행이나 CameraX 전체 rebind를 강제하지 않는다 |
| `AndroidView` 노드가 구조적으로 제거됐다 다시 추가됨(예: `04` §6의 `Column`↔`Row` 전환이 실제로 이렇게 동작한다면) | View 재생성(factory 재실행) → 새 Surface → EGL Context 재생성 → SurfaceTexture 재생성 → CameraX에 새 Surface 재공급 필요 | CameraX UseCase 전체 rebind는 강제하지 않는다 — `01` §4 Fix Now대로 같은 `SurfaceProvider`가 새 요청을 처리하면 된다 |
| View는 살아있는데 Surface만 파괴됨(예: 백그라운드 전환) | 출력 EGL Surface/current 연결을 잃을 수 있다. Context까지 소멸한 경우에만 새 GL 자원/Surface 재공급이 필요하다 | View, Composition과 보존된 EGL Context의 파괴를 강제하지 않는다 |
| CameraX UseCase를 명시적으로 다시 bind | 새 `SurfaceRequest` 발생 → Surface 재공급 필요 | View/EGL/SurfaceTexture를 강제로 재생성하지 않는다 — 기존에 유효한 SurfaceTexture 기반 Surface를 그대로 다시 공급할 수 있는지는 §7에서 다룬다 |
| `LifecycleOwner` destroy | CameraX 자동 unbind → 남은 UseCase가 없으면 Camera device도 닫힘 | — |
| Activity 완전 재생성(configChanges 없는 회전, 프로세스 재시작 등) | 전부 새로 만들어진다 — 새 Composition, 새 View, 새 Surface, 새 EGL, 새 SurfaceTexture, CameraX UseCase도 새 `LifecycleOwner`에 다시 bind해야 한다 | 이게 가장 단순한 케이스다 — 아무것도 살아남는 게 없어서 오히려 추론이 쉽다 |

## 4. SurfaceProvider 소유권 — 다시 열림

이전 판단("provider는 `CameraController`가 소유한다")을 재검토한다. CameraX API 계약은 `Preview.SurfaceProvider`라는 경계가 존재해야 한다는 것만 요구할 뿐, 그 구현체를 누가 소유해야 하는지는 강제하지 않는다. **(Android/CameraX API Contract — 경계의 존재만 계약, 소유자는 계약 밖)**

우리 구조에서는 `SurfaceTexture`/OES 텍스처를 실제로 만들고 재생성하는 쪽이 rendering 계층이다(§2 — EGL Context/`SurfaceTexture`의 소유자는 Renderer). Provider 소유권을 `CameraController`에 고정하면, `CameraController`가 "지금 rendering 계층의 최신 `SurfaceTexture`가 무엇인지"를 알아야 하는 역방향 결합이 생긴다 — `CameraController`가 CameraX 관심사에만 머물러야 한다는 `01` §2/§3의 책임 분리와 충돌할 수 있다.

**Fix Now (여기까지만)**:
- `CameraController`와 GL/View의 lifetime은 분리돼 있다(§2에서 이미 확정).
- `CameraController`는 `GLSurfaceView`/EGL/`SurfaceTexture`를 직접 소유하지 않는다.
- CameraX `Preview`와 실제 렌더링 surface 사이에는 `SurfaceProvider` 경계가 존재한다 — 이는 CameraX 쪽 계약이다.
- Surface lifecycle과 CameraX binding을 불필요하게 결합하지 않는다(`01` §4에서 이미 확정).

**Verify First로 이동**:
- `SurfaceProvider` 구현체의 실제 소유자 — `CameraController`, rendering 계층(Renderer/`GLSurfaceView`), 또는 둘 사이를 잇는 별도 adapter 객체 중 무엇인지.
- 그 provider 인스턴스의 lifetime.
- 새 `SurfaceTexture`가 생성됐을 때(예: View 재생성 이후) 그 provider가 어떻게 연결받는지의 구체적 메커니즘.
- `SurfaceTexture` 교체가 진행되는 동안 기존에 미해결 상태인 `SurfaceRequest`를 어떻게 처리하는지(그대로 두는지, 취소하는지, 새 요청을 기다리는지).

이 네 가지는 구현하며 실제 클래스 구조를 짜 볼 때 결정한다 — 지금 어느 한쪽으로 미리 정하면, 나중에 다른 쪽이 더 맞는 것으로 밝혀져도 그 결합을 되돌리는 비용이 든다.

## 5. `onFrameAvailable` → GL 스레드 전달

- `SurfaceTexture.OnFrameAvailableListener.onFrameAvailable()`은 임의의 스레드(대개 GL 렌더 스레드가 아님)에서 호출된다. **(Android API Contract)**
- `updateTexImage()`는 그 텍스처를 소유한 EGL Context가 current인 스레드에서만 호출 가능하다(`02` §2에서 이미 확정). 따라서 "새 프레임이 왔다"는 신호를 GL 스레드로 안전하게 넘기는 절차가 필요하다 — 일반적인 패턴은 `GLSurfaceView.requestRender()`(임의 스레드에서 호출 가능하도록 문서화돼 있다)로 GL 스레드에 렌더 요청을 걸고, 실제 `updateTexImage()` 호출은 GL 스레드에서 실행되는 렌더 콜백 안에서 수행하는 것이다. **(Android/OpenGL API Contract — 일반적으로 쓰이는 패턴)**
- 신호 전달에 쓸 정확한 동기화 방식(플래그를 어떻게 세우고 지우는지 등)은 여기서 코드로 제시하지 않는다 — `SurfaceTexture`/OES 스레딩은 이 프로젝트의 명시적 학습 목표다.

## 6. Layout mode 전환과의 상호작용 (`04` §6/§7에 답함)

`04` §6이 P1에서 검증하기로 한 것 — `Column`↔`Row` 전환이 Preview의 `AndroidView`를 재생성시키는지 — 은 §3 매트릭스의 두 번째 행("AndroidView 노드가 구조적으로 제거됐다 다시 추가됨")에 정확히 해당한다. 만약 그렇게 동작한다면, 그 뒤로 이어지는 View→Surface→EGL→SurfaceTexture→CameraX 재공급 연쇄는 §2/§3에서 이미 정의한 것과 동일하다 — layout mode 전환이라고 해서 새로운 종류의 전파 규칙이 필요하지 않다. `04` §6이 지정한 로깅 체크리스트(`AndroidView.factory`, attach/detach, `surfaceCreated`/`surfaceDestroyed`, EGL 재생성)는 이 매트릭스의 각 행을 실기기에서 확인하는 절차 그 자체다.

## 7. 아직 Verify First로 남는 것

- `SurfaceProvider`의 소유자·lifetime·연결 메커니즘·요청 처리 방식 (§4 — 이번에 Verify First로 재확장됨)
- CameraX가 이전과 "같은" `Surface` 객체(예: View는 안 죽고 EGL Context만 살아남아 SurfaceTexture가 그대로인 경우)를 새 `SurfaceRequest`에 다시 공급받아도 정상 동작하는지, 아니면 항상 새 Surface 객체를 기대하는지
- 앱이 CameraX에게 "지금 준 Surface가 무효해졌다"를 알리는 정확한 API — CameraX가 여러 `SurfaceRequest`를 보낼 수 있다는 것은 계약(§2)이지만, 그 트리거를 앱이 능동적으로 유발하는 정확한 방법은 구현 시점에 최신 CameraX 문서로 확인한다

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
- Capture 처리기는 별도의 fire-and-forget `CoroutineScope`를 만들지 않고 main-safe suspend 함수를 제공해 호출자의 구조화된 동시성에 참여한다. Camera destination에서는 `viewModelScope`가 호출 수명을 소유한다. 화면 범위를 넘겨 저장을 보장해야 하는 요구가 생기면 더 긴 외부 Scope를 별도 결정하며, 현재 P0에서는 미리 도입하지 않는다. **(Implementation Decision)**
- Capture Context는 첫 촬영 처리 시 lazy 초기화하고 Camera destination/ViewModel 범위에서 재사용한다. 화면 범위가 끝나면 새 작업을 막고, GL 자원을 그 전용 스레드에서 먼저 해제한 뒤 Dispatcher/Executor를 종료한다. Context loss가 확인되면 program/LUT texture/FBO를 새 Context에서 다시 만든다. **(Implementation Decision)**
- LUT/program처럼 작은 장기 자원과 촬영별 full-resolution 자원을 구분한다. 두 LUT와 program은 Context 동안 유지하지만 source texture, output texture/FBO attachment, readback buffer, decoded/output bitmap은 각 촬영의 성공·실패가 끝나는 즉시 해제하며 다음 촬영까지 보관하지 않는다. **(Implementation Decision)**
- `ImageProxy`는 필요한 bitmap과 `rotationDegrees`/`cropRect`를 확보한 뒤 `finally`에서 닫는다. GL 초기화·렌더링·MediaStore 저장 중 어느 단계가 실패해도 `ImageProxy`와 촬영별 GL/CPU 자원이 남지 않아야 한다. **(Android/CameraX API Contract, Implementation Decision)**

## 9. 이 문서에서 다루지 않는 것

- Camera sensor rotation, `targetRotation` → `02` §3
- LUT/셰이더 실행 경로 자체 → `03`
- Layout mode 판정 로직 자체 → `04`
- 구현 순서 → `06-implementation-roadmap.md`
