# 자동화 테스트

GitHub Actions는 JVM 단위 테스트 15개, 카메라 비의존 계측 테스트 31개, 가상 장면 촬영 E2E 1개를 실행한다. 모든 브랜치의 Push와 PR에서 API 35와 API 36으로 검사한다. 매일 UTC 18:00(KST 03:00)에는 최소 지원 API 29, API 35, API 36을 검사한다. 모든 기기 테스트는 폐기 가능한 에뮬레이터를 대상으로 한다.

가상 장면 촬영 E2E는 실제 CameraX·렌더러·MediaStore를 사용해 첫 프레임, WARM/DUSK 전환, 화면·볼륨 셔터, 교대 10회 촬영, 설정 화면 왕복, Activity STOP/RESUME 후 촬영을 확인한다. 장면의 색상은 에뮬레이터별로 달라 고정 RGB 비교를 하지 않는다. 고정 회색 `imagefile:` 입력을 사용하는 기존 전체 촬영 시나리오는 실험용으로 남아 있으며 기본 Actions 검사에는 포함하지 않는다. workflow가 기본 브랜치에 반영되면 GitHub의 Run workflow와 야간 스케줄도 사용할 수 있고, 기능 브랜치에서도 Push·PR 검사를 실행한다.

프로젝트의 [AGENTS.md](../AGENTS.md)에 따라 로컬 에뮬레이터 실행·ADB·설치·앱 조작·캡처는 해당 작업에 대한 사용자의 명시적 허가 후에만 수행한다. 테스트 작성이나 아래 명령의 문서화가 실행을 의미하지 않는다.

| 테스트 코드 | 검증 범위 | 실행 시점 |
| --- | --- | --- |
| `CameraScreenUiTest` | 시작 필름·왕복 전환, 셔터 상태·중복 입력, 격자, 줌·플래시, 권한 안내·초기화 재시도 | PR |
| `SettingsUiTest` | 필름·격자·셔터음 선택, 로딩·저장 중 입력 차단, 필수 셔터음·저장 오류 안내 | PR |
| `FilmRenderingTest` | 실제 capture 및 Surface/OES preview 렌더러의 WARM·DUSK 기준 이미지, 왕복 복원, 두 경로 일치 | PR |
| `CaptureGeometryRenderingTest` | 두 필름 × 4방향 × 전체/비대칭 크롭, 잘못된 입력 후 정상 렌더링 | PR |
| `PhotoSaveIntegrationTest` | JPEG 픽셀·크기·방향·메타데이터, 별도 URI, 처리 실패 후 저장, 쓰기·공개 실패 시 미완성 항목 삭제 | PR |
| `CameraCaptureE2ETest` | 실제 CameraX 촬영, 미리보기 PixelCopy 색상, 저장 사진 색상, 썸네일 출처·로딩, 필름 변경 직후 촬영·복원 | 수동 실험용 |
| `CameraCaptureE2ETest`의 반복 촬영 | WARM/DUSK 교대 촬영 10회, 각각 별도 사진 저장 | 수동 실험용 |
| `CameraSceneCaptureE2ETest` | 가상 장면 첫 프레임, WARM/DUSK 촬영, 화면·볼륨 셔터, 10회 연속 촬영, 설정 왕복·Activity 복귀 후 촬영, MediaStore JPEG·썸네일 | Push·PR API 36, nightly |
| `CameraLifecycleE2ETest` | STOP/RESUME, 설정 왕복, display 회전 후 새 프레임·촬영 | 수동 실험용 |
| `CameraPermissionE2ETest` | OS 권한 창 거부 → 촬영 차단 → 재요청 허용 → 실제 촬영 | 수동 별도 프로세스 |
| `SettingsRestartE2ETest` | UI에서 필름·격자·셔터음 변경 → 프로세스 종료 → 새 프로세스에서 복원·촬영 | 수동 2단계 |
| 기존 `PreviewRotationTest`, `CameraRendererLifecycleTest` | 좌표 변환과 실제 EGL/OES/SurfaceRequest 자원 수명 | PR |
| `CameraCaptureTest` | 셔터 시점 필름·소리 고정, 중복 요청 무시, 촬영/처리/저장 실패 후 재시도, 설정 간 간섭 | JVM |
| `CameraSettingsPersistenceTest` | DataStore 종료 후 새 인스턴스에서 필름·격자·셔터음 복원 | JVM |
| 기존 JVM 테스트 | 촬영 가능 상태, 권한 복구 분기, 알림, 사진 방향, 수집 동의 저장 | JVM |

동일 조건의 이미지 비교와 실제 촬영 경로를 나눠 검증한다. `PreviewFixture`는 테스트 비트맵을 실제 Surface/OES에 쓰고 production `CameraRenderer`를 사용한다. 촬영 이미지 테스트는 production `CaptureGlRenderer`를 사용한다. 저장 테스트도 실제 EGL·MediaStore를 사용하며 실패 테스트만 ContentProvider 경계에서 실패를 주입한다.

UI 테스트는 카메라 바인딩 완료와 화면 상태를 제어한다. E2E에서는 카메라·렌더러·저장을 대체하지 않고 실제 앱을 실행한다. 렌더러의 `renderedFrame`은 첫 입력 프레임을 소비한 뒤 그린 필름과 증가하는 번호를 기록한다. Compose 상태 반영, GL draw, Surface 게시 시점은 다르므로 PixelCopy 색상 확인도 완료 조건으로 기다린다. 고정 시간 sleep은 사용하지 않는다.

사진 방향의 픽셀 정확도는 비대칭 테스트 이미지로 네 방향을 검증한다. display 회전 E2E는 창 회전 이후 미리보기와 촬영이 계속 동작하는지 검증하며, 물리 센서의 방향 변화를 재현한다고 간주하지 않는다.

**기준 이미지와 오차**

`app/src/androidTest/assets/film/`의 `input.png`는 색상표·회색/색상 그라데이션이다. `warm.png`, `dusk.png`는 Python 표준 라이브러리로 RGB8 LUT를 삼선형 보간한 독립적인 계산 결과다. GLSL이나 GPU 출력으로 기대값을 생성하지 않는다. `references.json`에 LUT/PNG 해시와 회색 카메라 입력의 기대 RGB를 기록한다. 이 기준은 현재 LUT 동작의 회귀 검증용이며 미적 품질에 대한 사용자 승인 기록은 아니다.

```sh
# 기기 없이 기존 기준 파일과 현재 LUT의 일관성 확인
python3 scripts/generate_film_references.py --check

# 의도적인 LUT 변경을 검토한 경우에만 기준 파일을 명시적으로 갱신
python3 scripts/generate_film_references.py
```

기본 capture 비교는 평균 채널 오차 0.8, 최대 4(0~255)를 허용한다. Surface/OES 비교는 평균 1.5, 최대 8이며, JPEG 저장 비교는 기준 이미지를 동일 품질 95로 인코딩한 결과와 평균 1.5·최대 14를 비교한다. 실패하면 expected/actual/diff(8배 강조)를 저장한다. 최초 기기 실행에서 수치의 적절성을 확인하고, 실패 원인을 확인하지 않은 채 오차를 늘리거나 기준 파일을 자동 갱신하지 않는다.

**고정 카메라 입력과 실행**

기본 `standard` 모드는 실제 카메라 입력을 요구하지 않는다. UI 테스트가 상태를 제어하고, 이미지 테스트는 자체 Surface/OES 입력을 사용한다. Actions에서도 카메라를 비활성화한 에뮬레이터로 31개를 실행한다.

실험용 `core`·`extended` 모드는 `-camera-back imagefile:<절대 경로>/app/src/androidTest/assets/film/camera-input.png`로 시작한 에뮬레이터를 요구한다. `imagefile:`를 지원하는 Emulator가 필요하다. 검증 환경이 바뀌면 버전·출력 차이를 검토한다. 가상 장면의 이미지 색은 고정 RGB 기준에 사용하지 않는다. [Android 카메라 입력 옵션](https://developer.android.com/studio/run/emulator-commandline)

카메라 입력은 RGB(128,128,128)의 회색이다. 해상도·센서 방향·카메라 크롭 차이로 색상 판정이 흔들리지 않도록 중앙 영역의 RGB 평균을 비교하며 채널별 오차 8을 허용한다. WARM·DUSK의 기대 채널 차이보다 작게 정했다. 형상·회전의 정확도는 별도의 비대칭 이미지 테스트가 담당한다.

기기 없는 검증:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
bash -n scripts/run_android_ci.sh
python3 scripts/generate_film_references.py --check
```

사용자가 에뮬레이터 실행을 허가하고 테스트 에뮬레이터가 연결된 경우:

```sh
bash scripts/run_android_ci.sh standard
```

가상 장면 촬영 E2E는 GitHub Actions에서 API 36 에뮬레이터로 실행한다. 연결된 가상 장면 에뮬레이터에서 로컬 실행하려면:

```sh
bash scripts/run_android_ci.sh scene
```

고정 카메라 입력을 준비한 실험용 E2E 실행:

```sh
bash scripts/run_android_ci.sh core
bash scripts/run_android_ci.sh extended
```

스크립트는 APK를 한 번 설치하고 AndroidJUnitRunner를 실행한다. 기본값은 `standard`이며 CameraScreenUiTest, SettingsUiTest, FilmRenderingTest, CaptureGeometryRenderingTest, PhotoSaveIntegrationTest, PreviewRotationTest, CameraRendererLifecycleTest의 7개 클래스를 명시적으로 선택한다. 예상 결과는 31개다. 테스트 클래스를 추가하거나 메서드 수를 변경하면 스크립트의 목록과 예상 개수도 함께 갱신해야 한다.

core에서는 `ExtendedCameraTest`, `ProcessRestartTest`, 권한 테스트를 제외하여 33개를 선택한다. extended에서는 37개 후 권한 검사 1개와 설정 쓰기/읽기 각 1개를 별도 instrumentation 프로세스로 실행한다. 설정 두 단계 사이에는 앱 데이터와 APK를 유지하고 앱 프로세스를 종료하므로, Activity 재생성과 구별된다. 권한 취소도 instrumentation 시작 전에 수행한다.

테스트마다 자신이 생성한 사진만 정리한다. 테스트 시작 전 사진 목록을 확보하지 못하면 삭제하지 않는다. CI 전용 에뮬레이터는 실행 종료 후 폐기한다.

**실행 결과**

`app/build/ci-device/<시나리오>-<고유 실행 ID>/`에 JUnit XML, instrumentation 출력, Logcat, 화면·사진·이미지 차이를 수집한다. GitHub Actions의 `android-device-api-*` artifact에서 내려받는다. 고유 실행 ID로 이전 실행의 성공 보고서를 재사용하지 않는다.

`adb am instrument`의 종료 코드만으로 테스트 성공을 판단하지 않는다. `CiResultListener`의 JUnit XML을 확인해 예상 개수 불일치, 실패, skip, 보고서 누락을 모두 실패로 처리한다. 앱 크래시로 보고서가 없어도 instrumentation 출력과 Logcat은 보관한다. JVM 결과와 Lint는 `android-check-reports` artifact에 보관한다.

에뮬레이터는 실제 센서·자동 노출·물리 플래시·스피커와 제조사 GPU 차이를 보장하지 않는다.

2026-10-28 검증: JVM 15개 통과, 앱·테스트 APK 빌드 통과. 계측 40개 중 일반 31개는 사용자 Android Studio 확인과 로컬 실행에서 통과했다. 촬영 E2E 6개는 Android 36·37.2 환경에서 첫 프레임 대기가 실패했으며, 이후 권한·프로세스 재시작 3개는 검증이 완료되지 않았다.

최초 Android 37 실행에서 Compose UI 테스트가 전이 의존성으로 받은 Espresso 3.5.0의 내부 API 호출이 실패했다. 테스트 의존성에 Espresso 3.7.0을 명시하여 해결했다. 저장 실패 테스트가 이미 삭제를 확인한 URI를 다시 삭제하던 정리 오류도 테스트 코드에서 수정했다. [Espresso 3.7.0 수정 내역](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0)
