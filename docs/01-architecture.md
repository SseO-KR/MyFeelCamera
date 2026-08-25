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
com.<app>/
├─ camera/    CameraX 세팅, Surface/SurfaceTexture 소유 — UI를 모른다
├─ gl/        GLSurfaceView(또는 대응 View), Renderer, shader 로딩 — 레이아웃을 모른다
├─ filter/    Filter/LUT 모델 — 이상적으로는 Android 프레임워크 비의존
├─ capture/   촬영 트리거·저장
├─ layout/    window size/posture → CameraLayoutMode 판정 (순수 함수에 가깝게)
├─ ui/        Compose Screen, ViewModel
└─ di/        DI 배선 (프레임워크 선택은 §9)
```

경계는 지금은 "모듈"이 아니라 "패키지 + import 규율"로 지킨다 — 예를 들어 `filter/`는 `android.*`를 import하지 않는다, `gl/`은 `layout/`을 import하지 않는다 같은 규칙. 실제로 재사용 대상이 생기거나 빌드 시간이 문제가 되면 `:domain`/`:data`로 승격하는 것은 기계적인 리팩터링이라 나중으로 미룬다(Later).

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
| `SurfaceProvider` 구현체 | CameraX에 카메라 입력 `Surface`를 공급 | §4 참고 — 정확한 안정성 수준은 아직 미정 |
| Capture 파이프라인 | 촬영 트리거, 원본 이미지 획득 | 거의 stateless — ViewModel 스코프에서 주입받아 사용 |
| Capture 측 filter 적용기(가칭) | Preview와 같은 filter 파라미터를 입력받아 캡처 결과에 적용 | 호출 단위 — 실행 방식은 §7에서 미정으로 남김 |

이 표의 핵심은 **각 객체의 lifetime을 Composable 트리의 lifetime과 동일시하지 않는다**는 것이다. 특히 `CameraController`/`SurfaceProvider`는 `CameraScreen`이 재구성되어도 살아남아야 한다 — 그래야 `00` §9의 "재생성돼도 복구" 원칙을 카메라 세션 레벨에서 지킬 수 있다.

## 3. Lifecycle 레이어 분리 — 왜 나누는가

`00` §9가 못박은 레이어 목록(Composition / Android View / Surface / EGL Context / SurfaceTexture / CameraX SurfaceRequest / CameraX UseCase / Camera device)을 나누는 이유는 구체적인 실패 시나리오 때문이다:

- Compose recomposition은 `AndroidView` factory를 재호출하지 않는다(factory는 최초 1회) → View lifetime은 Composition보다 길게 유지될 수 있다.
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

**Verify First (아직 열린 질문 — 답을 안다고 가정하지 않는다)**:
- 이 `SurfaceProvider` 구현체 인스턴스를 얼마나 오래 살려둘지(어느 스코프가 맞는지)는 정해지지 않았다.
- 같은 provider 인스턴스가 Surface 재생성을 실제로 어떤 방식으로 흡수하는지 — 새 `SurfaceTexture`/`Surface`를 만들어 기존 또는 새 `SurfaceRequest`에 연결하는 구체적인 코드 경로 — 는 아직 없다. 구현하며 찾는다.
- 실제로 어떤 조건에서 UseCase 전체 rebind가 필요해지는지(EGL context 소실, 해상도 변경 등은 후보일 뿐 확정된 목록이 아니다)는 실기기 로그로만 확인 가능하다.

이 세 질문에 대한 답이 나오기 전까지는 "SurfaceProvider가 Surface 재생성을 매끄럽게 흡수한다"는 문장을 이미 그렇게 동작하는 것처럼 쓰지 않는다.

## 5. Layout과 Camera Rotation 분리

`00` §6의 원칙을 타입 레벨로 옮긴다 — 다음을 같은 개념으로 다루지 않는다:

```
Window / layout 크기, foldable posture   →  CameraLayoutMode를 결정한다        (§6)
Display rotation                          →  ControlEdgeOrder를 결정한다 (UI의 Start/End 위치)
Display rotation                          →  CameraX targetRotation을 결정한다
CameraX TransformationInfo                →  GL texture transform(crop/rotate/mirror)을 결정한다
```

`CameraLayoutMode`는 window size + posture만 입력으로 받는다. `ControlEdgeOrder`와 `targetRotation`은 각자 `Display.rotation`(또는 그에 준하는 리스너)을 읽을 수 있지만, 서로의 값을 계산하지 않는다. UI order와 CameraX/GL transform 사이에도 코드 의존성을 만들지 않는다.

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

**P0는 `Portrait` 분기의 measure/place만 채운다.** `Landscape`/`Tabletop`/`Book`은 타입은 존재하되 구현은 `TODO()`로 남긴다 — `00` §3, §4가 요구한 "구조는 처음부터, 구현은 순서대로"를 여기서 구체화한 것이다.

**Material3 Adaptive 라이브러리(`SupportingPaneScaffold` 등)는 채택하지 않는다.** 그 라이브러리는 list-detail 내비게이션 UI를 위해 설계된 것이라, 고정 3-region(StartControls/Preview/EndControls) 카메라 레이아웃엔 맞지 않는다. `androidx.window`(`WindowInfoTracker`, `FoldingFeature`)는 posture 감지에만 쓰고, 레이아웃 자체는 위 스케치처럼 직접 만든다.

## 7. Filter / LUT 아키텍처 — 공유 지점과 미정 지점

`00` §10을 그대로 옮긴다: 공유해야 하는 것은 **입력**이지 **실행 방식**이 아니다.

```
FilmFilter / FilterParams (filter/ 패키지, 단일 source of truth)
      │
      ├── Preview 실행 경로   ← 아직 미정
      └── Capture 실행 경로   ← 아직 미정
```

**지금 확정하는 것(Fix Now)**: Preview 경로와 Capture 경로는 이 모델의 **같은 인스턴스**를 참조한다. 이 모델은 적용 중인 필터를 완전히 표현한다(LUT asset 참조 포함).

**지금 확정하지 않는 것(Verify First)**: 두 경로가 셰이더 코드/GL 파이프라인을 공유하는지, 완전히 독립적으로 구현되는지. 이건 다음 두 가지가 먼저 나와야 판단 가능하다 — (1) LUT 실물 파일의 포맷(`00` §11, 아직 미도착), (2) Preview 쪽 기본 shader 파이프라인을 실제로 구현해 본 경험. 두 조건이 갖춰진 뒤 결정하고, 그 결과는 `03-rendering-and-lut.md`에 기록한다.

## 8. Filter 모델 확장성

```kotlin
data class FilmFilter(
    val id: String,
    val displayName: String,
    val params: FilterParams,
)
```

P0는 이 리스트가 정확히 2개다(`00` §3) — 런타임 필터 조합/블렌딩 파이프라인은 만들지 않는다. 목록을 늘리는 것은 P1이다(`00` §4).

"현재 선택된 Film"은 이 목록과 별개의 상태다 — 어떤 `FilmFilter`가 지금 활성인지를 가리키는 값이며, `00` §1/§10의 source of truth가 바로 이 값이다. 이 상태는 UI State(`CameraViewModel`, §2)가 들고 있고, Preview 렌더러와 Capture 측 filter 적용기(§2)는 둘 다 이 값을 읽기만 한다 — 각자 자기만의 "현재 필터" 사본을 갖지 않는다.

## 9. DI — Hilt constructor injection부터 사용한다

`CameraController`처럼 Hilt가 생성할 수 있는 클래스는 `@Inject constructor`와 `@ApplicationContext`로 주입한다. Module은 `ProcessCameraProvider`/Executor처럼 생성자를 직접 호출할 수 없는 객체를 제공하거나, 인터페이스 구현을 교체해야 할 때만 추가한다. Controller는 camera session 상태를 가지므로 `@Singleton`으로 만들지 않고, `CameraViewModel` 생성자에 주입된 인스턴스를 해당 화면 범위에서 사용한다.

## 10. 알려진 오픈 아이템 요약

| 항목 | 분류 | 확정 위치(예정) |
|---|---|---|
| minSdk | TBD — skeleton 생성 전 확정 | `00` §2 |
| SurfaceProvider 재사용 필요 여부·정도 | Verify First | `05-lifecycle-and-resource-ownership.md` |
| Preview/Capture 셰이더·GL 파이프라인 공유 여부 | Verify First | `03-rendering-and-lut.md` |
| LUT 포맷/차원/ordering/GPU representation | 파일 도착 후 결정 | `03-rendering-and-lut.md` |
| `Tabletop`/`Book`의 구체적 배치 계산 | Verify First (P1) | `04-adaptive-layout.md` |
| DI 프레임워크·scope | Verify First | 구현 시점 |
