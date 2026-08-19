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
| EGL Context | Renderer(`GLSurfaceView.Renderer` 관례상 `onSurfaceCreated`에서 생성) | 기본적으로 `surfaceDestroyed` 시 함께 해제된다. `preserveEGLContextOnPause(true)`는 이 해제 빈도를 줄이는 완화 수단이지 보장이 아니다. **(Android/EGL API Contract, `01` §3에서 이미 확정)** |
| SurfaceTexture(OES 텍스처 포함) | 그 텍스처를 만든 EGL Context | OES 텍스처 id는 자신을 만든 EGL Context 안에서만 유효하다 — Context가 죽으면 텍스처 id도 무효가 되고, `SurfaceTexture`도 다시 만들어야 한다. **(OpenGL API Contract — 텍스처는 컨텍스트 스코프다)** |
| CameraX SurfaceRequest | CameraX | 이전에 공급한 Surface가 무효화되면(예: 그 Surface가 더 이상 유효한 렌더 대상이 아니게 됨) CameraX가 같은 `SurfaceProvider`에 새 요청을 보낸다. 정확히 어떤 앱 측 신호가 "무효화"를 트리거하는지는 §7에서 다룬다. **(Android/CameraX API Contract — 일반 계약은 `01` §4에서 확인, 세부는 Verify First)** |
| CameraX UseCase | 바인딩한 `LifecycleOwner` | `LifecycleOwner`가 destroy되면 자동 unbind된다. 그 외엔 앱이 명시적으로 `unbind()`하거나 `bindToLifecycle()`을 다시 호출할 때만 바뀐다. **(Android/CameraX API Contract — CameraX의 핵심 기능)** |
| Camera device | OS/CameraX 내부 세션 관리 | 어떤 UseCase도 그 카메라를 필요로 하지 않게 되면 닫힌다. 앱이 직접 열고 닫지 않는다 |

## 3. 이벤트 전파 매트릭스

| 이벤트 | 강제로 유발하는 것 | 강제하지 않는 것 |
|---|---|---|
| Recomposition (구조 변경 없음) | 없음 | View/Surface/EGL/SurfaceTexture/CameraX 전부 그대로 |
| `AndroidView` 노드가 구조적으로 제거됐다 다시 추가됨(예: `04` §6의 `Column`↔`Row` 전환이 실제로 이렇게 동작한다면) | View 재생성(factory 재실행) → 새 Surface → EGL Context 재생성 → SurfaceTexture 재생성 → CameraX에 새 Surface 재공급 필요 | CameraX UseCase 전체 rebind는 강제하지 않는다 — `01` §4 Fix Now대로 같은 `SurfaceProvider`가 새 요청을 처리하면 된다 |
| View는 살아있는데 Surface만 파괴됨(예: 백그라운드 전환) | EGL Context 파괴(`preserveEGLContextOnPause` 없으면) → SurfaceTexture 무효 → 새 Surface 재공급 필요 | View 객체 자체, Composition은 그대로 |
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
- Capture 측이 자체 오프스크린 GL Context를 쓰기로 결정되면(`01` §7, `03` §6/§7 — 아직 미정) 그 Context의 소유권·lifetime은 이 문서가 다루지 않는다 — 결정된 뒤 별도로 다룬다

## 8. 이 문서에서 다루지 않는 것

- Camera sensor rotation, `targetRotation` → `02` §3
- LUT/셰이더 실행 경로 자체 → `03`
- Layout mode 판정 로직 자체 → `04`
- 구현 순서 → `06-implementation-roadmap.md`
