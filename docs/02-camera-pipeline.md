# 02. Camera Pipeline

이 문서는 CameraX UseCase 구성부터 카메라 프레임이 앱이 소유한 `SurfaceTexture`(OES texture)에 도착하기까지, 그리고 셔터 입력이 원본 이미지를 만들어내기까지의 파이프라인을 다룬다.

**다루지 않는 것**: LUT/셰이더 적용과 Preview·Capture 실행 경로 공유 여부(`03-rendering-and-lut.md`) · Layout mode 판정과 Portrait 외 모드(`04-adaptive-layout.md`) · Surface/EGL 재생성 시 정확한 이벤트 전파와 소유권 전이(`05-lifecycle-and-resource-ownership.md`).

**표기 규칙**: 각 결정 뒤에 근거를 표시한다.
- **(Product Requirement)** — `00-product-requirements.md`가 요구하는 것
- **(Android/CameraX API Contract)** — 플랫폼/라이브러리 API가 그렇게 생겼다는 사실. 우리가 정한 게 아니다
- **(Verify First)** — 아직 답을 모른다. 질문 형태로 남겨두고, 실기기/코드로 확인한다
- **(Later)** — 지금 P0에 필요 없다

## 1. UseCase 구성

- 카메라 선택은 후면 전용이다. **(Product Requirement — `00` §2)**
- `ProcessCameraProvider`를 얻는 것은 비동기 작업이다(`ProcessCameraProvider.getInstance(context)`가 `ListenableFuture`를 반환한다). **(Android/CameraX API Contract)**
- `Preview`와 `ImageCapture`는 같은 `bindToLifecycle()` 호출에 함께 등록한다. **(Android/CameraX API Contract)** — CameraX는 여러 UseCase를 하나의 바인딩 호출에 묶어 등록하는 것을 표준으로 지원한다. Preview와 Capture를 서로 다른 시점에 따로 바인딩하는 것은 "같은 카메라 세션을 공유해야 한다"는 이 프로젝트의 전제와 맞지 않는다.
- 이 바인딩 호출은 `CameraController`가 소유한다. **(`01` §2에서 이미 확정)**
- `Preview`의 목표 해상도를 CameraX 기본 선택에 맡길지 명시적으로 지정할지는 정해지지 않았다 — 기본 선택이 이 프로젝트의 GL 렌더링(OES 텍스처 크기)과 잘 맞는지는 실기기에서 확인해야 안다. **(Verify First)**

## 2. Surface 공급 경로

- CameraX `Preview` UseCase는 Surface가 필요할 때 `SurfaceProvider.onSurfaceRequested(SurfaceRequest)`를 호출한다. **(Android/CameraX API Contract)**
- 이 프로젝트는 `PreviewView`가 만드는 Surface가 아니라, 앱이 직접 만든 `SurfaceTexture`(OES 텍스처에 attach된)를 감싼 `Surface`로 응답한다. **(Product Requirement — `00` §8)**
- `SurfaceRequest`는 카메라가 요구하는 해상도(`SurfaceRequest.resolution`) 정보를 담고 있고, 공급하는 Surface의 버퍼 크기가 이 값과 일치해야 한다. **(Android/CameraX API Contract)**
- "카메라에 Surface를 공급하는 지점"과 "그 Surface 뒤의 텍스처를 GL이 소유하는 지점"은 같은 객체의 책임이어야 한다(`01` §2 — Renderer가 OES 텍스처를 소유). `SurfaceProvider` 구현체가 이 둘을 어떻게 중개하는지는 정해지지 않았다. **(Verify First — `01` §4)**
- `SurfaceTexture.updateTexImage()`는 그 텍스처를 소유한 GL 컨텍스트가 current 상태인 스레드에서만 호출할 수 있다. **(Android platform API 계약 — `SurfaceTexture` 문서에 명시)** 카메라의 새 프레임 알림(`onFrameAvailable`)은 다른 스레드에서 올 수 있으므로 GL 스레드로 안전하게 전달하는 방법이 필요하다 — 구체적 전달 메커니즘은 `05`에서 다룬다.

## 3. Rotation 신호

- Window/layout 신호와 기기 방향 기반 camera target rotation 신호는 서로 다른 입력이다. 카메라 sensor orientation(하드웨어의 고정 특성)과 `OrientationEventListener`가 감지하는 기기 방향을 같은 뜻으로 부르지 않는다. **(`01` §5에서 이미 확정)**
- CameraX `Preview`는 `targetRotation` 값을 받아, 그 값을 기준으로 계산한 `rotationDegrees`를 프레임 메타데이터로 알려준다. **(Android/CameraX API Contract)**
- `ImageCapture` target rotation은 `OrientationEventListener`의 방향값을 `Surface.ROTATION_*`으로 양자화해 결정한다. `ORIENTATION_UNKNOWN`은 무시하고, 같은 rotation 값은 중복 적용하지 않는다. 양자화는 CameraX의 `UseCase.snapToSurfaceRotation()` 같은 검증된 helper 또는 동등한 명시적 매핑으로 한 곳에서만 수행한다. **(Implementation Decision, Android/CameraX API Contract)**
- 방향 listener는 화면이 STARTED일 때 활성화하고 STOPPED일 때 비활성화한다. 현재 rotation은 bind 전에 도착할 수 있으므로 Controller는 최신 값을 보관하고, UseCase 생성 시 초기값과 실행 중 갱신 양쪽에 적용한다. **(Implementation Decision)**
- `Preview` target rotation은 실제 display rotation을 사용한다. Phone의 Portrait 요청이 적용된 동안에는 기기만 돌아도 Preview target rotation을 바꾸지 않는다. 실제 Window/display가 바뀌는 adaptive fallback에서는 새 display rotation을 적용한다. `ImageCapture`는 물리적 기기 방향을 따르므로 두 UseCase의 target rotation이 달라질 수 있다. `UseCase.setTargetRotation()`은 실행 중에도 호출할 수 있으며, 이 갱신 자체는 UseCase 전체 rebind를 요구하지 않는다. **(Implementation Decision, Android/CameraX API Contract)**
- `UseCase.snapToSurfaceRotation()` 결과는 물리 센서 각도 자체가 아니라 CameraX target rotation이다. 읽기 방향이 있는 UI content는 Surface 상수를 실제 각도로 변환한 뒤 `(display rotation + device target rotation)`을 `-180°..179°`로 정규화한다. Portrait Window가 고정되면 device target rotation만큼 보정되고, Window와 기기가 함께 돌아가면 반대 규약의 두 값이 상쇄된다. 이 UI 각도는 CameraX transform에 입력하지 않는다. **(Android/CameraX API Contract, Implementation Decision)**
- `Preview`의 custom `SurfaceProvider`는 `SurfaceRequest.setTransformationInfoListener()`로 변환 정보를 받아야 한다. 변환 정보가 변경될 때마다 listener가 새 정보를 받으며, GL thread에서 사용할 immutable snapshot으로 전달한다. **(Android/CameraX API Contract)**
- `hasCameraTransform() == true`라고 해서 `SurfaceTexture.getTransformMatrix()`가 `TransformationInfo.rotationDegrees` 전체를 이미 포함한다고 일반화하지 않는다. 다만 이 경우 `rotationDegrees`를 별도 행렬로 무조건 재적용하지 않는다. SurfaceTexture transform이 실제로 담당한 변환 범위를 기기에서 확인한 뒤, 필요한 나머지 crop / mirror / viewport 변환만 합성한다. `TransformationInfo.rotationDegrees`는 관찰할 메타데이터이지 항상 별도 GL 회전 행렬로 곱해야 하는 명령이 아니다. **(Android/CameraX API Contract, Verify First — 실제 행렬 방향은 기기에서 검증)**

`ImageCapture`의 target rotation은 저장 이미지의 EXIF 또는 buffer 방향에 반영된다. 저장 후 JPEG를 다시 처리하는 단계가 추가되면 EXIF와 GL/Capture 변환을 중복 적용하지 않는지 별도로 검증한다. **(Android/CameraX API Contract)**

## 4. Capture 트리거 & 원본 획득

- 셔터 입력은 `ImageCapture.takePicture(...)`를 호출한다. **(Android/CameraX API Contract)**
- `ImageCapture`는 두 콜백 형태를 제공한다 — 파일/MediaStore에 직접 저장하는 형태(`OnImageSavedCallback`)와 메모리 상의 `ImageProxy`를 돌려주는 형태(`OnImageCapturedCallback`). **(Android/CameraX API Contract)**
- 저장 전에 LUT를 적용해야 한다는 요구사항은 저장이 끝나기 전에 픽셀 데이터에 접근할 수 있어야 한다는 제약으로 이어진다. **(Product Requirement — `00` §1, §10)**
- 이 제약을 만족하는 두 콜백 형태 중 어느 쪽을 쓸지, 그 뒤에 필터를 어떻게 끼워 넣을지는 정해지지 않았다 — Preview·Capture 실행 경로 공유 여부(`01` §7)가 먼저 정해져야 판단할 수 있다. **(Verify First)**
- 촬영된 원본에 필터를 실제로 적용하는 방식은 이 문서의 범위 밖이다(`03`).

## 5. 이 문서에서 다루지 않는 것

- LUT/셰이더 적용, Preview·Capture 실행 경로 공유 여부 → `03-rendering-and-lut.md`
- Layout mode 판정 로직, Portrait 외 모드 → `04-adaptive-layout.md`
- Surface/EGL 재생성 시 정확한 이벤트 전파·복구 절차 → `05-lifecycle-and-resource-ownership.md`
- 구현 순서 → `06-implementation-roadmap.md`
