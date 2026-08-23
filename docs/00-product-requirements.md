# 00. Product Requirements

## 1. 목적

Android용 필름 카메라 앱을 만든다. 이 프로젝트는 두 목표를 동등한 우선순위로 갖는다.

1. 실제로 안정적으로 동작하는 필름 카메라 앱을 완성한다.
2. CameraX, Jetpack Compose, Android lifecycle, Surface, OpenGL ES, SurfaceTexture, OES texture, shader, LUT 처리, adaptive layout을 직접 구현하며 학습한다.

두 번째 목표 때문에 AI는 요구사항 정리·architecture 설계·책임 분리·module/package 구조·interface/class skeleton·함수 signature·TODO·구현 순서 제안·코드 리뷰까지만 담당한다. CameraX/OpenGL/Compose custom layout 등 핵심 구현은 AI가 대신 완성하지 않는다 — 이 원칙은 이 문서 이후 모든 설계 문서와 코드 골격에 적용된다.

### 핵심 불변식 (Core Invariant)

> 사용자가 실시간 Preview에서 본 LUT 필터 결과와, 실제 촬영해 저장한 사진의 필터 결과는 시각적으로 최대한 일치해야 한다.

"최대한 일치"는 픽셀 단위로 동일한 바이트를 요구하지 않는다. Preview와 Capture의 **실행 방식**(셰이더 구현을 공유하는지, 각각 독립된 파이프라인으로 구현하는지)은 지금 정하지 않는다 — 실험과 구현을 거치며 결정한다(§10). 지금 확정하는 것은 실행 방식이 아니라 **입력**이다:

- Preview와 Capture는 반드시 **같은 Filter/LUT source of truth**(같은 필터 파라미터 모델 인스턴스, 같은 LUT asset)를 참조해야 한다.
- 이 source of truth가 둘로 갈라지면(예: Preview용 파라미터와 Capture용 파라미터가 별도로 존재) 핵심 불변식을 지킬 방법이 없어지므로, 이것만은 지금 확정한다.

## 2. 지원 범위

| 항목 | 값 | 비고 |
|---|---|---|
| minSdk | TBD — 프로젝트 skeleton(Gradle build 파일) 생성 전 확정 | 지금 architecture 설계 자체를 막는 값은 아니다(주로 권한 처리 분기·일부 API 가용성에만 영향). 다만 Gradle build 파일은 구체적 숫자 없이 만들 수 없으므로 "Later"가 아니라 "다음 마일스톤(skeleton 생성) 전에 결정해야 하는 TBD"로 구분한다 |
| compileSdk / targetSdk | 빌드 시점 최신 stable | |
| 화면 폼팩터 | Phone / Foldable / Large screen(태블릿) / Multi-window(resized) | **아키텍처는 전부 대응 가능하게 설계한다 — §6** |
| 카메라 | 후면 카메라 전용 (확정) | P0는 후면 전용으로 확정한다. 전면 카메라는 현재 scope 밖 — 지원 여부, 전환 UI 모두 다루지 않는다 |

## 3. P0 기능

P0는 아래 루프가 **Portrait 한 방향에서** 실기기 검증 가능한 상태로 끝나는 것을 목표로 한다. 구현 순서가 Portrait부터 시작한다는 것이지, 그 외 방향을 architecture가 막는다는 뜻이 아니다 — §6.

1. 앱 실행 — 콜드 스타트, 카메라 권한 처리
2. 후면 카메라 Preview 표시 — CameraX `Preview` UseCase, OES `SurfaceTexture` + OpenGL 경로(§8, `PreviewView` 미사용)
3. 촬영 — `ImageCapture`, 셔터 트리거, 저장
4. Film 선택 — 2개의 LUT 기반 Film Preset을 제공하고, 사용자가 촬영 전 현재 적용할 Film을 선택·변경할 수 있다
5. 선택된 Film의 LUT 색보정을 Preview에 실시간 적용 — P0 필수 효과는 LUT로 한정한다(grain/vignette는 §5)
6. 같은 LUT 색보정을 Capture 결과에 적용한 뒤 저장 — 핵심 불변식(§1) 충족
7. Preview에서 본 결과와 저장된 결과를 실기기에서 나란히 비교해 일치를 확인
8. 화면 전환 후 복귀, Home↔Resume 복귀 시 Preview가 깨지지 않고 정상 표시(Portrait 안에서)

## 4. P1 이후

- Landscape / Large screen / Foldable(Tabletop, Book 등) 레이아웃의 실제 구현 — **아키텍처 자체는 P0부터 이 모드들을 배제하지 않는 구조로 만들어져 있어야 한다(§6). P1은 "타입/구조는 있는데 비어 있던 자리"를 채우는 작업이지, 새 구조를 만드는 작업이 아니다.**
- 기본 Zoom / Flash / 중앙 고정 AF·AE
- 필터를 여러 개로 확장, 필터 선택 UI 고도화
- 성능 수치 목표 설정 및 튜닝(§12)
- 카운터/설정 등 영속화

## 5. Later (지금 구현을 막지 않는 범위에서 뒤로 미룸)

- 결제 / Entitlement / 무료 장수 제한, 온보딩 플로우
- RAW, manual exposure, manual focus, 고급 color management
- 볼륨 버튼 셔터, 셔터음·암전·필름 감기 인터랙션
- 범용 필터 체인, 런타임 필터 강도 슬라이더, 커스텀 필터 에디터
- grain/vignette 등 LUT 외 필름 효과의 제품 채택 여부 — 제공된 shader는 지원하지만 P0 요구사항은 아니다
- CameraX/GL 계층에 대한 자동화된 계측(instrumented) 테스트 하네스 — 로그 기반 수동 검증을 우선한다
- 모든 OEM별 카메라 edge case 대응, 과도한 performance optimization

## 6. Orientation / Adaptive 정책

**아키텍처는 처음부터 Portrait / Landscape / Large screen / Foldable 전부를 대응 가능한 구조로 설계한다.** "Portrait 전용 구조를 먼저 만들고 나중에 adaptive하게 리팩터링한다"는 방식은 명시적으로 피한다. 구체적으로:

- 레이아웃을 표현하는 타입(모드 구분)은 처음부터 Portrait 외의 모드를 포함한 형태로 정의한다. 아직 구현하지 않는 모드는 타입 자리는 있되 내부 구현이 `TODO()`인 상태로 둔다 — 타입 자체가 없는 상태로 두지 않는다.
- UI를 구성하는 Composable 트리는 모드에 따라 통째로 스왑되지 않고, 하나의 트리를 유지한 채 measure/place만 모드별로 달라지는 구조를 전제로 한다. (세부 타입/Composable 설계는 `01-architecture.md`에서 다룬다.)
- 전 기기 방향 잠금 해제(`fullUser`)를 목표로 하되, Activity `configChanges`로 재생성을 막는 것을 정상 동작의 전제 조건으로 삼지 않는다 — "재생성되지 않는 구조"가 아니라 "재생성돼도 복구되는 구조"를 원칙으로 한다(§9).
- 카메라 UI의 기준 배치는 Portrait다: StartControls는 상단, Preview는 중앙, EndControls는 하단이다. 기기를 왼쪽으로 90° 돌리면 StartControls/EndControls는 각각 좌/우로, 오른쪽으로 90° 돌리면 각각 우/좌로 이동한다. 180°에서는 각각 하/상으로 이동한다. Preview는 항상 두 control 영역 사이의 남는 공간을 차지한다.
- Window/layout 신호(화면 크기, foldable posture), UI control의 display rotation, 카메라 sensor rotation 신호(`targetRotation`)는 별개다. display rotation은 UI의 Start/End 물리적 위치를 정할 수 있지만, 그 값으로 layout mode나 camera target rotation을 계산하지 않는다.

**카메라/LUT P0 검증은 Portrait부터 시작한다.** 다만 현재 사용자 요구에 따라 3-region UI의 방향별 배치는 PreviewView 단계부터 구현·검증한다. 이것은 CameraX `targetRotation` 또는 GL texture transform 구현을 앞당기지 않는다.

## 7. Foldable 정책

Foldable posture는 비즈니스 state가 아니라 presentation/layout input으로 취급한다. §6의 원칙을 그대로 따른다 — 아키텍처는 posture를 입력으로 받을 자리를 처음부터 갖고, 실제 Tabletop/Book 배치 로직은 P1 이후 실기기/에뮬레이터 관찰을 거쳐 구현한다(추측성 설계를 피하기 위해 — Verify First).

## 8. 기술 선택 — PreviewView를 사용하지 않는 이유

이 프로젝트는 CameraX `PreviewView`를 사용하지 않고, 카메라 프레임을 앱이 직접 만드는 `SurfaceTexture`(OES texture) 경유로 받아 OpenGL로 렌더링한다.

**이것은 Android 카메라 앱을 만드는 일반적인 정답이 아니다.** 대부분의 카메라 앱에는 `PreviewView`가 더 간단하고 검증된 선택이다. 이 프로젝트가 다른 경로를 택한 이유는 이 프로젝트에 고유하다:

1. **실시간 LUT 합성** — Preview에 LUT 필터를 실시간으로 입히려면 카메라가 그리는 원본 프레임에 GPU 셰이더로 직접 개입해야 한다. `PreviewView`는 이 지점을 앱에 노출하지 않는다.
2. **학습 목적** — 이 프로젝트의 명시적 목표(§1)에 OpenGL ES, `SurfaceTexture`, OES texture, shader가 포함되어 있다. `PreviewView`를 쓰면 이 학습 목표 자체가 성립하지 않는다.

이 결정은 두 이유 중 하나라도 없었다면 다시 검토했을 결정이다 — "항상 이렇게 해야 한다"는 일반 원칙으로 다른 문서에 인용하지 않는다.

## 9. Lifecycle 기본 원칙

다음 레이어를 하나로 뭉뚱그리지 않는다(각 레이어의 ownership 상세는 이후 lifecycle 문서에서 다룬다):

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

원칙: 한 레이어가 재생성되어도 다른 레이어까지 불필요하게 연쇄 재생성되지 않는 것이 이상적이지만, **"재생성 자체를 막는 것"보다 "재생성됐을 때 그 레이어만 정상 복구하는 것"을 항상 먼저 증명한다.**

## 10. Preview / Capture 필터 일관성 요구사항

§1 핵심 불변식을 구현 차원에서 다시 명시한다.

**지금 확정하는 것 (Fix Now):**
- 필터 파라미터를 표현하는 모델은 하나여야 하고, Preview와 Capture 양쪽이 그 **같은 인스턴스**를 참조해야 한다.
- 이 모델은 LUT asset 참조를 포함해 "어떤 필터가 적용 중인가"를 완전히 표현할 수 있어야 한다.

**지금 확정하지 않는 것 (Verify First — 실험 후 결정):**
- Preview와 Capture가 셰이더 구현/코드를 공유하는지, 각자 독립적으로 구현하는지.
- Capture가 Preview와 같은 GL 파이프라인을 재사용하는지, 다른 실행 방식을 쓰는지.

두 경로가 결과적으로 다른 코드로 구현되더라도, **같은 입력(같은 파라미터, 같은 LUT asset)에서 출발한다는 것**만으로 핵심 불변식을 지키는 데 필요한 최소 제약은 충족한다. 실행 방식의 통일 여부는 구현하면서 판단한다.

사용자가 촬영 전 Film을 선택·변경할 수 있다는 것(§3)은 이 요구사항을 구체화한다 — "선택된 Film"이 곧 이 source of truth이고, 선택이 바뀌면 그 변경은 Preview와 다음 Capture 양쪽에 동일하게 반영되어야 한다.

## 11. LUT 리소스

LUT는 이 프로젝트가 별도로 제공하는 제품 리소스다. 예정 위치: `app/src/main/assets/lut/`. P0는 2개의 LUT 파일로 시작한다(§3).

파일이 도착하기 전까지 다음은 전부 미정이다 — 기존 어떤 프로젝트의 LUT 처리 방식도 참고하지 않고, 실제 파일을 분석해 새로 판단한다:

- 포맷
- Dimension
- 1D/3D 여부
- `DOMAIN_MIN` / `DOMAIN_MAX`
- Data ordering
- GPU representation

## 12. 성능 목표

지금 수치를 정하지 않는다(Later). 실측 없는 숫자는 근거 없는 정밀도다 — P0가 Portrait에서 실기기 동작을 마친 뒤, 실측 기반으로 정한다. 방향성만 명시한다: Preview는 사용자가 버벅임을 느끼지 않는 수준으로 부드러워야 하고, 촬영 후 저장까지의 지연은 사용자가 "멈췄다"고 느끼지 않는 선이어야 한다 — 둘 다 구체적 수치가 아니라 실기기 확인 대상이다.

## 13. 명시적으로 하지 않을 것 (Non-goals, P0 기준)

- Landscape / Large screen / Foldable의 실제 레이아웃 구현(아키텍처 자리는 마련하되 구현은 P1)
- 결제 / Entitlement / 무료 장수 제한, 온보딩 플로우
- RAW, 수동 노출/포커스
- 전면 카메라 전환 UI, 부가 카메라 기능(Zoom/Flash/AF)
- 범용 필터 체인, 런타임 필터 강도 슬라이더, 커스텀 필터 에디터
- grain/vignette 등 LUT 외 필름 효과의 제품 채택 여부 — 제공된 shader는 지원하지만 P0 요구사항은 아니다
- CameraX/GL 계층에 대한 자동화된 계측 테스트 하네스 — 로그 기반 수동 검증 우선
- 모든 OEM별 카메라 edge case 대응
- 성능 수치 목표 설정(§12)
