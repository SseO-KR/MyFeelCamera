# 00. Product Requirements

현재 코드 기준은 1.0.0 (1)이다. P0/P1은 초기 개발 순서를 설명하며, 현재 기능·빌드 범위는 [README](../README.md)와 아래 지원 범위를 따른다. 초기 계획에서 달라진 결정은 해당 절에 반영한다.

## 1. 목적

Android용 필름 카메라 앱을 만든다. 이 프로젝트는 두 목표를 동등한 우선순위로 갖는다.

1. 실제로 안정적으로 동작하는 필름 카메라 앱을 완성한다.
2. CameraX, Jetpack Compose, Android lifecycle, Surface, OpenGL ES, SurfaceTexture, OES texture, shader, LUT 처리, adaptive layout을 구현하고 코드를 분석하며 학습한다.

초기에는 AI의 설명·골격·리뷰를 받으며 핵심 코드를 직접 작성했다. 이후 구현 속도와 학습 방식을 조정해 AI와 동작하는 구현을 먼저 만들고, 코드 흐름을 따라가며 모르는 개념과 대안을 학습하는 방식으로 전환했다. 구현·수정·테스트 지원 범위와 사례는 [AI 활용 문서](portfolio/ai-collaboration.md)에 정리한다.

### 핵심 불변식 (Core Invariant)

> 셔터를 누를 때 Preview에서 본 LUT 필터 효과와 저장 사진의 필터 효과는 시각적으로 최대한 일치해야 한다.

"최대한 일치"는 픽셀 단위로 동일한 바이트를 요구하지 않는다. 초기에는 Preview와 Capture의 실행 방식 결정을 미루고 **입력**의 일관성을 먼저 정했다. 현재는 각자의 GL Context와 셰이더 진입점에서 같은 필름과 LUT를 사용하는 경로를 구현했다(§10):

- 필름 선택의 원본 상태는 `CameraUiState.selectedFilm`으로 두고, Preview와 Capture는 같은 `FilmPreset` 정의와 LUT asset을 사용한다.
- Preview는 현재 선택을 반영하고, Capture는 셔터 순간의 선택을 snapshot으로 고정해 저장까지 사용한다. Capture가 진행되는 동안 UI 선택을 바꿔도 이미 시작한 사진의 필름은 바뀌지 않는다.

## 2. 지원 범위

| 항목 | 값 | 비고 |
|---|---|---|
| minSdk | 29 (Android 10) | MediaStore 저장에서 API 28 이하의 저장소 권한 분기를 추가하지 않는 선택 |
| compileSdk / targetSdk | 37 / 37 | 현재 [빌드 설정](../app/build.gradle.kts) 기준 |
| 그래픽 | OpenGL ES 3.0 필수 | Manifest의 하드웨어 요구 조건 |
| 화면 폼팩터 | Phone 중심, 실제 창 크기에 따른 Portrait/Landscape 배치 | Foldable 자세별 UI는 후속 범위이며 큰 화면 검증 범위는 [검증 문서](portfolio/validation.md) 참고 |
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

초기 확장 목록이다. 현재는 Zoom·Flash·중앙 AF/AE·최근 사진·볼륨 셔터·설정 영속화가 연결돼 있다. 추가 필름과 폴더블 자세별 UI, 성능 튜닝은 후속 범위다.

- 실제 창 크기에 따른 Portrait/Landscape 배치는 구현했다. Foldable의 Tabletop/Book 등 추가 모드는 자세 이벤트와 UI 요구를 관찰한 뒤 타입과 배치를 함께 추가한다(§6).
- 기본 Zoom / Flash / 중앙 고정 AF·AE
- 카메라 화면에는 이 앱에서 저장한 가장 최근 사진의 썸네일을 표시하고, 누르면 해당 사진을 갤러리 앱에서 연다. 저장한 사진이 없으면 갤러리 버튼은 비활성화한다.
- 필터를 여러 개로 확장, 필터 선택 UI 고도화
- 성능 수치 목표 설정 및 튜닝(§12)
- 설정값 영속화
- 설정 화면은 카메라 화면에서 열고 뒤로 돌아올 수 있어야 한다. Phone에서는 설정 화면도 Camera와 같은 Portrait 요청을 유지해 화면 이동만으로 Window 방향이 바뀌지 않도록 한다. 복귀 시 카메라 Preview가 정상적으로 다시 표시되어야 한다. 첫 단계에서는 화면 이동과 방향 정책을 연결하고, 볼륨 버튼 동작·격자 표시·시작 필름의 설정값 저장은 이어지는 단계에서 구현한다.
- Camera 화면이 활성화된 동안 볼륨 ↑/↓ 버튼을 셔터로 사용한다. Settings에서는 시스템 음량 조절을 유지하며, 화면 셔터와 동일한 촬영 경로를 사용한다.
- 사진 저장이 성공하면 촬영을 막지 않는 짧은 안내 문구를 표시하고, 실패하면 기존 촬영 오류 메시지를 사용자에게 보여준다.
- 설정 화면에서 시작 필름(Warm/Dusk)과 프리뷰 격자 표시 여부를 변경하고 앱 재실행 후에도 유지한다. 시작 필름은 카메라 화면의 초기 선택값이며, 카메라 화면에서 일시적으로 필름을 바꾸는 동작은 설정값을 덮어쓰지 않는다. 격자 설정은 Preview 위 UI 오버레이만 제어하고 저장 사진에는 영향을 주지 않는다.
- **v1 초기 배포 정책:** 무료 다운로드, 촬영 횟수 제한 없음, 광고 없음, 인앱결제 없음. 화면·볼륨 키 셔터 모두 구매 상태나 과거의 무료 촬영 카운터에 의존하지 않는다. 국내외 배포 국가 목록은 출시 전에 확정한다.
- 배포용 Release AAB에는 Firebase Analytics·Crashlytics SDK를 기본 포함해 앱 사용과 오류를 파악한다. `firebaseEnabled` 빌드 옵션은 사용하지 않으며, Firebase 설정 파일이 없으면 Release 빌드를 실패시킨다. 데이터 수집 방식은 사용자 선택, 개인정보처리방침, Play 데이터 보안 신고와 일치시킨다. **(사용자 요청, 2026-10-09)**
- 최초 실행에서 사용 통계와 오류 보고를 각각 선택하며, 기본 선택은 모두 꺼짐이다. 선택하지 않아도 촬영 기능을 사용할 수 있고 설정에서 변경할 수 있다. Firebase는 둘 중 하나를 선택한 뒤 초기화한다. Analytics 철회는 즉시 적용하고 Crashlytics 철회는 SDK 계약에 따라 다음 앱 실행부터 적용한다. **(글로벌 출시 개인정보 정책 / Firebase API Contract, 2026-10-09)**
- 기존 24장 한도와 일회성 무제한 구매는 초기 배포에서 사용하지 않는다. 유료화 재검토 시 기존 무료 사용자 경험, 판매 국가·세금, 결제·복원·환불 정책을 새로 확정한다. 이전 설계와 남겨 둔 Billing 구현은 [일회성 무제한 구매 문서](features/one-time-unlimited-purchase.md)에 기록한다.
- GitHub에 소스를 공개한다. README 안내 문구와 공개 라이선스는 출시 전에 별도로 확정한다.

## 5. Later (지금 구현을 막지 않는 범위에서 뒤로 미룸)

- 유료화와 관련된 계정·서버 기반 구매 검증·사용량 관리, 온보딩 플로우. v1에는 촬영 횟수 제한이 없다.
- RAW, manual exposure, manual focus, 고급 color management
- 추가 암전·필름 감기 인터랙션. 기본 셔터음과 on/off 설정은 구현했다.
- 범용 필터 체인, 런타임 필터 강도 슬라이더, 커스텀 필터 에디터
- grain/vignette 등 LUT 외 필름 효과의 제품 채택 여부 — 제공된 shader는 지원하지만 P0 요구사항은 아니다
- CameraX/GL 계측 테스트의 기기·시나리오 범위 확대. 현재 생명주기 7개와 회전·비율 5개 테스트가 있다.
- 모든 OEM별 카메라 edge case 대응, 과도한 performance optimization

## 6. Orientation / Adaptive 정책

**아키텍처는 처음부터 Portrait / Landscape / Large screen / Foldable 전부를 대응 가능한 구조로 설계한다.** "Portrait 전용 구조를 먼저 만들고 나중에 adaptive하게 리팩터링한다"는 방식은 명시적으로 피한다. 구체적으로:

- `CameraLayoutMode`는 `Portrait`와 `Landscape`를 갖는 sealed interface다. 현재 두 배치를 구현했으며, Tabletop/Book 같은 미확정 타입을 TODO로 미리 만들지 않는다. 추가 모드는 실제 자세 이벤트와 UI 요구를 확인한 뒤 확장한다.
- UI를 구성하는 Composable 트리는 모드에 따라 통째로 스왑되지 않고, 하나의 트리를 유지한 채 measure/place만 모드별로 달라지는 구조를 전제로 한다. (세부 타입/Composable 설계는 `01-architecture.md`에서 다룬다.)
- 현재 Camera와 Settings가 같은 `MainActivity`를 사용하므로 Phone에서는 Activity의 Portrait orientation을 요청한다. 설정 이동 시에도 방향 요청을 바꾸지 않아 Window 축 교환에 따른 전체 remeasure, Preview 크기 변경과 control 재배치의 체감 지연을 피한다. 기본 카메라처럼 촬영 프레임은 유지하고 읽기 방향이 있는 content만 기기 방향을 따라 회전시킨다. 이 요청은 모든 환경에서 Window 방향을 보장하는 아키텍처 불변식이 아니다. 큰 화면·폴드·멀티윈도우·제조사 정책 등으로 요청이 적용되지 않아 실제 Window가 바뀌면 Camera와 Settings 모두 실제 크기/방향에 대응해야 한다.
- Portrait 요청이 적용돼 Window가 유지되는 동안 StartControls는 앱 좌표상 상단, Preview는 중앙, EndControls는 하단에 고정한다. 기기를 오른쪽으로 돌리면 고정된 하단 EndControls가 사용자 기준 왼쪽에, 왼쪽으로 돌리면 오른쪽에, 180°에서는 위쪽에 보인다. 이 효과를 만들기 위해 Rail이나 Preview를 기기 방향에 따라 재배치하지 않는다.
- Rail 배경과 PreviewSlot은 회전시키지 않는다. 버튼 아이콘, Film 정보, Preview overlay text처럼 읽기 방향이 있는 개별 콘텐츠만 실제 기기 방향을 따라 회전한다. `OrientationEventListener`의 물리 각도와 CameraX target rotation은 반대 방향 규약이므로, UI 각도는 실제 display rotation과 양자화된 device target rotation의 규약을 변환해 합성한다.
- Window/layout 신호, display rotation, 물리적 기기 방향은 별개다. 현재 실제 Window 너비·높이는 `CameraLayoutMode`를, display rotation은 adaptive fallback의 UI 좌표계와 `Preview.targetRotation`을 결정한다. `ImageCapture.targetRotation`은 기기 방향 관찰값을 `Surface.ROTATION_*`으로 양자화한 신호를 사용한다. Preview와 ImageCapture가 항상 같은 회전 source를 쓴다고 가정하지 않는다.

**카메라/LUT P0 검증은 Portrait부터 시작한다.** 다만 현재 사용자 요구에 따라 3-region UI의 방향별 배치는 PreviewView 단계부터 구현·검증한다. CameraX `targetRotation`과 GL texture transform은 UI layout과 별도의 카메라 회전 단계에서 구현·검증한다.

## 7. Foldable 정책

Foldable posture를 도입한다면 presentation/layout input으로 취급한다. 현재는 힌지 자세를 구독하지 않으며, 창 크기에 따른 두 배치만 사용한다. Tabletop/Book의 타입·입력·배치는 실제 이벤트와 필요한 UI를 관찰한 뒤 추가한다. **(Later / Verify First)**

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
- 필름 모델과 LUT asset 정의를 공유하고, 선택 상태의 원본은 `CameraUiState.selectedFilm`으로 둔다.
- Preview는 현재 선택을 사용하고, Capture는 셔터 순간의 선택을 작업용 snapshot으로 전달받아 저장까지 유지한다.
- 필름 모델은 LUT asset 참조를 포함해 "어떤 필터를 적용하는가"를 표현해야 한다.

**구현하며 확정한 것 (Implementation Decision):**
- Preview는 OES 입력, Capture는 Bitmap에서 올린 2D texture 입력을 처리하므로 셰이더 진입점을 나눈다.
- Preview의 GLSurfaceView와 Capture의 전용 스레드가 각각 GL Context·자원을 소유하고 같은 LUT 자산과 색보정 규칙을 사용한다.

두 경로는 **동일한 필름 선택에 동일한 LUT와 색보정 규칙을 적용**하도록 구성한다. 실제 입력과 실행 시점이 다르므로 시각적 일치 여부는 별도로 검증하고, 두 셰이더의 공통 색보정 코드가 달라지지 않도록 함께 검토한다.

선택을 바꾸면 Preview와 다음 Capture에 반영하며, 이미 진행 중인 Capture는 셔터 순간의 선택을 유지한다.

## 11. LUT 리소스

제공된 WARM·DUSK LUT는 `app/src/main/assets/lut/`에 있다. 각 파일은 98,304바이트의 32×32×32 RGB 데이터이며 현재 셰이더는 3D texture로 샘플링한다. 파일 형식과 GPU 표현을 같은 요구로 취급하지 않으며, 분석 근거는 [렌더링·LUT 설계](03-rendering-and-lut.md)에 둔다.

## 12. 성능 목표

지금 수치를 정하지 않는다(Later). 실측 없는 숫자는 근거 없는 정밀도다 — P0가 Portrait에서 실기기 동작을 마친 뒤, 실측 기반으로 정한다. 방향성만 명시한다: Preview는 사용자가 버벅임을 느끼지 않는 수준으로 부드러워야 하고, 촬영 후 저장까지의 지연은 사용자가 "멈췄다"고 느끼지 않는 선이어야 한다 — 둘 다 구체적 수치가 아니라 실기기 확인 대상이다.

## 13. 명시적으로 하지 않을 것 (Non-goals, P0 기준)

아래는 초기 P0에서 범위를 좁힐 때의 목록이다. 현재 구현 범위는 §4와 README를 따른다.

- Landscape / Large screen / Foldable의 실제 레이아웃 구현(아키텍처 자리는 마련하되 구현은 P1)
- 결제 / Entitlement / 무료 장수 제한, 온보딩 플로우
- RAW, 수동 노출/포커스
- 전면 카메라 전환 UI, 부가 카메라 기능(Zoom/Flash/AF)
- 범용 필터 체인, 런타임 필터 강도 슬라이더, 커스텀 필터 에디터
- grain/vignette 등 LUT 외 필름 효과의 제품 채택 여부 — 제공된 shader는 지원하지만 P0 요구사항은 아니다
- CameraX/GL 계층에 대한 자동화된 계측 테스트 하네스 — 로그 기반 수동 검증 우선
- 모든 OEM별 카메라 edge case 대응
- 성능 수치 목표 설정(§12)
