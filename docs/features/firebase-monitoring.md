# Firebase 운영 모니터링 연결

## 범위와 결정

- 프로젝트 `newfeelcamera`, Android 패키지 `com.newfeelcamera`에 Analytics와 Crashlytics 기본 SDK를 연결한다. Auth, Firestore, Storage, Performance, Remote Config, 결제·무료 한도 변경은 포함하지 않는다. **(User Request / Implementation Decision)**
- `app/google-services.json`은 로컬에만 두고 기존 `.gitignore`를 유지한다. 공개 소스 빌드에서는 이 파일이 없으면 Firebase 플러그인 적용과 SDK 의존성을 함께 생략한다. `-PfirebaseEnabled=false`로도 같은 경로를 검증할 수 있다. Firebase API 직접 호출은 조건부 Firebase 소스에만 둔다. **(Implementation Decision)**
- Debug의 Analytics·Crashlytics 자동 수집은 기본 off, Release는 설정 파일이 있을 때 on이다. 개발 검증에만 `-PfirebaseDebugCollectionEnabled=true`를 사용한다. 광고 ID 권한/수집과 광고 개인화 신호는 사용하지 않는다. 이 설정이 SDK의 모든 초기화·통신을 차단한다는 뜻은 아니다. SDK 자체를 제외하려면 `firebaseEnabled=false`로 빌드한다. **(Implementation Decision)**
- 수동 `FirebaseApp.initializeApp()` 호출은 추가하지 않는다. Google Services 플러그인이 JSON을 Android 리소스로 변환하고 SDK의 초기화 Provider가 이를 사용한다. **(Firebase API Contract)**
- BoM으로 SDK 버전을 맞추고 별도 `-ktx` 모듈은 사용하지 않는다. 공식 가이드와 Google Maven POM에서 BoM `34.19.0`, Google Services `4.5.0`, Crashlytics 플러그인 `3.0.8` 배포를 확인했다(2026-09-27). 실제 AGP 9.3.1 호환성은 빌드 결과로 확인한다. **(Firebase API Contract / Verify First)**

## 사용과 검증

```sh
# 일반 개발: 연결은 초기화되지만 자동 수집은 off
./gradlew :app:assembleDebug
# 테스트 시에만 자동 수집 허용
./gradlew :app:assembleDebug -PfirebaseDebugCollectionEnabled=true
# 공개 소스의 Firebase 없는 빌드 경로
./gradlew :app:assembleDebug -PfirebaseEnabled=false
```

- [x] Firebase 포함 Debug 빌드, 생성 리소스와 merged Manifest 확인
- [x] Firebase 제외 Debug 빌드, Provider/프로젝트 설정 리소스 미포함 확인
- [x] Release Manifest의 자동 수집 설정 확인
- [ ] 기기 실행으로 Firebase 초기화·기존 카메라 기능 회귀 확인
- [ ] Analytics DebugView에서 자동 이벤트 확인(테스트 기기의 `debug.firebase.analytics.app` 설정 후, 검증 완료 시 `.none.`으로 해제)
- [x] 임시 테스트 버튼으로 크래시를 발생시키고 앱 재실행 후 Crashlytics 콘솔 수신 확인(사용자 확인, 2026-10-07). 테스트 버튼과 Debug 수집 기본 on 변경은 제거했다.

빌드 성공과 콘솔 수신 성공은 구분한다. SDK 추가만으로 촬영 성공/실패·필름 선택 이벤트 또는 catch된 예외가 자동 보고되지는 않는다. 저장 성공 `photo_saved`만 아래 계약으로 명시적으로 연결했고, 촬영 실패·필름 선택 이벤트는 아직 없다. 사진, 파일 URI/경로, 구매 토큰, 사용자 ID를 사용자 정의 로그에 넣지 않는다. Java/Kotlin 크래시 기본 연결이며 native GL 드라이버 크래시용 NDK 심볼 처리까지 완료한 것은 아니다.

2026-09-27: AGP 9.3.1에서 Firebase 포함/제외 `:app:assembleDebug` 모두 통과했다. 최종 일반 Debug Manifest는 수집 `false`, Release Manifest는 `true`, 검증 옵션을 켠 Debug Manifest는 `true`였다. 기본 연결 APK의 Google Services 리소스와 초기화 Provider, Firebase 제외 APK의 해당 항목 미포함을 확인했다. 광고 ID·AdServices 광고 ID/기여 분석 권한은 최종 Manifest에서 제외됐다. 검증용 옵션을 해제하고 일반 Debug APK로 다시 빌드했다. 기기 설치·실행·콘솔 수신 테스트는 아직 수행하지 않았다. **(Observed / Verification Limit)**

Crashlytics 자동 수집 off는 보고서 영구 삭제 정책이 아니다. SDK가 로컬에 보존한 미전송 보고서는 나중에 수집을 켤 때 전송될 수 있으므로, 운영 앱과 같은 설치 데이터에서 테스트 크래시를 반복하지 않는다. 별도 dev 프로젝트/테스트 설치 환경 분리는 출시 전에 검토한다. **(Firebase API Contract / Verify First)**

출시 전에 개인정보처리방침, Play 데이터 보안 신고, 배포 지역별 동의 요구를 검토한다. 현재는 운영 프로젝트 하나이며 별도 dev 프로젝트는 아직 생성하지 않았다. 무료 한도·구매 권한의 source of truth는 Firebase가 아니다. **(Verify First / Scope Boundary)**

## 촬영 사용 분석 (2026-10-08)

상태: **앱 이벤트 코드와 GA4 맞춤 측정기준 등록 완료, 실제 이벤트 수신 확인은 남음.** 목표는 실제 저장된 사진을 기준으로 필름·가로/세로 방향·플래시 설정의 사용 비율을 파악하는 것이다. 필름 버튼을 누른 횟수나 셔터를 시도한 횟수는 이 지표에 포함하지 않는다. **(User Request / Implementation)**

### 이벤트 계약

`photo_saved` 맞춤 이벤트를 MediaStore에 JPEG가 게시되어 `CameraController.takePhoto()`가 정상 반환한 뒤 **사진 한 장당 한 번** 기록한다. 무료/무제한 촬영과 화면/볼륨 셔터는 같은 계약을 사용한다. 무료 횟수의 `recordSavedPhoto()`가 뒤이어 실패해도 사진은 이미 저장됐으므로 이벤트를 기록한다. 촬영 제한·권한 거부·중복 입력·카메라/처리/저장 실패에는 이 이벤트를 기록하지 않는다. Analytics 기록 오류가 사진 저장 결과와 사용자 안내를 바꾸지 않게 한다. **(Current Code / Implementation)**

| 매개변수 | 값 | 정의 |
| --- | --- | --- |
| `film` | `warm`, `dusk` | 셔터가 허용된 순간의 `selectedFilm`. 화면 표시 문구 대신 고정된 영문 값을 사용한다. |
| `orientation` | `portrait`, `landscape`, `square` | 필터 처리 후 실제 저장할 Bitmap의 너비·높이를 비교한 결과. Activity가 Portrait로 고정돼 있으므로 화면 방향으로 추정하지 않는다. |
| `flash_enabled` | `on`, `off` | 셔터가 허용된 순간의 플래시 설정. 실제 발광 여부를 뜻하지 않는다. |
| `flash_available` | `yes`, `no` | 그 순간 카메라에 플래시 유닛이 있는지. 플래시 지원 기기의 사진만 분모로 삼아 사용률을 볼 수 있게 한다. |

사진·썸네일·URI/파일 경로·EXIF·위치·사용자 ID·구매 토큰은 이벤트에 넣지 않는다. 필름별 사진 수와 함께 필름별 사용자 수도 보고, 기본 시작 필름 `WARM`의 영향까지 고려해 해석한다. 선택 자체가 궁금해질 때만 별도 `film_selected` 이벤트를 후속 검토한다. **(Data Minimization / Analysis Plan)**

### 구현 내용과 콘솔 작업

1. [x] `CaptureProcessor`에서 저장할 최종 Bitmap의 너비·높이로 `orientation`을 계산하고, 저장된 `Uri`와 함께 `SavedPhoto`로 반환한다. 이 값을 `CameraController`를 거쳐 `CameraViewModel`에 전달한다. 저장된 JPEG를 다시 열어 읽거나 기기 방향 센서의 기본값으로 추정하지 않는다.
2. [x] `CameraViewModel.capturePhoto()`에서 촬영 허용 직후 필름·플래시 설정·플래시 지원 여부를 함께 스냅샷으로 고정한다. `takePhoto()`의 성공 반환 직후, 무료 횟수 기록 분기 전에 `photo_saved`를 한 번 기록한다. Analytics 예외는 저장 성공 흐름과 분리한다.
3. [x] 공통 `CaptureAnalytics` 인터페이스와 조건부 Firebase/no-op 구현을 분리했다. `google-services.json`이 없거나 `-PfirebaseEnabled=false`이면 Firebase SDK 참조 소스를 컴파일하지 않는다. 기존 Debug 기본 수집 off와 Release 수집 설정은 유지한다.
4. [x] GA4 `newfeelcamera` 속성에 `film`(촬영 필름), `orientation`(사진 방향), `flash_enabled`(촬영 시 플래시 설정), `flash_available`(플래시 지원 여부)를 이벤트 범위 맞춤 측정기준으로 등록하고 목록에서 네 항목을 확인했다(2026-10-08). 데이터가 들어오면 `photo_saved` 이벤트 수를 각 값으로 나눠 보되, 필름별 사용자 수와 플래시 지원 기기 기준 사용률도 확인한다.

### 완료 기준과 검증

- [x] 최종 Bitmap의 너비·높이 분류(세로/가로/정사각형) 단위 테스트. [ ] 저장 성공 이벤트의 한 번 기록, 무료 횟수 오류, 저장 실패·촬영 차단 분기는 기기 수신 검증 전까지 코드 경로로만 확인했다.
- [x] `:app:testDebugUnitTest`, Firebase 포함 Debug 빌드와 `-PfirebaseEnabled=false` Debug 빌드 통과. JSON이 없는 공개 소스 빌드에서도 Firebase 참조 때문에 컴파일이 깨지지 않아야 한다.
- [x] Firebase 포함 `:app:assembleRelease` 통과. Release R8 축소와 Hilt 바인딩까지 빌드로 확인했다(2026-10-08). 빌드 성공은 실제 이벤트 전송·콘솔 수신의 증거가 아니다.
- 실제 이벤트 수신은 테스트 기기에서 Debug 수집을 임시로 켜고 Firebase DebugView로 별도 확인한다. 화면/볼륨 셔터, 두 필름, 세로/가로, 플래시 on/off와 지원하지 않는 기기를 가능한 범위에서 대조한다. 보고서 반영에는 시간이 걸리므로 DebugView 확인과 집계 보고서 확인을 구분한다. **Android 에뮬레이터 실행·ADB·설치·체크리스트는 해당 작업에 대한 사용자 명시적 허가 전까지 수행하지 않는다.**
- 출시 전 이벤트 수집 내용을 개인정보처리방침과 Play 데이터 보안 신고에 반영할지 검토한다. 사진 내용은 전송하지 않는다는 점과 Analytics SDK의 자동 수집 범위는 구분해 설명한다.

## 공식 근거

- [Android 연결·BoM·KTX 안내](https://firebase.google.com/docs/android/setup)
- [Crashlytics 연결과 테스트 크래시](https://firebase.google.com/docs/crashlytics/android/get-started)
- [Analytics 수집/광고 ID 설정](https://firebase.google.com/docs/analytics/android/configure-data-collection)
- [Android Analytics 이벤트와 맞춤 매개변수](https://firebase.google.com/docs/analytics/android/events)
- [GA4 이벤트 범위 맞춤 측정기준](https://support.google.com/analytics/answer/14239696)
- [Firebase Analytics DebugView](https://firebase.google.com/docs/analytics/debugview)
- [Crashlytics 수집 제어](https://firebase.google.com/docs/crashlytics/android/customize-crash-reports)
