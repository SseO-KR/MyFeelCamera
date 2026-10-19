# 01. Architecture

이 문서는 `00-product-requirements.md`에 확정된 내용만을 요구사항 근거로 삼는다. 전체 그림만 설명하고 세부는 하위 문서로 위임한다:

- 카메라 / Surface 파이프라인 세부 → `02-camera-pipeline.md`
- LUT / 렌더링 셰이더 세부(실행 경로가 실제로 정해진 뒤) → `03-rendering-and-lut.md`
- 적응형 레이아웃 세부 → `04-adaptive-layout.md`
- 레이어별 lifecycle / ownership 세부 → `05-lifecycle-and-resource-ownership.md`
- 단계별 구현 순서 → `06-implementation-roadmap.md`

## 1. 모듈 구조

**지금은 단일 `:app` 모듈, 패키지 단위로만 책임을 나눈다.**

`00`에는 module 구조에 대한 요구사항이 없다 — 학습 목표 목록(CameraX, Compose, lifecycle, Surface, OpenGL ES, SurfaceTexture, OES texture, shader, LUT, adaptive layout)에도 module 경계 자체는 포함돼 있지 않다. 이 프로젝트는 재사용 대상(다른 앱이 이 코드를 가져다 쓰는 시나리오)이 아직 없는 1인 프로젝트이므로, `:domain`/`:data`를 별도 Gradle 모듈로 미리 쪼개는 비용(빌드 그래프, DI 컴포넌트 경계 관리)을 지금 낼 이유가 없다.

```
com.newfeelcamera/
├─ camera/    CameraX 실행과 카메라 Preview/Filter 파이프라인 — UI를 모른다
│  ├─ gl/     카메라 전용 GLSurfaceView, SurfaceProvider, Renderer, shader 로딩
│  ├─ filter/ Film/LUT 모델 — Android 프레임워크 비의존
│  └─ capture/ ImageCapture 입력, 오프스크린 GL 처리, MediaStore 저장
├─ data/      설정·최근 사진 Repository, 분석 계약
├─ ui/        Compose Screen, ViewModel, navigation
│  └─ camera/layout/  측정한 너비·높이 → CameraLayoutMode
└─ application/ FeelCameraApp, 앱 초기화
```

Hilt constructor injection과 필요한 Module로 객체를 연결한다(§9).

경계는 지금은 "모듈"이 아니라 "패키지 + import 규율"로 지킨다. `camera/filter/`는 `android.*`를 import하지 않고, `camera/gl/`과 `camera/capture/`는 Compose·디자인·레이아웃을 import하지 않는다. UI는 `camera/gl/`의 Preview View를 사용하지만 GL/CameraX 구현은 UI를 역으로 알지 않는다. 실제로 재사용 대상이 생기거나 빌드 시간이 문제가 되면 `:domain`/`:data`로 승격하는 것은 기계적인 리팩터링이라 나중으로 미룬다(Later).

## 2. 카메라 아키텍처 — 객체 책임과 Lifetime

`00` §8에 따라 `PreviewView`를 쓰지 않으므로, Preview 렌더링을 위한 View/Renderer를 직접 소유해야 한다. 아래는 그 결과로 필요한 객체들의 책임과 lifetime이다.

| 객체 | 책임 | Lifetime |
|---|---|---|
| `CameraRoute` (Composable) | ViewModel 구독, 권한 요청 결과와 UI event 연결 | Navigation destination의 `NavBackStackEntry`를 따름 |
| `CameraScreen` (Composable) | UI 트리 구성, 상태 표시, event callback 전달 | Composition을 따름 — 화면 이탈/복귀마다 재생성될 수 있다 |
| `CameraViewModel` | UI State/Intent/Effect 매핑 | Camera navigation destination 스코프 — Composition 재생성에서 살아남고 destination이 back stack에서 제거되면 정리된다 |
| `CameraController` | `ProcessCameraProvider` 획득, UseCase(Preview/ImageCapture) 구성, bind/unbind | ViewModel과 동일 스코프로 유지 — Composable이 재생성될 때마다 새로 만들지 않는다 |
| Preview 렌더링 View (예: GLSurfaceView 기반) | OES `SurfaceTexture` 보유, GL 렌더링 | `AndroidView` 팩토리가 관리 — View 시스템 lifecycle을 따르며 Composition보다 하위 개념 |
| `Renderer` | 셰이더 컴파일, draw call | 소유 View의 EGL context lifetime을 따름 |
| `CameraSurfaceProvider` | Renderer에 SurfaceRequest 전달 | `CameraGlSurfaceView`가 소유하며 View와 함께 생성 |
| Capture 파이프라인 | 촬영 시점의 `FilmPreset`을 snapshot으로 고정하고, `ImageCapture`에서 원본 `ImageProxy` 획득과 MediaStore 저장을 조정 | `CameraController`와 같은 Camera destination/ViewModel 스코프 |
| `CaptureProcessor` | 촬영 이미지를 2D texture로 업로드하고 별도 offscreen EGL/FBO에서 LUT를 적용 | Camera destination 범위의 단일 Executor 기반 CoroutineDispatcher/EGL Context를 소유하고 lazy 초기화·명시적 해제. 외부에는 main-safe suspend 작업을 제공하고, 입력·출력 image buffer는 촬영 호출 단위로 해제 |

이 표의 핵심은 **각 객체의 lifetime을 Composable 트리의 lifetime과 동일시하지 않는다**는 것이다. `CameraController`는 ViewModel 범위를 따르고, `SurfaceProvider`는 GL View 범위를 따른다. 일반 recomposition에는 View가 유지되지만 View가 재생성되면 새 provider를 Controller에 연결한다. provider 자체를 ViewModel 수명까지 연장할 필요는 없다.

## 3. Lifecycle 레이어 분리 — 왜 나누는가

`00` §9가 못박은 레이어 목록(Composition / Android View / Surface / EGL Context / SurfaceTexture / CameraX SurfaceRequest / CameraX UseCase / Camera device)을 나누는 이유는 구체적인 실패 시나리오 때문이다:

- 같은 `AndroidView` 호출부의 일반 recomposition은 factory를 재호출하지 않는다. 호출부가 Composition에서 제거되면 View가 해제될 수 있으므로 recomposition과 Composition 이탈을 구분한다.
- View의 `Surface`가 파괴·재생성돼도(예: 가시성 변경) EGL context가 항상 같이 죽는 것은 아니다 — 하지만 항상 살아남는다고 가정할 수도 없다. "EGL context가 항상 살아있다"를 전제로 코드를 짜지 않는다.
- EGL context가 실제로 죽으면 그 안에서 만든 GL 텍스처 id(OES 텍스처 포함)는 전부 무효가 된다 → `SurfaceTexture`도 다시 만들어야 한다 → 그 `SurfaceTexture`를 감싼 `Surface`를 CameraX에 다시 공급해야 한다.
- 이때 "다시 공급"이 UseCase를 통째로 다시 bind하는 것인지, 기존 `SurfaceRequest` 계약 안에서 새 `Surface`만 물리는 것인지가 §4의 주제다.

레이어별 이벤트 전파 매트릭스는 `05-lifecycle-and-resource-ownership.md`에서 다룬다.

## 4. CameraX Surface 공급 — API 계약과 설계 결정을 구분한다

**Android/CameraX API Contract**: `Preview.SurfaceProvider`는 `onSurfaceRequested(SurfaceRequest)` 하나를 갖는 인터페이스다. CameraX는 바인딩된 `Preview` UseCase의 lifetime 동안 이 메서드를 한 번 이상 호출할 수 있다(예: 이전 `SurfaceRequest`가 무효화된 경우) — `SurfaceProvider`는 1회성 콜백이 아니라 여러 요청을 받을 수 있는 대상으로 API가 정의해 둔 것이다. 이건 API가 이렇게 생겼다는 사실이지, 우리가 정한 것이 아니다.

**Fix Now (이 사실 위에서 우리가 내리는 설계 결정)**:
- `CameraController`는 `Preview.SurfaceProvider` 추상화에 의존한다 — CameraX가 Surface를 요구하는 지점을 이 인터페이스 하나로 받는다.
- Surface lifecycle과 CameraX binding을 불필요하게 결합하지 않는다 — "Surface를 새로 공급해야 한다"는 사실이 자동으로 "UseCase를 다시 bind해야 한다"를 뜻하도록 코드를 짜지 않는다. 이건 지금 지킬 원칙이지, 이미 증명된 메커니즘이 아니다.

```
CameraController
      │ Preview.setSurfaceProvider()
      ▼
SurfaceProvider 구현체
      │  onSurfaceRequested(request) — API 계약상 1회 이상 호출 가능
      ├─ SurfaceRequest #1
      ├─ SurfaceRequest #2 (예: 카메라 입력 Surface가 무효화된 뒤 재공급)
      └─ ...
```

**현재 구현 (Implementation Decision):**

- `CameraGlSurfaceView`가 `CameraSurfaceProvider(renderer)`를 생성한다. Controller는 전달받은 provider를 Preview에 연결한다.
- Renderer는 요청을 보관하고 GL 콜백에서 새 OES texture·SurfaceTexture·Surface를 생성한다. Context 교체나 binding 퇴역 때 `SurfaceRequest.invalidate()`로 새 요청을 유도한다.
- 교체 중인 이전 Surface는 CameraX 사용 완료 후 해제하며, GL texture 삭제는 Context 세대와 current 여부로 판단한다. 구체적인 해제 규칙은 `05` §8을 따른다.
- 어떤 기기·화면 전환에서 전체 rebind가 필요한지는 실행 환경에 따라 추가 검증한다. provider의 객체 수명만으로 모든 복구를 보장하지 않는다.

## 5. Layout과 Camera Rotation 분리

`00` §6의 원칙을 타입 레벨로 옮긴다 — 다음을 같은 개념으로 다루지 않는다:

```
Window / layout의 실제 너비·높이         →  CameraLayoutMode를 결정한다        (§6)
Display rotation                          →  adaptive fallback의 ControlEdgeOrder와 Preview targetRotation을 결정한다
Device target rotation − display rotation →  개별 control content의 화면상 회전각을 결정한다
기기 방향 관찰값(양자화)                   →  device target rotation과 ImageCapture targetRotation을 결정한다
CameraX TransformationInfo                →  GL texture transform 정책을 결정한다
SurfaceTexture transform + 위 정책        →  Preview 최종 GL transform을 결정한다
```

Phone UX는 Activity에 Portrait orientation을 요청한다. 요청이 적용돼 Window가 유지되는 동안 Portrait의 Start/Preview/End 앱 좌표 배치를 그대로 유지하고, 읽기 방향이 있는 개별 control content만 기기 방향을 따라 회전한다. Portrait 요청은 멀티 윈도우·large screen·제조사 동작까지 고정하는 보장이 아니므로, 실제 Window가 바뀌면 `CameraLayoutMode`와 `ControlEdgeOrder`가 adaptive fallback을 담당한다.

`CameraLayoutMode`는 현재 측정한 너비·높이만 입력으로 받는다. posture는 아직 구독하지 않는다. `ControlEdgeOrder`와 Preview target rotation은 observable display rotation을 읽고, ImageCapture target rotation은 기기 방향 listener를 읽는다. `UseCase.snapToSurfaceRotation()` 결과와 display rotation은 같은 Surface 회전 규약이다. control content는 두 값을 각도로 바꾼 뒤 `device target rotation − display rotation`을 정규화한다. 기기를 따라 Window도 회전하면 추가 UI 회전은 0이다. UI 배치와 CameraX/GL transform 사이에는 코드 의존성을 만들지 않는다.

`ImageCapture.targetRotation`은 `OrientationEventListener`의 양자화된 기기 방향을 사용한다. `Preview.targetRotation`은 화면 좌표계에 맞도록 실제 display rotation을 사용한다. Portrait 요청이 적용된 Phone에서는 display가 유지되어 Preview도 유지되지만 Capture와 control content는 물리적 기기 방향을 계속 반영한다. 각 신호는 독립적으로 구독하고 수명 관리한다.

## 6. 적응형 레이아웃 — 확장 가능한 타입, 아직 확정하지 않는 구체 형태

`00` §6은 "레이아웃 타입이 Portrait 외 모드를 배제하지 않아야 한다"를 요구했다 — 그 모드들의 구체적 형태(이름·개수·payload)까지 지금 확정하라는 뜻은 아니다. 지금 확정하는 것과 미루는 것을 분리한다.

**Fix Now**: `CameraLayoutMode`는 sealed interface로 정의하고, 지금 아는 두 모드(`Portrait`, `Landscape`)만 구체 타입으로 넣는다.

```kotlin
sealed interface CameraLayoutMode {
    data object Portrait : CameraLayoutMode
    data object Landscape : CameraLayoutMode
    // 추가 adaptive/foldable 모드는 여기 추가된다 — 이름·개수·payload는 Verify First
}
```

sealed interface를 쓰는 이유가 이 결정의 근거다: 새 subtype이 추가되면, 이 타입을 `when`으로 분기하던 기존 호출부는 (`else` 분기가 없는 한) 컴파일 에러로 그 사실을 알게 된다. "이 타입은 앞으로 더 늘어난다"는 사실을 지금 구조에 반영하면서도, 늘어날 항목의 구체적 형태는 지금 결정하지 않아도 된다는 뜻이다 — `00` §6의 요구와 "Tabletop/Book을 지금 확정하지 않는다"는 제약을 동시에 만족한다.

**Verify First (지금 정하지 않는 것)**:
- Foldable/adaptive 상태를 몇 개의 case로 나눌지, 이름을 무엇으로 할지(`Tabletop`/`Book`이 후보일 뿐 확정 아님).
- 각 case가 어떤 payload(hinge 위치, 방향, 그 외 정보)를 가져야 하는지.
- 이 판단은 `androidx.window`의 `WindowInfoTracker`/`FoldingFeature` 이벤트를 실제로 관찰하고, 그 상태에서 UI가 실제로 무엇을 보여줘야 하는지 확인한 뒤 내린다(`04-adaptive-layout.md`). P0/P1 범위 밖이라 지금 급하지 않다(`00` §4, §7).

```kotlin
@Composable
fun CameraAdaptiveLayout(
    layoutMode: CameraLayoutMode,
    startControls: @Composable () -> Unit,
    preview: @Composable () -> Unit,
    endControls: @Composable () -> Unit,
)
```

Child composable 트리는 `layoutMode`와 무관하게 항상 동일하게 유지한다(`00` §6) — `when`으로 트리 자체를 스왑하지 않는다. Custom `Layout`(measure/place)이 실제로 필요해지는 지점까지는 표준 Compose primitive(`Row`/`Column`/`Box` 조합)로 버틴다. 미리 만들지 않는다.

초기에는 Portrait부터 구현했고, 현재 고정 `Box` 트리에서 Portrait/Landscape 배치를 모두 처리한다. `Tabletop`/`Book` 타입과 TODO는 두지 않았다. 실제 자세 이벤트와 필요한 UI가 확인되면 해당 타입과 배치를 함께 추가한다.

현재 고정 3-region(StartControls/Preview/EndControls)은 표준 `Box`로 구현할 수 있어 별도 Adaptive scaffold를 도입하지 않았다. `androidx.window`의 자세 이벤트 구독도 아직 연결하지 않았다. 구체 구현은 [CameraScreen](../app/src/main/java/com/newfeelcamera/ui/camera/CameraScreen.kt)과 [CameraLayoutMode](../app/src/main/java/com/newfeelcamera/ui/camera/layout/CameraLayoutMode.kt)를 따른다.

## 7. Filter / LUT 아키텍처 — 공유 입력과 분리된 실행 경로

`00` §10을 그대로 옮긴다: 공유해야 하는 것은 **입력**이지 **실행 방식**이 아니다.

```
CameraUiState.selectedFilm (단일 source of truth)
      │
      ├── Preview snapshot
      │     └── GLSurfaceView EGL Context + OES texture + preview fragment shader
      │
      └── Shutter 시점 snapshot
            └── ImageProxy + 별도 offscreen EGL Context + 2D texture + capture fragment shader
```

**P0 Implementation Decision**: Preview와 Capture는 같은 `FilmPreset` 값과 그 값이 가리키는 동일한 LUT asset, LUT 해석 규칙, 색보정 의미를 공유한다. 다만 EGL Context, GL program, texture ID, framebuffer 같은 실행 자원은 공유하지 않는다. Preview는 화면의 `GLSurfaceView` Context에서 연속 프레임을 처리하고, Capture는 `camera/capture/`의 단일 Executor 기반 CoroutineDispatcher가 소유한 별도 offscreen Context에서 셔터 1회의 이미지를 처리한다. 코루틴은 촬영→처리→저장의 구조화된 흐름과 오류 전파를 담당하고, 전용 Executor는 EGL/GL 호출의 물리 스레드 고정을 담당한다. 이 결정의 렌더링 근거와 아직 검증할 차이는 `03` §6~§7에 둔다.

`CameraUiState.selectedFilm`만 선택 상태의 권위 있는 원본이다. Preview Renderer가 draw를 위해 보관하는 값과 Capture가 셔터 순간 전달받는 값은 이 원본에서 파생된 작업용 snapshot이며, 독립적으로 변경되는 또 다른 source of truth가 아니다. Capture가 진행되는 동안 UI 선택이 바뀌더라도 이미 시작된 사진은 셔터 순간의 snapshot을 끝까지 사용한다.

설정의 `startFilm`은 DataStore에 저장하는 **다음 Camera 진입의 초기값**이며 촬영 중 선택된 Film의 또 다른 원본이 아니다. CameraViewModel은 설정을 처음 읽을 때 초기 선택에 반영하고, 설정 화면에서 `startFilm`이 변경되면 현재 선택에도 한 번 반영한다. Camera 화면에서 Film을 바꾸는 동작은 `startFilm`을 저장하지 않는다. `showGrid`는 UI Preview 오버레이만 제어하며 GL/CameraX와 Capture 결과에 전달하지 않는다. **(Product Requirement: `00` §4 / Implementation Decision)**

## 8. Filter 모델 확장성

현재 [FilmPreset](../app/src/main/java/com/newfeelcamera/camera/filter/FilmPreset.kt)은 LUT 자산 경로를 가진 `WARM`·`DUSK` enum이다. 별도의 `FilmFilter`/`FilterParams` 추상화는 사용하지 않는다.

P0는 이 리스트가 정확히 2개다(`00` §3) — 런타임 필터 조합/블렌딩 파이프라인은 만들지 않는다. 목록을 늘리는 것은 P1이다(`00` §4).

"현재 선택된 Film"은 이 목록과 별개의 상태다 — 어떤 `FilmPreset`이 지금 활성인지를 가리키는 값이며, `00` §1/§10의 source of truth가 바로 이 값이다. 이 상태는 UI State(`CameraViewModel`, §2)가 들고 있고, Preview와 Capture는 §7의 규칙대로 작업용 snapshot만 전달받는다.

## 9. DI — Hilt constructor injection부터 사용한다

`CameraController`처럼 Hilt가 생성할 수 있는 클래스는 `@Inject constructor`와 `@ApplicationContext`로 주입한다. Module은 `ProcessCameraProvider`/Executor처럼 생성자를 직접 호출할 수 없는 객체를 제공하거나, 인터페이스 구현을 교체해야 할 때만 추가한다. Controller는 camera session 상태를 가지므로 `@Singleton`으로 만들지 않고, `CameraViewModel` 생성자에 주입된 인스턴스를 해당 화면 범위에서 사용한다.

## 10. 현재 결정과 후속 검토

| 항목 | 현재 상태 | 근거·후속 범위 |
|---|---|---|
| minSdk | 29로 확정 | `00` §2, `app/build.gradle.kts` |
| SurfaceProvider 소유권 | GL View 소유, Renderer가 binding 수명 관리 | §4, `05` §4·§8 |
| DI 프레임워크·scope | Hilt, Controller는 CameraViewModel 범위에서 사용 | §9 |
| `Tabletop`/`Book` | 자세별 타입·UI 보류 | 실제 자세 이벤트와 UI 요구 확인 후 결정 |
| Capture Context loss | 자동 복구 미구현 | `05` §9에서 후속 범위로 구분 |
