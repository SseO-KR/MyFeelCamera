# NewFeelCamera — 프로젝트 가이드

## 에뮬레이터 테스트 허가

이 프로젝트에서는 사용자가 **해당 작업에 대해 명시적으로 허가하기 전까지** Android 에뮬레이터 테스트를 진행하지 않는다. 에뮬레이터 실행·조작, 에뮬레이터 대상 ADB 명령, APK 설치·앱 실행, 스크린샷 및 에뮬레이터 체크리스트 수행도 포함한다. 코드 검토, 빌드, 정적 검사와 단위 테스트는 이 제한과 별개로 진행할 수 있다.

## 프로젝트 개요

필름 카메라 감성의 Android 카메라 앱. Kotlin + CameraX + OpenGL ES 3.0 + Jetpack Compose. `feelcamera`(sibling 프로젝트)의 이식이 아니라 요구사항부터 새로 판단한 clean-slate 프로젝트다.

이 프로젝트는 두 목표를 동등한 우선순위로 갖는다:

1. 실제로 안정적으로 동작하는 필름 카메라 앱을 완성한다.
2. 사용자가 CameraX, Compose, Android lifecycle, Surface, OpenGL ES, SurfaceTexture, OES texture, shader, LUT 처리, adaptive layout을 **구현과 코드 분석을 통해 학습**한다.

핵심 불변식: 사용자가 실시간 Preview에서 본 LUT 필터 결과와 실제 저장된 사진의 필터 결과는 시각적으로 최대한 일치해야 한다(`docs/00-product-requirements.md` §1).

---

## 문서 구조

설계는 전부 `docs/`에 있다. 코드/리뷰 작업 전에 관련 섹션을 먼저 확인한다 — 문서에 없는 것을 임의로 판단하지 않는다.

| 파일 | 역할 |
|---|---|
| `docs/00-product-requirements.md` | 제품 목표, P0 범위, orientation/foldable 정책, 핵심 불변식, non-goals |
| `docs/01-architecture.md` | 모듈/패키지 구조, 카메라 객체 책임·lifetime, SurfaceProvider, LayoutMode, Filter/LUT 아키텍처 |
| `docs/02-camera-pipeline.md` | CameraX UseCase 구성, Surface 공급 경로, rotation 신호, Capture 트리거 |
| `docs/03-rendering-and-lut.md` | LUT/셰이더 계층(Resource Contract → Rendering Contract → Current Shader Implementation), Preview/Capture 일치 A/B/C |
| `docs/04-adaptive-layout.md` | Portrait/Landscape 배치 메커니즘, window size/posture 신호, custom Layout 필요 여부 |
| `docs/05-lifecycle-and-resource-ownership.md` | 8개 레이어(Composition~Camera device) 소유권, 이벤트 전파 매트릭스 |
| `docs/06-implementation-roadmap.md` | 개발 당시 단계와 현재 구현 상태. 작업 범위는 사용자의 요청과 관련 단계의 책임을 기준으로 정한다 |
| `docs/features/one-time-unlimited-purchase.md` | v1에서 보류한 일회성 결제 Feature의 이전 설계와 재검토 항목 |
| `docs/features/firebase-monitoring.md` | Firebase Release 기본 포함, 사용자 동의와 과거 수집 검증 기록 |
| `new_feelcamera_filter_assets/` | 실제 LUT(`lut/*.bin`)·shader(`shader/*.glsl`) 리소스 원본 |

---

## AI 역할 경계

초기에는 코드 골격과 설명·리뷰를 제공하고 사용자가 핵심 코드를 직접 작성하는 멘토링 방식이었다. 이후 구현 속도와 학습 방식을 조정해, AI와 동작하는 구현을 먼저 만들고 사용자가 코드를 따라가며 모르는 개념을 학습하는 방식으로 바뀌었다. 변경 배경과 사례는 `docs/portfolio/ai-collaboration.md`에 기록한다.

AI는 요청한 범위에서 요구사항·대안 검토, 코드 구현·수정, 테스트와 문서 작성을 지원한다. 제안에는 선택 이유와 필요한 대안을 설명하고, 구현 후에는 사용자가 실행 흐름과 모르는 개념을 살펴볼 수 있도록 관련 코드와 검증 결과를 연결한다. 사용자가 직접 실습하는 학습 작업을 요청하면 그 작업의 구현 경계를 따르며, 요청 범위 밖의 TODO를 미리 완성하지 않는다.

---

## 핵심 협업 규칙

**1. 설계는 `docs/00~06`을 근거로 한다.** 개별 Feature의 세부는 해당 문서에서 연결한 `docs/features/` 문서를 함께 확인한다. 새 판단은 관련 섹션을 인용한다. 문서에 없는 결정은 문서를 먼저 갱신하고 코드에 반영한다 — 반대 순서로 하지 않는다.

**2. 구현과 학습을 함께 지원한다.** 요청한 CameraX/Compose/GL 구현도 지원하며, 대안·실행 흐름·수정 이유를 설명한다. 직접 실습을 위한 TODO는 해당 작업에서 정한 경계를 유지한다.

**3. 현재 Roadmap Step 밖의 코드를 미리 만들지 않는다.** 새 class/file/package는 그 책임이 실제로 필요해지는 Step에서 추가한다. "나중에 필요할 것 같아서"는 지금 만들지 않는 이유가 된다 — 만드는 이유가 되지 않는다.

**4. `Verify First` 항목을 임의로 설계 결정으로 승격하지 않는다.** `01`~`05`가 Verify First로 남긴 것(Foldable 구체 모드, grain/vignette 채택 여부, Capture Context loss 복구, 성능 측정 등)은 해당 Roadmap Step에서 실제 구현/로그로 확인하기 전까지 열어둔다. Verify First 문장을 이미 그렇게 동작하는 것처럼(선언형/invariant처럼) 쓰지 않는다 — 질문 형태를 유지한다.

**5. 새 문제 하나로 전체 아키텍처를 다시 뜯지 않는다.** 문제를 발견하면 **Fix Now**(지금 안 고치면 이후 핵심 구현을 다시 뜯어야 함) / **Verify First**(실기기·로그 확인 필요) / **Later**(현재 Step에 불필요)로 분류한다. Fix Now라도 관련 섹션만 좁게 고친다 — 문서/코드를 전면 재작성하지 않는다.

**6. 실제 로그/기기 동작을 추측보다 우선한다.** Surface/EGL/Camera lifecycle, Gradle/의존성 버전, layout 전환 등 실측 가능한 것은 실기기 로그·실제 빌드 결과로 확인한 뒤 판단한다(예: Gradle 골격 세팅 때 버전을 추측이 아니라 `maven-metadata.xml` 조회와 실제 `./gradlew` 에러로 확정한 것과 같은 방식).

---

## 문서 표기 규칙

`02`~`05`는 각 결정 뒤에 근거를 표시한다 — 새 문서/개정도 이 규칙을 따른다:

- **Product Requirement** — `docs/00`이 요구하는 것
- **Resource Contract** — 실제 제공된 asset(LUT/shader 파일 등)을 분석해 확인한 사실
- **Android/CameraX/OpenGL/Compose/Window API Contract** — 플랫폼·라이브러리 API가 그렇게 생겼다는 사실. 우리가 정한 게 아니다
- **Verify First** — 아직 답을 모른다. 질문 형태로 남기고 실기기/코드로 확인한다
- **Later** — 지금 Step에 필요 없다

하위 계층(예: Current Shader Implementation, Implementation Decision)의 사실은 상위 계층(Product Requirement 등)의 요구사항으로 역승격하지 않는다(`03` §5~§6 참고).

---

## 하지 않는 것

- `feelcamera`(sibling 프로젝트) 또는 다른 기존 Repository를 참고/이식하지 않는다 — architecture·code·LUT/shader 기법 전부. 이 프로젝트는 clean-slate이고, 겉보기에 비슷한 결론에 도달하더라도 근거는 이 프로젝트의 `docs/`와 Android/CameraX/OpenGL 자체 지식이어야 한다.
- 과설계 금지: 지금 실사용처가 없는 abstraction(범용 FilterEngine, Strategy 계층, 사용처 하나뿐인 UseCase/interface, 미래 확장을 이유로 한 계층)을 만들지 않는다.

---

## 현재 상태 (수시 갱신)

- 설계 문서 `00`~`06` 확정.
- 현재 코드 버전은 1.0.4 (5), minSdk는 29, compileSdk/targetSdk는 37이다. Gradle/Hilt 설정이 완료되어 있으며, `FeelCameraApp`은 `@HiltAndroidApp`, `MainActivity`는 `@AndroidEntryPoint`, `CameraController`는 `@Inject constructor`, `CameraViewModel`은 `@HiltViewModel`을 사용한다. KSP가 AGP 9 내장 Kotlin의 source-set DSL을 아직 완전히 지원하지 않아 `gradle.properties`에 `android.disallowKotlinSourceSets=false`(AGP 공식 과도기 escape hatch)가 있다.
- `CameraController`는 `Preview`와 `ImageCapture`를 함께 bind한다. `CameraScreen`은 고정된 `Box` 안에서 `CameraGlSurfaceView`를 유지하며, GL Preview는 `CameraSurfaceProvider` → OES `SurfaceTexture` → `CameraRenderer` 경로로 표시된다. Warm/Dusk LUT는 Preview EGL Context에 미리 업로드되고 `CameraUiState.selectedFilm` 변경에 따라 texture binding이 즉시 전환된다.
- **현재 출시 정책: v1은 무료·무제한 촬영, 광고 없음, 인앱결제 없음이다.** 이전 Step 19의 24장 한도와 Step 20의 구매 정책은 출시 경로에서 사용하지 않는다(`docs/00` §4, `docs/06`의 v1 무료 출시 전환).
- Billing·24장 한도 코드는 `app/src/deferredMonetization/`에 보존하며 기본 빌드에는 포함하지 않는다. Firebase Analytics·Crashlytics는 Release에 기본 포함하고 사용자 선택 후 수집한다. 설정 파일이 없는 Debug 환경만 no-op 구현을 사용한다. 개인정보처리방침, Play 데이터 보안 신고, Play Console의 인앱 상품 상태 및 실제 배포 국가 확정은 별도 출시 과제다.

---

## 작업/리뷰 요청 시 참고 순서

1. 관련 Roadmap Step 확인(`06`) — 지금 그 Step 범위인지 먼저 판단한다.
2. 그 Step이 참조하는 `docs/0X` 섹션 확인.
3. 위 "핵심 협업 규칙" 6개 위반 여부 확인.
4. Verify First 항목을 건드리는 변경이면, 실기기/로그 근거 없이 결정하지 않는다 — 없으면 질문으로 남기거나 사용자에게 확인한다.
