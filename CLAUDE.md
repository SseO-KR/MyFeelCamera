# NewFeelCamera — 프로젝트 가이드

## 프로젝트 개요

필름 카메라 감성의 Android 카메라 앱. Kotlin + CameraX + OpenGL ES 3.0 + Jetpack Compose. `feelcamera`(sibling 프로젝트)의 이식이 아니라 요구사항부터 새로 판단한 clean-slate 프로젝트다.

이 프로젝트는 두 목표를 동등한 우선순위로 갖는다:

1. 실제로 안정적으로 동작하는 필름 카메라 앱을 완성한다.
2. 사용자가 CameraX, Compose, Android lifecycle, Surface, OpenGL ES, SurfaceTexture, OES texture, shader, LUT 처리, adaptive layout을 **직접 구현하며 학습**한다.

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
| `docs/06-implementation-roadmap.md` | Step 1~13 구현 순서. **지금 작업 범위는 이 문서의 "현재 Step"을 기준으로 정한다** |
| `new_feelcamera_filter_assets/` | 실제 LUT(`lut/*.bin`)·shader(`shader/*.glsl`) 리소스 원본 |

---

## AI 역할 경계

이 프로젝트에서 AI는 **코드를 대신 작성하는 개발자가 아니라, 설계를 잡고 학습 경로를 만들고 사용자가 작성한 코드를 리뷰하는 mentor**다.

**AI가 하는 것**: 요구사항 정리, architecture 설계, 책임 분리, module/package 구조, interface/class skeleton, 함수 signature, TODO 작성, 구현 순서 제안, Gradle/Manifest 등 빌드 인프라, 코드 리뷰.

**AI가 완성본을 먼저 작성하지 않는 것** (반드시 TODO + skeleton까지만):
- `ProcessCameraProvider` 설정, CameraX `Preview`/`ImageCapture` 구성, `bindToLifecycle`
- `Preview.SurfaceProvider` 실제 구현, `SurfaceRequest` 처리, Surface 생성/해제
- `SurfaceTexture` 생성·lifecycle, OES Texture, `onFrameAvailable` 처리, GL thread 전달, `updateTexImage()`
- Renderer draw loop, Shader compile/link, Vertex/Fragment 연결
- LUT texture upload, LUT sampling 연결, Capture-side LUT 처리, Preview/Capture 결과 일치 처리
- custom Compose `Layout`, Foldable posture 처리

사용자가 명시적으로 "구현해줘"라고 요청하지 않는 이상, 코드가 비어 있다고 해서 위 TODO를 대신 채우지 않는다.

---

## 핵심 협업 규칙

**1. 설계는 `docs/00~06`을 근거로 한다.** 새 판단은 관련 섹션을 인용한다. 문서에 없는 결정은 문서를 먼저 갱신하고 코드에 반영한다 — 반대 순서로 하지 않는다.

**2. 핵심 CameraX/Compose/GL 코드는 사용자가 직접 구현한다.** 위 "AI 역할 경계" 목록에 있는 것은 항상 TODO로 남긴다. TODO에는 Roadmap Step, 학습 포인트, 관련 문서 섹션을 남긴다(`06` Step 4의 TODO 형식 참고).

**3. 현재 Roadmap Step 밖의 코드를 미리 만들지 않는다.** 새 class/file/package는 그 책임이 실제로 필요해지는 Step에서 추가한다. "나중에 필요할 것 같아서"는 지금 만들지 않는 이유가 된다 — 만드는 이유가 되지 않는다.

**4. `Verify First` 항목을 임의로 설계 결정으로 승격하지 않는다.** `01`~`05`가 Verify First로 남긴 것(SurfaceProvider 소유자/lifetime, Preview·Capture 실행 경로 공유 여부, custom Layout 필요 여부, Foldable 구체 모드, grain/vignette 채택 여부 등)은 해당 Roadmap Step에서 실제 구현/로그로 확인하기 전까지 열어둔다. Verify First 문장을 이미 그렇게 동작하는 것처럼(선언형/invariant처럼) 쓰지 않는다 — 질문 형태를 유지한다.

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
- Gradle/Hilt 설정이 완료되어 있으며, `NewFeelCameraApp`은 `@HiltAndroidApp`, `MainActivity`는 `@AndroidEntryPoint`, `CameraController`는 `@Inject constructor`, `CameraViewModel`은 `@HiltViewModel`을 사용한다. KSP가 AGP 9 내장 Kotlin의 source-set DSL을 아직 완전히 지원하지 않아 `gradle.properties`에 `android.disallowKotlinSourceSets=false`(AGP 공식 과도기 escape hatch)가 있다.
- `CameraController`는 `Preview`와 `ImageCapture`를 함께 bind하고 `takePhoto()`로 MediaStore 저장을 수행한다. `CameraScreen`은 고정된 `Box` 안에서 `CameraGlSurfaceView`를 유지하며, GL Preview는 `CameraSurfaceProvider` → OES `SurfaceTexture` → `CameraRenderer` 경로로 표시된다.
- **현재 Roadmap Step: Step 4와 Step 12의 Surface lifecycle 검증을 마쳤고, Step 13에서 Phone Portrait 요청·control content 회전·Preview/Capture rotation 신호 분리를 구현했다. Phone 4방향 저장 결과와 실제 Window resize의 adaptive fallback 실기기 검증이 남아 있다.**

---

## 작업/리뷰 요청 시 참고 순서

1. 관련 Roadmap Step 확인(`06`) — 지금 그 Step 범위인지 먼저 판단한다.
2. 그 Step이 참조하는 `docs/0X` 섹션 확인.
3. 위 "핵심 협업 규칙" 6개 위반 여부 확인.
4. Verify First 항목을 건드리는 변경이면, 실기기/로그 근거 없이 결정하지 않는다 — 없으면 질문으로 남기거나 사용자에게 확인한다.
