# 04. Adaptive Layout

이 문서는 Portrait/Landscape 배치 메커니즘과, 이를 구동하는 window size/posture 신호 수집을 다룬다. Foldable(Tabletop/Book)의 구체 형태는 `01` §6에서 이미 Verify First로 남겼고 이 문서도 확정하지 않는다. Preview/Capture의 `targetRotation` 정책은 `02` §3에서 다뤘고 여기서 반복하지 않는다.

표기: **(Product Requirement)** · **(Android/Compose/Window API Contract)** · **(Verify First)** · **(Later)**.

## 1. Product Requirement

- 아키텍처는 Portrait 외 모드를 배제하지 않는 구조여야 한다 — "Portrait 전용을 먼저 만들고 나중에 adaptive하게 리팩터링"하는 방식은 피한다. **(`00` §6)**
- Phone은 Portrait orientation을 요청한다. 요청이 적용돼 Window가 유지되는 동안 3-region 구조를 앱 좌표상 StartControls=상단, Preview=중앙, EndControls=하단으로 유지한다. 기기를 돌렸을 때의 사용자 기준 물리적 위치는 이 고정 배치에서 자연스럽게 결정하며, Rail과 Preview를 기기 방향만으로 다시 배치하지 않는다. **(Product Requirement)**
- Phone에서는 현재 단일 Activity의 Camera와 Settings가 같은 Portrait 요청을 유지한다. Settings 이동만으로 Window 방향을 바꾸지 않기 위한 정책이다. 큰 화면에서는 요청이 무시되거나 적용되지 않을 수 있으므로 실제 Window에 적응한다. **(Product Requirement)**
- 버튼 아이콘, Film 정보와 촬영 수, Preview overlay text처럼 읽기 방향이 있는 개별 content는 실제 display rotation과 CameraX 규약으로 양자화된 device target rotation을 합성해 회전한다. Rail 배경과 PreviewSlot은 회전하지 않는다. Flash/Shutter/Gallery/Settings/Zoom의 반복적인 시각 레이블은 표시하지 않고 아이콘·형태와 접근성 설명으로 역할을 전달한다. 촬영 수는 Preview 위에 겹치지 않고 Start 패널의 Film과 Zoom 사이에 `현재 수 / 최대 수`만 표시한다. Film 이름은 Film 영역의 `WARM` 표기를 재사용한다. **(Product Requirement, Implementation Decision)**
- Portrait 요청이 적용되지 않아 실제 Window 크기나 방향이 바뀌면 `CameraLayoutMode`와 기존 `Box` 배치가 adaptive fallback을 담당한다. Phone 여부만으로 Window가 항상 Portrait라고 가정하지 않는다. **(Product Requirement, Android Window API Contract)**
- Galaxy S25에서 시스템 상태바와 내비게이션 바가 카메라 화면을 가리는 문제가 보고됐다. Camera와 Settings가 공유하는 Activity를 몰입형 전체 화면으로 표시하고, 가장자리 스와이프로 시스템 바를 잠깐 볼 수 있게 한다. Camera 조작 영역과 Settings 내용은 `safeDrawing` insets를 적용해 카메라 홀·멀티윈도우의 캡션 바 등에 가리지 않게 한다. **(실기기 사용자 관찰, Implementation Decision, Android/Compose Window API Contract)**
- 시스템 바를 숨긴 뒤 Camera의 상단이 넓게 비어 보인다는 사용자 관찰에 따라, 카메라 화면의 `safeDrawing` 여백은 유지하되 바깥 배경을 control rail과 같은 색으로 이어 그린다. 상단에 중복으로 적용된 고정 16dp 여백은 제거하고, 하단 조작 버튼 아래에만 16dp를 둔다. 기기별 상태바 높이를 하드코딩하지 않는다. **(실기기 사용자 관찰, Implementation Decision, Android/Compose Window API Contract)**

## 2. 신호 재확인 — Window Size ≠ Camera Rotation

`CameraLayoutMode`를 구동하는 입력은 "지금 사용 가능한 공간의 크기/모양"과 "foldable posture"뿐이다. `Display.rotation`이나 `Configuration.orientation`은 여기 입력으로 쓰지 않는다. `Display.rotation`은 실제 Window가 바뀌는 adaptive fallback에서 Start/End 순서와 UI content 회전 기준을 정하는 별도 입력이며, Preview target rotation에도 전달된다. 각 소비자는 같은 관찰값을 받을 수 있지만 서로의 계산 결과에 의존하지 않는다. **(`01` §5, `02` §3 및 Product Requirement)**

`LocalView.current.display?.rotation`처럼 Composition 시점에 값을 한 번 읽는 것만으로는 rotation 변경을 관찰할 수 없다. `DisplayManager.DisplayListener` 등 observable display signal을 화면 lifecycle에 맞춰 구독하고 해제한다. 물리적 기기 방향은 `OrientationEventListener`로 별도 관찰한다. **(Android API Contract, Implementation Decision)**

창 크기 변경(멀티윈도우 리사이즈)·폴드/언폴드는 회전과 다른 트리거다. 회전이 (`configChanges` 없이) Activity를 재생성시키는지 여부는 리사이즈·폴드 전환이 Activity를 재생성시키는지에 대해 아무것도 알려주지 않는다 — 이 문서의 배치 메커니즘 판단(§6)은 이 셋을 뭉뚱그려 회전 쪽 사정을 리사이즈·폴드의 근거로 쓰지 않는다. 셋 다 각자 독립적으로 정상 동작해야 한다.

## 3. Portrait ↔ Landscape 판정 — WindowSizeClass가 아니라 측정된 종횡비

이 부분이 이번에 새로 판단하는 지점이다.

- `androidx.compose.material3` 계열의 WindowSizeClass류(Compact/Medium/Expanded 같은 폭 기반 breakpoint)는 절대 크기(dp) 임계값 개념이다 — 가로가 세로보다 넓은지(종횡비)를 직접 말해주지 않는다. 태블릿이 세로로 놓여도 "Expanded" 폭 클래스일 수 있다. **(Android/Compose Window API Contract)**
- 이 프로젝트가 Portrait/Landscape를 가르는 데 실제로 필요한 신호는 종횡비(너비가 높이보다 큰지)이지, 절대 크기 구간이 아니다.
- 종횡비는 WindowSizeClass 없이도 얻을 수 있다 — 예를 들어 `BoxWithConstraints`가 주는 measured 폭/높이, 또는 `LocalWindowInfo`의 컨테이너 크기. 정확히 어떤 API를 쓸지는 구현 시점에 확인한다. **(Android/Compose API Contract, 구체 API 선택은 Verify First)**
- 따라서 **P0는 `androidx.window`의 WindowSizeClass류를 아예 끌어들이지 않아도 된다.** `androidx.window`는 §4의 posture(폴드) 감지에만 필요하다.

## 4. Foldable Posture 감지

- Fold 유무·힌지 방향·상태는 크기 측정만으로 알 수 없다 — `androidx.window`의 `WindowInfoTracker`(`FoldingFeature` 포함)가 유일한 소스다. **(Android/Window API Contract)**
- 이 신호는 Flow/콜백으로 오는 비동기 값이다 — Activity의 lifetime 동안 바뀔 수 있는 관찰 대상이지, 컴포지션 시작 시점에 한 번 읽고 끝나는 값이 아니다. **(Android/Window API Contract)**
- Tabletop/Book의 구체 형태는 `01` §6에서 이미 Verify First로 남겼다 — 이 문서도 확정하지 않는다. P0/P1 범위 밖이라 posture 구독 자체도 지금 배선하지 않는다. **(Later)**

## 5. CameraLayoutMode 판정 함수

- `layout/` 패키지(`01` §1)에 순수 함수로 둔다: `(measuredSize, posture) -> CameraLayoutMode`. 입력은 §3(측정된 크기)과 §4(posture)뿐이다.
- Portrait/Landscape 판정의 정확한 종횡비 임계값(정사각형에 가까운 경우의 처리, 히스테리시스 필요 여부 등)은 지금 정하지 않는다. **(Verify First)**
- ControlEdgeOrder는 실제 Window가 바뀌는 경우에만 `(CameraLayoutMode, displayRotation) -> ControlEdgeOrder`로 계산한다. Phone Portrait Window가 유지될 때는 기준 Portrait 순서를 바꾸지 않는다. 이 함수는 UI 순서만 반환하며 CameraX API를 호출하지 않는다. **(Product Requirement)**

## 6. 배치 메커니즘 — custom Layout이 필요한가

초기 `Column ↔ Row` 전환을 실기기에서 검증한 결과, 회전 때 `CameraStage`/`PreviewSlot`이 서로 다른 Composition 위치에 놓여 `AndroidView.factory`와 EGL/CameraX 연결이 다시 실행되고 약 1~2초의 Preview 공백이 발생했다. **(실기기 관찰)**

따라서 현재 구현은 표준 `Box` 하나 안에 StartControls / Preview / EndControls를 각각 한 번씩 선언한다. Phone Portrait Window가 유지되는 물리적 기기 회전에서는 rail의 Alignment, 여백, Preview aspect ratio를 바꾸지 않고 개별 UI content의 graphics rotation만 갱신한다. 실제 Window 크기가 바뀌는 adaptive fallback에서는 rail 배치와 Preview 크기를 다시 측정하되 Preview의 호출 위치는 유지한다. 일반적인 크기 변경 경로에서는 같은 `GLSurfaceView`가 유지된 채 `onSurfaceChanged()` 중심으로 처리되는 것을 기대한다. **(Implementation Decision)**

Manifest의 `MainActivity` Portrait 요청을 Camera와 Settings에 공통 적용한다. Settings로 이동할 때 요청을 해제하고 돌아올 때 다시 적용하면 Phone Window가 가로↔세로로 전환되는 중 Preview가 재측정·재준비될 수 있으므로 destination별 요청은 사용하지 않는다. `orientation|screenSize` configuration change 처리는 유지한다. Android 17의 큰 화면에서는 [방향 제한이 무시될 수 있지만](https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored), 구형 OS를 포함한 모든 Fold가 반드시 회전한다고 가정하지 않는다. 큰 화면·폴드·멀티윈도우 등에서 방향 요청이 무시되거나 Window가 외부 요인으로 바뀔 수 있으므로, 고정 방향을 정상 동작의 전제 조건으로 삼지 않는다. 실제 Window가 회전하거나 리사이즈되면 기존 adaptive `Box` 배치와 display rotation 관찰 경로를 사용한다. Activity나 Surface/EGL이 재생성되는 경우에도 Preview가 복구되어야 한다. **(Implementation Decision — Settings도 Phone Portrait 유지)**

Portrait 요청의 직접 목적을 "카메라·GL 객체의 재생성을 막는다"고 표현하지 않는다. 고정 `Box` 적용 후의 로그에서는 일반 회전에도 같은 Preview, ImageCapture, SurfaceRequest, GL thread가 유지됐다. Portrait 요청은 Window 축 교환에 따른 Compose 전체 remeasure, Preview 크기 변경, adaptive rail 재배치를 Phone Camera UX에서 피하기 위한 정책이다. 실제 객체 재생성 여부와 화면 재측정은 별개의 문제다. **(실기기 관찰, Implementation Decision)**

`graphicsLayer(rotationZ)`는 그리기 결과만 회전시키며 Compose에 보고하는 측정 너비·높이는 바꾸지 않는다. 따라서 촬영 수나 Preview 모서리 overlay처럼 경계에 가까운 content는 90°/270°에서 회전된 그림이 rail 또는 Preview의 clip 영역 밖으로 나갈 수 있다. 이런 content는 회전만 적용하지 않고, 90°/270°일 때 자식의 측정 너비와 높이를 서로 바꿔 보고하는 rotation-aware wrapper 안에 배치한다. Rail/Preview 자체의 배치와 `AndroidView` identity는 변경하지 않는다. **(Compose API Contract, Implementation Decision)**

UI의 보정각은 `device target rotation − display rotation`이다. rotation-aware wrapper는 90°/270°일 때 자식에게 전달하는 최대 너비/높이 제약도 교환한다. 실제 가로 Window의 세로 rail은 기본 104dp(큰 글꼴에서는 fontScale 반영)로 두고 촬영 수는 한 줄로 표시한다. Phone의 상하 rail은 기존 배치를 유지한다. **(Implementation Decision — 폴드의 뒤집힘·카운터 줄바꿈 수정)**

Preview는 rail을 제외한 Stage의 너비와 높이에 모두 들어가는 크기로 중앙 배치한다. Window의 짧은 변이 600dp 이상이면 최대 너비를 900dp로, 그 외에는 360dp로 둔다. 큰 화면의 상한은 폴드 화면에서 프리뷰 주변 여백이 넓었던 것을 줄이기 위해 800dp에서 올렸다. 실제 크기는 Stage의 가용 너비·높이와 이 상한 중 작은 값으로 정한다. 이 크기 기준은 orientation lock 여부나 CameraLayoutMode를 바꾸지 않는다. UI의 기본 세로 3:4 / 가로 3:2 프레임은 유지하고, 영상은 GL에서 비율을 보존한 center-crop으로 맞춘다. 별도 Tabletop/Book 레이아웃은 이번 범위에 포함하지 않는다. **(Implementation Decision — 큰 화면의 프리뷰 크기 조정)**

Phone에서 기기만 회전할 때는 Preview 크기와 `AndroidView` identity가 유지되고 `CameraController.bind`가 다시 실행되지 않는지 확인한다. 실제 Window resize/rotation에서는 같은 View가 유지된 채 `onSurfaceChanged()`로 처리되는지 확인한다. 시스템이 Surface/EGL을 재생성하면 `onSurfaceCreated`와 새 `SurfaceRequest`가 발생해도 되며, CameraX 전체 rebind 없이 Preview가 복구되는지 확인한다. **(Verify First)**

**Later**: Foldable(Tabletop/Book) 배치가 실제로 결정되면(`01` §6), 그 배치가 단순 선형 스택/배열로 표현 가능한지, 아니면 처음부터 identity-preserving 구조가 필요한지 별도로 판단한다.

## 7. 이 문서에서 다루지 않는 것

- 기기 방향 기반 camera target rotation → `02` §3
- Tabletop/Book의 구체 타입과 배치 → 이 문서에서 결정하지 않음(`01` §6), 실제 요구사항 확인 후 별도 개정
- Surface/EGL 재생성과 layout mode 전환의 상호작용 → `05-lifecycle-and-resource-ownership.md`
- 구현 순서 → `06-implementation-roadmap.md`
