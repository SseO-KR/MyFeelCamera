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
- `UseCase.snapToSurfaceRotation()` 결과는 물리 센서 각도 자체가 아니라 CameraX target rotation이며, display rotation과 같은 Surface 회전 규약이다. 읽기 방향이 있는 UI content는 Surface 상수를 각도로 변환한 뒤 `(device target rotation − display rotation)`을 `-180°..179°`로 정규화한다. Portrait Display가 0°로 고정되면 device target rotation만큼 보정되고, Window와 기기가 함께 돌아가면 두 값이 같아 추가 회전은 0이다. 이전의 덧셈 설명은 잘못됐으며, 두 값이 90° 또는 270°일 때 180° 뒤집힘을 만들었다. 이 UI 각도는 CameraX transform에 입력하지 않는다. **(Android/CameraX API Contract, Implementation Decision)**
- `Preview`의 custom `SurfaceProvider`는 `SurfaceRequest.setTransformationInfoListener()`로 변환 정보를 받아야 한다. 변환 정보가 변경될 때마다 listener가 새 정보를 받으며, GL thread에서 사용할 immutable snapshot으로 전달한다. **(Android/CameraX API Contract)**
- `hasCameraTransform() == true`라고 해서 `SurfaceTexture.getTransformMatrix()`가 `TransformationInfo.rotationDegrees` 전체를 이미 포함한다고 일반화하지 않는다. 다만 이 경우 `rotationDegrees`를 별도 행렬로 무조건 재적용하지 않는다. SurfaceTexture transform이 실제로 담당한 변환 범위를 기기에서 확인한 뒤, 필요한 Display 방향 보정과 나머지 crop / mirror / viewport 변환을 합성한다. `TransformationInfo.rotationDegrees`는 항상 별도 GL 회전 행렬로 곱해야 하는 명령이 아니다. **(Android/CameraX API Contract, Verify First — 실제 행렬 방향은 기기에서 검증)**

### 현재 custom GL Preview의 Display 보정

- 2026-09-23 폴드 에뮬레이터에서 Display=270°로 회전할 때 같은 SurfaceRequest의 `rotationDegrees`는 90°→180°로 바뀌었지만 SurfaceTexture 행렬은 유지됐다. 변환 정보를 로그로만 남기는 기존 구현은 실제 Window 회전을 보정하지 못했다. **(Observed)**
- 현재 직접 연결한 후면 Camera Surface에서는 `hasCameraTransform=true`일 때 SurfaceTexture 행렬을 유지하고 Display 방향만 추가 보정한다. `false`인 경로에서는 `rotationDegrees` 및 명시적 mirroring을 적용한다. GL의 y-up UV는 출력→입력 샘플링 좌표이므로 중심 기준 UV 회전각은 각각 `−displayDegrees`, `rotationDegrees`다. UI content 보정각과 이 UV 각도는 서로 다른 값이다. **(Implementation Decision, Android/CameraX API Contract)**
- 실제 그리기에 `SurfaceTexture × 방향 보정 × center-crop` 행렬을 전달한다. 회전된 UV 축이 원본 buffer에서 차지하는 픽셀 길이를 이용해 종횡비를 구하므로, SurfaceTexture가 처리한 축 교환을 다시 추측하지 않는다. 출력 영역과 비율이 다르면 늘이지 않고 중앙 crop한다. 물리적 기기 방향은 이 경로에 전달하지 않으며, Phone의 Display가 유지되면 Preview 변환도 유지된다. **(Implementation Decision)**
- 이 단계의 CameraX 구성은 후면 카메라, ViewPort 미지정(full-buffer crop)이다. 임의의 ViewPort cropRect와 전면 mirror 정책까지 지원했다고 간주하지 않는다. 추가 시 SurfaceTexture의 producer crop/mirror와 중복되지 않는지 별도 검증한다. **(Current Scope, Verify First)**
- 근거: [CameraX 회전 기준](https://developer.android.com/media/camera/camerax/orientation-rotation), [TransformationInfo](https://developer.android.com/reference/androidx/camera/core/SurfaceRequest.TransformationInfo).

`ImageCapture`의 target rotation은 저장 이미지의 EXIF 또는 buffer 방향에 반영된다. 저장 후 JPEG를 다시 처리하는 단계가 추가되면 EXIF와 GL/Capture 변환을 중복 적용하지 않는지 별도로 검증한다. **(Android/CameraX API Contract)**

## 4. Capture 트리거 & 원본 획득

- 셔터 입력은 `ImageCapture.takePicture(...)`를 호출한다. **(Android/CameraX API Contract)**
- `ImageCapture`는 파일/MediaStore에 직접 저장하는 형태와 메모리 상의 `ImageProxy`를 돌려주는 형태를 제공한다. CameraX 1.6.1의 Kotlin API에는 두 경로를 각각 coroutine에 연결하는 공식 suspend `ImageCapture.takePicture()` 확장 함수도 포함되어 있다. **(Android/CameraX API Contract)**
- 저장 전에 LUT를 적용해야 한다는 요구사항은 저장이 끝나기 전에 픽셀 데이터에 접근할 수 있어야 한다는 제약으로 이어진다. **(Product Requirement — `00` §1, §10)**
- P0의 filtered Capture는 공식 suspend `ImageCapture.takePicture()`의 메모리 경로로 `ImageProxy`를 먼저 받은 뒤 LUT 처리와 MediaStore 저장을 수행한다. 이 suspend API의 내부 구현은 `OnImageCapturedCallback`을 coroutine continuation에 연결하고 취소 시 callback delegate를 해제한다. 기존 파일 직접 저장 경로는 픽셀 처리 전에 저장을 완료하므로 Step 9의 최종 경로로 사용하지 않는다. `ImageCapture` 출력은 P0에서 JPEG로 명시해 Ultra HDR/RAW 분기를 범위 밖으로 둔다. **(Implementation Decision, Android/CameraX API Contract)**
- `ImageProxy`의 buffer에는 target rotation이 직접 적용되지 않을 수 있으므로 `imageInfo.rotationDegrees`와 `cropRect`를 Capture 입력의 일부로 함께 전달한다. 회전·crop은 GL 변환 또는 저장 메타데이터 중 한 곳에서 정확히 한 번만 적용하고, Step 10에서 Preview와 비교한다. **(Android/CameraX API Contract, Verify First — 최종 transform 검증)**
- `ImageProxy`는 앱이 닫아야 한다. 필요한 bitmap/metadata를 확보하는 성공 경로뿐 아니라 decode·GL·저장 실패 경로에서도 반드시 `close()`하며, 큰 이미지 decode·GL·JPEG 저장은 Main Thread가 아닌 직렬 Capture worker에서 수행한다. **(Android/CameraX API Contract, Implementation Decision)**
- `CameraController.takePhoto()`는 one-shot 작업을 suspend 함수로 노출하고, `CameraViewModel`은 `viewModelScope`의 한 `try/catch/finally`에서 성공·실패·`isCapturing` 복구를 관리한다. `CancellationException`은 일반 촬영 오류로 변환하지 않고 다시 전파한다. **(Implementation Decision)**
- 촬영음은 촬영 요청 직전이 아니라 suspend `takePicture(onCaptureStarted = ...)`의 노출 시작 콜백에서 재생한다. 플래시 준비 중에 소리가 먼저 나지 않도록 CameraX가 알리는 실제 촬영 시작에 연결한다. `onCaptureStarted`는 같은 촬영의 이미지 반환보다 먼저 한 번 호출되며, CameraX도 이 콜백에서 촬영음을 재생하도록 권장한다. **(Implementation Decision, [CameraX API Contract](https://developer.android.com/reference/androidx/camera/core/ImageCapture.OnImageCapturedCallback#onCaptureStarted()))**
- Capture는 셔터를 누른 순간의 `selectedFilm` snapshot을 입력으로 받는다. 처리 도중 UI에서 Film을 바꿔도 이미 시작된 사진의 LUT는 바뀌지 않는다. **(Implementation Decision — `01` §7)**
- 촬영된 원본에 필터를 실제로 적용하는 방식은 이 문서의 범위 밖이다(`03`).

## 5. P1 조작: Zoom, Flash, Gallery

- Zoom +/−는 바인딩된 `Camera`의 `CameraControl.setZoomRatio()`를 0.5배씩 변경한다. `CameraInfo.zoomState`의 최소·최대값으로 제한하고 실제 배율을 UI에 표시한다. 같은 카메라의 Preview와 ImageCapture에 적용한다. 카메라를 다시 바인딩하면 요청 배율을 새 `CameraControl`에 다시 적용한다. **(Product Requirement, CameraX API Contract, Implementation Decision)**
- Flash 버튼은 후면 카메라의 `hasFlashUnit()`이 참일 때에만 촬영 플래시 OFF/ON을 전환한다. `ImageCapture.flashMode`에 적용하고 다시 바인딩해도 선택을 유지한다. 지속 점등하는 torch와 AUTO 모드는 이 버튼의 범위 밖이다. **(Product Requirement, CameraX API Contract, Implementation Decision)**
- 플래시 ON에서 저장 사진도 실제로 어두워지는가? 프리뷰가 어두워지는 시점이나 저장 완료 시점을 노출 시점으로 간주하지 않고, 해당 실기기의 촬영 요청·노출 시작·이미지 반환 로그와 같은 장면의 OFF/ON 저장 결과로 확인한다. 현재 코드는 앱에서 촬영 중 플래시를 끄지 않으며, 발광과 노출의 동기화는 CameraX/기기 카메라 구현이 담당한다. **(Verify First, Current Implementation)**
- 갤러리 썸네일은 `Pictures/FeelCamera`의 공개 완료된 최신 이미지를 MediaStore에서 찾고, 저장 직후와 카메라 화면 복귀 때 갱신한다. 앱 소유 미디어만 사용하므로 저장소 읽기 권한을 추가하지 않는다. 탭하면 사진 URI를 `ACTION_VIEW`로 열고 읽기 권한을 전달한다. **(Product Requirement, Android MediaStore API Contract, Implementation Decision)**

## 6. 이 문서에서 다루지 않는 것

- LUT/셰이더 적용, Preview·Capture 실행 경로 공유 여부 → `03-rendering-and-lut.md`
- Layout mode 판정 로직, Portrait 외 모드 → `04-adaptive-layout.md`
- Surface/EGL 재생성 시 정확한 이벤트 전파·복구 절차 → `05-lifecycle-and-resource-ownership.md`
- 구현 순서 → `06-implementation-roadmap.md`
