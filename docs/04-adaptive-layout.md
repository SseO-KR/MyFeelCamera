# 04. Adaptive Layout

이 문서는 Portrait/Landscape 배치 메커니즘과, 이를 구동하는 window size/posture 신호 수집을 다룬다. Foldable(Tabletop/Book)의 구체 형태는 `01` §6에서 이미 Verify First로 남겼고 이 문서도 확정하지 않는다. Camera sensor rotation/`targetRotation`은 `02` §3에서 다뤘고 여기서 반복하지 않는다.

표기: **(Product Requirement)** · **(Android/Compose/Window API Contract)** · **(Verify First)** · **(Later)**.

## 1. Product Requirement

- 아키텍처는 Portrait 외 모드를 배제하지 않는 구조여야 한다 — "Portrait 전용을 먼저 만들고 나중에 adaptive하게 리팩터링"하는 방식은 피한다. **(`00` §6)**
- 3-region 구조: StartControls / Preview / EndControls. 기준 Portrait에서는 위·중앙·아래다. 화면에 그린 graphics rotation이 `Surface.ROTATION_90`(기기를 왼쪽으로 90° 회전)일 때 Start/Preview/End는 좌·중앙·우, `Surface.ROTATION_270`(오른쪽으로 90° 회전)일 때는 우·중앙·좌다. `ROTATION_180`에서는 End/Preview/Start가 위·중앙·아래다. 어느 경우든 Preview가 남는 공간을 차지한다. **(Product Requirement)**
- 프레임워크가 Activity 창을 현재 display orientation에 맞춰 그리므로, control glyph/text를 별도 `graphicsLayer`로 미리 회전시키지 않는다. 실제 기기에서 읽기 방향이 어긋날 때만 변환을 검토한다. **(Verify First)**

## 2. 신호 재확인 — Window Size ≠ Camera Rotation

`CameraLayoutMode`를 구동하는 입력은 "지금 사용 가능한 공간의 크기/모양"과 "foldable posture"뿐이다. `Display.rotation`이나 `Configuration.orientation`은 여기 입력으로 쓰지 않는다. 다만 `Display.rotation`은 `CameraLayoutMode`가 축을 정한 뒤 Start/End의 물리적 순서를 정하는 별도 `ControlEdgeOrder` 입력이다. `ControlEdgeOrder`는 `targetRotation`과 독립이다. **(`01` §5, `02` §3 및 Product Requirement)**

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
- ControlEdgeOrder는 순수 함수로 `(CameraLayoutMode, displayRotation) -> ControlEdgeOrder`를 계산한다. 이 함수는 UI 순서만 반환하며 CameraX API를 호출하지 않는다. **(Product Requirement)**

## 6. 배치 메커니즘 — custom Layout이 필요한가

**첫 구현은 표준 Compose primitive로 한다.** Portrait 축에는 `Column`, 가로 축에는 `Row`를 쓰고 가운데 Preview에 `Modifier.weight(1f)`를 준다. `ControlEdgeOrder`는 Start/End의 배치 순서만 반전한다. **(Fix Now)**

**"같은 트리를 유지한다"(`00` §6)는 원칙을 좁혀서 대체하지 않는다.** 이 원칙은 여전히 두 가지를 다 뜻한다 — (1) 같은 세 논리적 자리(startControls/preview/endControls)에 같은 내용이 들어간다, (2) 가능하면 Preview의 `AndroidView`/`GLSurfaceView` 같은 비싼 리소스가 불필요하게 재생성되지 않는다. `Column`↔`Row`를 배타적으로 전환하는 것이 (2)까지 실제로 만족하는지는 — Compose의 위치 기반 identity 규칙상 의심할 이유는 있지만 — 아직 실기기에서 확인된 바 없다. "재생성된다"고도 "유지된다"고도 지금 단정하지 않는다. **(Android/Compose API Contract — 일반 규칙은 확인됨, 이 특정 사례의 결과는 미확인)**

**방향별 3-region UI를 검증할 때, 다음 순서로 진행한다**:
1. `Column ↔ Row` 배타적 전환을 가장 단순한 형태로 먼저 구현한다.
2. 실기기/로그로 검증한다 — `AndroidView.factory` 재호출 여부, View attach/detach, `surfaceCreated`/`surfaceDestroyed`, EGL 재생성 여부(프로젝트 초기 브리핑에서 정한 로깅 체크리스트를 그대로 적용한다). **(Verify First)**
3. `AndroidView.factory`/View attach-detach/Surface lifecycle에 문제가 없으면 그대로 유지한다 — 추가 구조를 만들지 않는다.
4. Preview 재생성이나 화면 끊김이 실제로 확인될 때만 identity-preserving 구조를 검토한다. 후보는 `movableContentOf`(구조 이동 시 상태·노드를 보존하도록 Compose가 제공하는 전용 API — custom `Layout`보다 가볍다)와 custom `Layout`(measure/place를 직접 제어) 둘 다이며, 실제 필요에 맞춰 이 시점에 고른다.

이 순서는 SurfaceProvider(`01` §4)에서 이미 적용한 것과 같은 절차다 — 단순한 경로를 먼저 만들고, 실기기 관찰로 필요를 확인한 뒤에만 식별 보존 구조를 추가한다. (`configChanges` 여부나 Activity 재생성 가능성은 이 판단의 근거로 쓰지 않는다 — §2.)

**Later**: Foldable(Tabletop/Book) 배치가 실제로 결정되면(`01` §6), 그 배치가 단순 선형 스택/배열로 표현 가능한지, 아니면 처음부터 identity-preserving 구조가 필요한지 별도로 판단한다.

## 7. 이 문서에서 다루지 않는 것

- Camera sensor rotation, `targetRotation` → `02` §3
- Tabletop/Book의 구체 타입과 배치 → 이 문서에서 결정하지 않음(`01` §6), 실제 요구사항 확인 후 별도 개정
- Surface/EGL 재생성과 layout mode 전환의 상호작용 → `05-lifecycle-and-resource-ownership.md`
- 구현 순서 → `06-implementation-roadmap.md`
