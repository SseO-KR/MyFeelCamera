# 06. Implementation Roadmap

학습 순서를 고려한 구현 단계다. 기능 의존성뿐 아니라 학습 부담(한 번에 낯선 개념 두 개를 섞지 않기)을 기준으로 나눴다. 순서는 확정이 아니다 — 실제로 구현하다 발견하는 게 있으면 재배치한다. 중요한 건 각 단계가 실기기에서 검증 가능한 상태로 끝나는 것이다.

핵심 원칙 (재확인, `00`~`05`에서 이미 확정):
- P0는 Portrait 하나, LUT 색보정 하나로 좁힌다. Zoom/Flash/AF, grain/vignette 제품 채택, Landscape/Large screen/Foldable 구현은 P0 이후다.
- 이 로드맵의 상당 부분은 `00`~`05`가 Verify First로 남긴 항목들을 실기기에서 답하는 과정이다 — 아래 단계마다 어떤 질문이 그 자리에서 해소되는지 표시했다.
- 핵심 구현(CameraX 세부, OpenGL, Compose custom layout 등)은 사용자가 직접 작성한다. AI는 skeleton/TODO/리뷰까지 담당한다.

## Step 1 — CameraX Preview 최소 표시
- 목표: `ProcessCameraProvider` 획득, `Preview` UseCase를 임시로 표준 `PreviewView`에 바인딩. 카메라가 화면에 보이는 것까지만.
- 개념: `ProcessCameraProvider.getInstance()`가 비동기라는 것(`02` §1), `bindToLifecycle()`, 카메라 권한.
- UI: 기준 Portrait의 3-region control 배치를 확인한다. 기기 방향에 따른 content 회전과 CameraX `targetRotation`은 Step 13 범위다.
- 완료 기준: 후면 카메라 영상이 화면에 뜬다.
- 검증: 실기기 실행.

## Step 2 — ImageCapture 추가 (아직 필터 없음)
- 목표: 같은 바인딩에 `ImageCapture`를 추가하고, 셔터로 촬영해 필터 없이 저장.
- 개념: 여러 UseCase를 한 바인딩에 묶는 것(`02` §1), `ImageCapture`의 두 콜백 형태(`02` §4).
- 완료 기준: 촬영한 원본 사진이 저장된다.
- 검증: 저장된 파일을 갤러리 등에서 확인.
- 주의: 이 저장 경로를 "완성"으로 취급하지 않는다 — Step 9에서 필터가 들어갈 자리이므로, 여기서 저장 로직을 필요 이상으로 다듬지 않는다.

## Step 3 — 기본 Compose 카메라 UI
- 목표: 셔터 버튼, `CameraViewModel`(State/Intent/Effect), `CameraController`(`01` §2)로 카메라 로직 분리.
- 개념: UI lifecycle과 카메라 세션 lifecycle을 같은 객체가 담당하지 않는다는 책임 분리(`01` §2/§3).
- 완료 기준: 버튼 탭 → 촬영 → 상태가 화면에 반영된다.
- 검증: 실기기 조작.

## Step 4 — `PreviewView` 제거, GLSurfaceView + OES SurfaceTexture로 전환
- 목표: `PreviewView`를 걷어내고 앱이 직접 만든 `SurfaceTexture`(OES) 기반 `Surface`를 CameraX에 공급, 필터 없이 카메라 프레임을 그대로 그린다.
- 개념: `SurfaceRequest`/`SurfaceProvider`(`02` §2), EGL Context/`SurfaceTexture` 소유권(`05` §2), `updateTexImage()` 스레딩 계약(`05` §5).
- 이 단계에서 실제로 부딪히고 답해야 하는 것: `SurfaceProvider`를 어디에 둘지, provider lifetime, `SurfaceTexture` 재생성 시 연결 방식(`05` §4 Verify First 전부) — 여기서 실물 코드로 처음 판단한다.
- 완료 기준: `PreviewView`였을 때와 화면상 차이 없이(필터 없는 pass-through) 카메라가 보인다.
- 검증: `05` §5/§6이 지정한 로그(`AndroidView.factory`, `surfaceCreated`/`Destroyed`, EGL 생성/파괴)로 실제 lifecycle을 관찰.

## Step 5 — 기본 shader 실험 (LUT 이전)
- 목표: 파라미터 없는 간단한 색 변환(흑백, 색 반전 등)을 셰이더로 적용.
- 개념: 셰이더 컴파일/링크, uniform 없이 fragment shader가 프레임에 개입하는 방식.
- 완료 기준: 화면이 실시간으로 그 변환을 반영한다.
- 검증: 실기기에서 육안 확인.

## Step 6 — LUT 업로드
- 목표: `new_feelcamera_filter_assets/lut/`의 실제 LUT를 `GL_TEXTURE_3D`로 업로드(`03` §3/§4/§5/§6).
- 개념: Resource Contract(32³/RGB/1byte) → Rendering Contract(unsigned normalized 해석) → Current Shader Implementation(`sampler3D`, half-texel 샘플링)의 계층이 실제 코드로 연결되는 지점.
- 완료 기준: 항등에 가까운 최소 테스트(예: `uLutStrength`를 0으로 낮추면 원본과 같아 보이는지)로 업로드 자체가 맞는지 확인.
- 검증: 실기기, 파라미터를 0/1로 토글하며 확인.

## Step 7 — Preview에 LUT 적용
- 목표: `filter_preview_fragment.glsl`을 실제로 붙여 Warm 또는 Dusk 하나를 프리뷰에 적용.
- 완료 기준: 필터가 실시간 프리뷰에 육안으로 반영된다.
- 검증: 실기기.

## Step 8 — Film 선택 UI
- 목표: Warm/Dusk 2개 Preset 중 선택하는 UI. `Selected Film`(source of truth)이 Preview가 읽는 파라미터를 바꾼다(`00` §3, `03` §2).
- 완료 기준: 선택을 바꾸면 Preview가 즉시 반응한다.
- 검증: 실기기.
- 확인 결과: Warm/Dusk 전환 시 Preview가 즉시 바뀌고 UI 선택과 일치했다. 전환 중에는 `Film changed` 로그만 추가됐고 LUT 재업로드, `onSurfaceCreated()`, 새 `SurfaceRequest`는 발생하지 않았다. 코드 경로상 다음 draw에서 선택된 texture ID가 binding된다.

## Step 9 — Capture에 동일 LUT 적용
- 목표: 촬영 결과에 Preview와 같은 Selected Film을 적용한 뒤 저장(`00` §1 핵심 불변식).
- 구현 결정: 셔터 순간의 `selectedFilm`을 snapshot으로 고정하고 CameraX 1.6.1의 공식 suspend `ImageCapture.takePicture()` 메모리 경로로 `ImageProxy`를 받는다. ViewModel은 한 coroutine의 `try/catch/finally`에서 촬영 전체 상태를 관리하고, Capture 처리기는 단일 Executor 기반 CoroutineDispatcher/offscreen EGL Context에서 source를 `GL_TEXTURE_2D`로 올려 `filter_capture_fragment.glsl`과 같은 LUT asset을 적용한 뒤 JPEG로 MediaStore에 저장한다. Preview의 EGL Context와 GL 객체는 공유하지 않는다(`01` §7, `02` §4, `03` §6~§7, `05` §9).
- 구현 순서: (1) Capture shader asset 연결 → (2) 메모리 Capture와 `ImageProxy` 수명 처리 → (3) offscreen EGL/FBO 렌더링 → (4) 회전/crop을 한 번만 반영 → (5) JPEG/MediaStore 저장과 실패 정리.
- 완료 기준: 저장된 파일이 필터 적용 상태로 나온다.
- 검증: 저장된 파일을 직접 열어 확인.

## Step 10 — Preview / Capture 결과 비교
- 목표: 같은 장면을 Preview로 보고, 촬영·저장한 뒤 나란히 비교.
- 완료 기준: `03` §7-A(같아야 하는 것)가 육안으로 충족되는지 판단한다. §7-C(crop/aspect, color space, grain/vignette 좌표 등)의 실제 영향을 이 단계에서 처음 관찰한다.
- 검증: 실기기, 여러 장면/조명 조건.

## Step 11 — Portrait 안에서 lifecycle 안정화 (P0 마감선)
- 목표: Home↔Resume, 다른 화면 이탈 후 복귀, 화면 꺼짐/켜짐 등에서 Preview가 깨지지 않고 정상 표시.
- 구현: `05` §8의 binding별 Surface 해제와 Context별 OES 삭제를 분리하고 draw/완료/폐기를 직렬화한다. 종료된 GL event queue에 완료 처리를 맡기지 않는다.
- 검증: 2026-09-23 에뮬레이터에서 native 자원 회귀 테스트 7개, Home 복귀 3회, 화면 꺼짐/켜짐, 분할 화면 왕복과 촬영 저장 통과. 실기기 반복 검증은 남아 있다(`05` §8).
- 이 단계를 통과하면 P0 완료(`00` §3 8개 항목 전부 충족).

## Step 12 — Preview identity / Surface lifecycle 검증
- 목표: 고정된 `Box` 구조에서 recomposition과 실제 Window 크기 변경이 Preview를 불필요하게 재생성하지 않는지 검증한다(`04` §6).
- 확인 결과: 일반 회전 로그에서 같은 Preview/ImageCapture/SurfaceRequest/GL thread가 유지됐다. 별도로 SurfaceRequest 재요청과 EGL 재생성 복구 경로를 확인했다.
- 남은 검증: Phone Portrait 고정 적용 후 물리적 회전에서 Preview 크기와 `AndroidView` identity가 유지되는지, 실제 Window resize에서는 adaptive fallback이 동작하는지 확인한다.

## Step 13 — Camera rotation policy & transform
- 목표: Phone에는 Portrait orientation을 요청하고, Window가 유지되는 동안 PreviewSlot/Rail 배치를 고정한다. Preview는 display 좌표계와 정렬하고, 개별 control content와 Capture는 물리적 기기 방향을 반영한다.
- 정책 근거: 초기 `Column ↔ Row` 전환에서는 Preview View가 실제로 다시 생성돼 공백이 발생했고, 고정 `Box` 구조로 identity를 안정화했다. Phone의 Portrait 요청은 여기에 더해 Window 축 교환에 따른 전체 remeasure, Preview 크기 변경, rail 재배치의 체감 지연을 피하기 위한 UX 선택이다. 현재 Settings도 같은 Activity에서 이 요청을 유지한다. 큰 화면의 실제 Window 변경에는 adaptive fallback을 사용한다.
- 구현 범위: Portrait orientation 요청, display rotation 관찰과 `Preview.targetRotation` 갱신, `OrientationEventListener` 관찰과 `ImageCapture.targetRotation` 및 UI content 회전 갱신, `SurfaceRequest.TransformationInfo` 전달. UI는 `device target − display`로 보정한다. GL은 SurfaceTexture 변환에 Display 방향 보정과 비율 보존 center-crop을 합성한다(`02` §3). 현재 후면/full-buffer 경로 외의 ViewPort crop 및 전면 mirror는 별도 검증한다.
- 완료 기준: Phone의 0/90/180/270도에서 PreviewSlot과 Rail 배치는 앱 좌표상 유지되고 버튼·텍스트는 읽기 좋은 방향을 향한다. 저장 사진의 EXIF/방향은 실제 촬영 방향과 일치하며, rotation 갱신만으로 `CameraController.bind()`가 다시 실행되지 않는다. 실제 Window가 바뀌면 adaptive layout이 동작한다.
- 검증: Phone 4방향 회전에서 Preview 크기·identity·저장 결과를 확인한다. 별도로 multi-window/large-screen Window resize에서 adaptive 배치, Preview 방향·비율·crop을 확인한다. `hasCameraTransform`, `TransformationInfo`, 실제 모서리 패턴을 함께 비교해 회전·crop·mirror가 중복 적용되지 않는지 확인한다.
- 2026-09-23: 폴드 에뮬레이터에서 네 Display 방향의 정방향 영상/UI와 카운터 한 줄 표시를 확인했다. 회전 왕복 동안 `request=141262818`, `GLThread 53`, `generation=1`이 유지됐다. 자동 회귀 테스트 12개(생명주기 7개 + 회전/비율 5개)가 통과했다. 큰 Window의 시스템 회전 애니메이션과 재측정 자체를 제거한 것은 아니며, 회전 과도기와 실기기의 동작은 별도 검증 대상이다. **(Observed)**
- 같은 수정본의 일반 폰 에뮬레이터 검증은 앱 실행 대기 timeout 이후 screencap·emu rotate·dumpsys 요청까지 응답이 멈춰 완료하지 못했다. 기기/앱을 강제 초기화하지 않고 검증 명령만 종료했다. Phone 고정 Display의 UI 각도는 자동 테스트에 포함되지만, 실제 Phone 화면 및 저장 결과 재검증을 대체하지 않는다. **(Verification Limit)**

## Step 14 — Large screen / Foldable
- 목표: `01` §6이 열어둔 Foldable 케이스(Tabletop/Book 등)의 구체 형태를 `androidx.window` 실제 이벤트와 실제 UI 요구사항을 보고 정한다.
- 이 단계 전까지는 손대지 않는다 — 추측성 설계를 피하기 위해서다(`01` §6, `04` §4).

## Step 15 — Settings navigation과 Phone Activity Portrait 유지
- 목표: Camera의 설정 버튼에서 Settings destination으로 이동하고 뒤로 돌아온다. Phone에서는 두 destination 사이에서 Portrait 요청을 변경하지 않는다(`00` §4, `04` §6).
- 범위: 화면 이동, 복귀, 방향 요청의 lifetime. 설정값 저장과 볼륨 버튼 촬영은 다음 단계에서 다룬다.
- 검증: Phone에서 물리적 회전과 Camera↔Settings 이동 중 Window가 Portrait를 유지하고, Camera 복귀 시 Preview가 표시되는지 확인한다. Fold/큰 화면에서 요청이 무시되어 실제 Window가 바뀌면 Camera의 adaptive 배치·Preview 방향·비율과 Settings 표시를 확인한다.
- 이전 destination별 방향 요청 구현에서는 2026-09-23 Phone의 Settings 진입 시 Window가 `ROTATION_270`, Camera 복귀 시 `ROTATION_0`으로 전환됐다. 이 화면 간 전환을 없애기 위해 Activity 공통 Portrait 요청으로 되돌렸다. 같은 물리적 방향에서 이동 전후 Preview 장면/글자 방향은 일치했으며, Fold에서는 회전 후 Camera adaptive 배치·Preview 복구를 확인했다. **(Observed — 이전 구현)**
- Activity 공통 Portrait 복원 후 Phone 에뮬레이터에서 물리적 90° 회전, Settings 진입, Camera 복귀 모두 Window `ROTATION_0`을 확인했다. Settings는 세로로 표시됐고 복귀한 Preview도 다시 표시됐다. 이번 빌드의 Fold 에뮬레이터는 연결되지 않아 큰 화면 회전은 재검증하지 못했다. Android 17에서 큰 화면의 방향 제한이 무시될 수 있다는 플랫폼 동작과 별개로, 실제 Window 변경 시 adaptive 경로의 기기 검증은 계속 필요하다. **(Observed / Verification Limit)**

## Step 16 — CameraRoute 볼륨 키 셔터
- 목표: Camera destination이 `RESUMED`인 동안만 볼륨 ↑/↓의 최초 `ACTION_DOWN`을 기존 `CameraAction.ShutterClicked` 경로로 전달한다. 반복 DOWN과 UP은 추가 촬영 없이 소비하고, Settings에서는 키를 시스템 기본 처리로 넘긴다.
- 범위: `MainActivity`의 키 이벤트 진입점과 callback 보관, `CameraRoute`의 활성 lifecycle 등록·해제. Manifest orientation, CameraX, GL, SurfaceProvider 및 DataStore는 변경하지 않는다.
- 검증: Camera에서 두 볼륨 키의 단발·길게 누르기 촬영, Settings의 기본 음량 조절, Camera 복귀 후 재촬영, 기존 화면 셔터와 Preview 회귀를 확인한다.

## Step 17 — 촬영 결과 안내
- 목표: 사진 저장이 완료됐을 때 짧은 비차단 안내를 표시하고, 촬영 실패 시 기존 `captureErrorMessage`를 노출한다. 저장 전에는 성공 문구를 표시하지 않는다.
- 범위: CameraRoute의 UI 피드백만 변경한다. CameraX/GL 경로와 Preview 배치는 그대로 유지한다.
- 검증: 화면·볼륨 셔터의 저장 성공 안내, 실패 안내, Settings 복귀 시 이전 안내의 재표시 여부를 확인한다.

## Step 18 — 시작 필름과 격자 설정 저장
- 목표: Settings에서 시작 필름과 격자 표시 여부를 바꾸고 Preferences DataStore에 저장한다(`00` §4). Camera 진입 시 저장된 시작 필름을 읽고, 설정 변경 후 복귀하면 새 시작 필름과 격자 상태가 보인다.
- 범위: 설정 저장소·Settings UI/ViewModel·CameraUiState 및 격자 오버레이 연결. 카메라에서 임시로 필름을 바꿔도 시작 필름은 저장하지 않는다. 촬영 기록/카운터 영속화와 Room 도입은 별도 단계에서 데이터 요구사항을 정한 뒤 진행한다.
- 검증: Warm/Dusk 기본값과 격자 on/off가 화면 전환 및 앱 재시작 후 유지되는지, Preview/저장 사진의 필터 선택이 일치하는지 확인한다.
- 2026-09-24: `:app:assembleDebug` 통과. 연결된 에뮬레이터는 `adb devices`에는 나타났으나 APK 설치와 Android shell 명령에 응답하지 않아 화면 전환·재시작 영속성은 아직 실기기/에뮬레이터에서 검증하지 못했다. 앱 데이터나 기기를 초기화하지 않았다. **(Verification Limit)**

## Step 19 — 로컬 무료 촬영 24장 한도
- 목표: 저장에 성공한 사진의 사용 횟수를 기기 내부에 보존하고 `00/24`부터 `24/24`까지 표시한다(`00` §4). 24장 이후에는 화면·볼륨 키 셔터를 모두 차단한다.
- 범위: 한도 값과 영속 카운터, 촬영 성공 후 증가, UI 비활성화 및 오류 안내. Warm/Dusk는 한도를 공유한다. 기존 사진 삭제는 횟수를 환급하지 않는다. 결제·Entitlement·계정/서버 검증은 이 단계에 포함하지 않는다.
- 검증: 재시작 후 횟수 유지, 실패 촬영 시 횟수 불변, 23→24 성공 후 `24/24`와 두 셔터 차단, 설정 이동·복귀 시 유지. 로컬 횟수의 초기화는 허용하며, 백업을 유지하므로 재설치 초기화 자체를 완료 기준으로 삼지 않는다. 구매 권한 연결은 Step 20에서 별도로 검증한다.
- 2026-09-25: `:app:assembleDebug` 통과. 폰 에뮬레이터에서 첫 표시 `00/24`, 볼륨 키 촬영·저장 후 `01/24`, 앱 프로세스 종료·재실행 후에도 `01/24` 유지를 확인했다. `24/24` 실제 도달과 실패 촬영, 설정 왕복은 아직 기기에서 검증하지 않았다. 기존 버전에서 저장된 사진은 새 로컬 카운터에 소급 반영하지 않는다. **(Observed / Verification Limit)**

## Step 20 — 일회성 무제한 구매 (별도 Feature, 아직 구현하지 않음)

- 목표: 무료 24회 정책 위에 Google Play 비소모성 일회성 구매 권한을 연결한다. 유효한 무제한 권한이 있으면 화면·볼륨 셔터 모두 무료 한도와 무관하게 촬영할 수 있다(`00` §4).
- 기능 문서: [일회성 무제한 구매](features/one-time-unlimited-purchase.md). 확정 정책, 현재 코드의 연결 지점, API 계약, 미정 사항과 테스트 목록을 이 문서에서 관리한다.
- 구현 순서: Play Console 상품/테스터 준비 → Billing 연결 → 상품·기존 구매 조회 → 구매 확인·승인·복구 → 구매 UI → 촬영 허용/무료 횟수 기록/무제한 표시 연결 → 결제와 카메라 회귀 검증.
- 범위: 클라이언트 Billing, 구매 권한 및 관련 UI. 무료 횟수는 로컬 저장을 유지하고 백업 설정은 변경하지 않는다. 로그인·서버·기기 식별·구독·소모성 상품·CameraX/GL 재설계는 포함하지 않는다.
- 완료 기준: 구매/복구 후 24회를 넘겨 촬영 가능, 유료 촬영의 무료 카운터 미차감, 대기/취소/실패 시 새 유료 권한 미부여, 대기 거래 취소 후 재조회·대기 해제·재구매 가능, 권한 조회 실패와 미구매의 구분, 구매 승인 기한 내 재시도와 기한 초과 자동 환불 후 재조회·권한 반영, 기존 카메라 기능 회귀 통과. 세부 테스트는 Feature 문서 §8을 따른다. 오프라인 정책과 승인 완료 전 권한 부여 시점은 구현 전에 결정한다.
- Git 단위: 구현 단계를 각각 기능 커밋으로 나누지 않고 기능 완성 후 `feat: add one-time unlimited capture purchase`로 묶는다. 브랜치 예시는 `codex/one-time-unlimited-purchase`이며, 이 문서 작업에서는 브랜치 생성·커밋·결제 코드 작성을 수행하지 않는다.

## 별도 운영 인프라 — Firebase 연결

- 사용자 요청으로 [Firebase 운영 모니터링](features/firebase-monitoring.md)의 기본 연결을 진행한다. Analytics·Crashlytics의 Gradle/Manifest 설정과 Firebase 없는 공개 빌드 경로를 검증한다. 카메라 구현과 Step 19~20의 미완료 항목은 그대로 유지한다.
- 촬영/필름 사용자 정의 이벤트, 잡힌 예외 전송, 기기에서 콘솔 수신 확인은 기본 SDK 빌드 검증과 구분한다.

## 이 로드맵이 아직 답하지 않는 것

각 문서가 Verify First로 남긴 항목은 해당 단계에서 검증한다. Step 20의 상품 설정·구매 UX·오프라인 정책처럼 아직 정하지 않은 항목은 Feature 문서에 남기고, 구현 시 임의로 확정하지 않는다.
