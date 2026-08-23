# 06. Implementation Roadmap

학습 순서를 고려한 구현 단계다. 기능 의존성뿐 아니라 학습 부담(한 번에 낯선 개념 두 개를 섞지 않기)을 기준으로 나눴다. 순서는 확정이 아니다 — 실제로 구현하다 발견하는 게 있으면 재배치한다. 중요한 건 각 단계가 실기기에서 검증 가능한 상태로 끝나는 것이다.

핵심 원칙 (재확인, `00`~`05`에서 이미 확정):
- P0는 Portrait 하나, LUT 색보정 하나로 좁힌다. Zoom/Flash/AF, grain/vignette 제품 채택, Landscape/Large screen/Foldable 구현은 P0 이후다.
- 이 로드맵의 상당 부분은 `00`~`05`가 Verify First로 남긴 항목들을 실기기에서 답하는 과정이다 — 아래 단계마다 어떤 질문이 그 자리에서 해소되는지 표시했다.
- 핵심 구현(CameraX 세부, OpenGL, Compose custom layout 등)은 사용자가 직접 작성한다. AI는 skeleton/TODO/리뷰까지 담당한다.

## Step 1 — CameraX Preview 최소 표시
- 목표: `ProcessCameraProvider` 획득, `Preview` UseCase를 임시로 표준 `PreviewView`에 바인딩. 카메라가 화면에 보이는 것까지만.
- 개념: `ProcessCameraProvider.getInstance()`가 비동기라는 것(`02` §1), `bindToLifecycle()`, 카메라 권한.
- UI: 현재 사용자 요구에 따라 3-region control 배치가 display rotation별로 올바른 물리적 edge에 놓이는지 함께 확인한다. CameraX `targetRotation` 구현은 이 단계 범위 밖이다.
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

## Step 9 — Capture에 동일 LUT 적용
- 목표: 촬영 결과에 Preview와 같은 Selected Film을 적용한 뒤 저장(`00` §1 핵심 불변식).
- 이 단계에서 실제로 답해야 하는 것: Preview와 Capture가 GL 파이프라인을 공유하는지(`filter_capture_fragment.glsl`을 오프스크린 렌더링으로 쓰는지), 완전히 다른 경로를 쓰는지 — `01` §7/`03` §7의 Verify First가 여기서 해소된다.
- 완료 기준: 저장된 파일이 필터 적용 상태로 나온다.
- 검증: 저장된 파일을 직접 열어 확인.

## Step 10 — Preview / Capture 결과 비교
- 목표: 같은 장면을 Preview로 보고, 촬영·저장한 뒤 나란히 비교.
- 완료 기준: `03` §7-A(같아야 하는 것)가 육안으로 충족되는지 판단한다. §7-C(crop/aspect, color space, grain/vignette 좌표 등)의 실제 영향을 이 단계에서 처음 관찰한다.
- 검증: 실기기, 여러 장면/조명 조건.

## Step 11 — Portrait 안에서 lifecycle 안정화 (P0 마감선)
- 목표: Home↔Resume, 다른 화면 이탈 후 복귀, 화면 꺼짐/켜짐 등에서 Preview가 깨지지 않고 정상 표시.
- 검증: `05` §5의 로깅 체크리스트로 반복 테스트(Home↔Resume, 화면 전환 왕복).
- 이 단계를 통과하면 P0 완료(`00` §3 8개 항목 전부 충족).

## Step 12 — Landscape Preview lifecycle 검증
- 목표: 이미 추가된 `CameraLayoutMode.Landscape`/ControlEdgeOrder 분기가 GLSurfaceView 기반 Preview에서도 불필요한 재생성 없이 동작하는지 검증한다(`04` §6).
- 이 단계에서 실제로 답해야 하는 것: `Column ↔ Row` 전환이 Preview `AndroidView`를 재생성시키는지, 재생성이 체감되는지 — `04` §6의 4단계 절차(단순 구현 → 로그 검증 → 문제없으면 유지/있으면 `movableContentOf`나 custom `Layout` 검토)를 그대로 실행한다.
- 검증: 실기기 회전, `05` §3 매트릭스 기준 로그 관찰.

## Step 13 — Large screen / Foldable
- 목표: `01` §6이 열어둔 Foldable 케이스(Tabletop/Book 등)의 구체 형태를 `androidx.window` 실제 이벤트와 실제 UI 요구사항을 보고 정한다.
- 이 단계 전까지는 손대지 않는다 — 추측성 설계를 피하기 위해서다(`01` §6, `04` §4).

## 이 로드맵이 아직 답하지 않는 것

각 문서가 Verify First로 남긴 항목은 위 단계들이 실행되면서 자연스럽게 답해진다. 이 로드맵 자체가 새로 여는 질문은 없다 — 이미 있는 질문에 "언제 답할 것인가"만 배정한다.
