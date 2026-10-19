# Firebase 운영 모니터링 연결

**현재 정책 (2026-10-09):** 사용자 요청에 따라 Release AAB에 Analytics·Crashlytics SDK를 기본 포함한다. `firebaseEnabled` 옵션을 제거했으며 `app/google-services.json`이 없으면 Release 빌드가 실패한다. 처음 실행할 때 사용 통계와 오류 보고를 각각 선택하고 설정에서 바꿀 수 있다. 둘 다 선택하지 않으면 Firebase를 초기화하지 않는다. 아래 2026-09-27·2026-10-08 기록은 당시 구성에 대한 기록이다.

## 범위와 결정

- 프로젝트 `newfeelcamera`, Android 패키지 `com.newfeelcamera`에 Analytics와 Crashlytics 기본 SDK를 연결한다. Auth, Firestore, Storage, Performance, Remote Config, 결제·무료 한도 변경은 포함하지 않는다. **(User Request / Implementation Decision)**
- `app/google-services.json`은 로컬에만 두고 기존 `.gitignore`를 유지한다. JSON이 있으면 추가 옵션 없이 Firebase 플러그인·SDK·소스를 포함한다. JSON이 없는 공개 소스에서는 Debug만 no-op으로 빌드할 수 있으며 Release는 실패한다. **(Release Requirement, 2026-10-09)**
- Manifest의 자동 수집 기본값은 off이며 런타임의 사용자 선택이 각 SDK의 수집을 활성화한다. 기본 Debug에서는 초기화·수집을 하지 않고, `-PfirebaseDebugCollectionEnabled=true`로 동의 흐름을 검증할 수 있다. 광고 ID·광고 저장·광고 사용자 데이터·광고 개인화 동의는 모두 거부한다. **(Implementation Decision, 2026-10-09)**
- 자동 초기화 Provider를 Manifest에서 제거하고 `FeelCameraApp`이 저장된 선택을 읽는다. 하나 이상 허용한 경우 `FirebaseApp.initializeApp()` 후 Analytics consent/collection 및 Crashlytics collection을 각각 설정한다. 촬영 이벤트는 Analytics 허용 상태에서만 호출한다. **(Implementation Decision, 2026-10-09)**
- BoM으로 SDK 버전을 맞추고 별도 `-ktx` 모듈은 사용하지 않는다. 공식 가이드와 Google Maven POM에서 BoM `34.19.0`, Google Services `4.5.0`, Crashlytics 플러그인 `3.0.8` 배포를 확인했다(2026-09-27). 실제 AGP 9.3.1 호환성은 빌드 결과로 확인한다. **(Firebase API Contract / Verify First)**

## 사용과 검증

```sh
# Release: SDK 기본 포함, 사용자 선택 후 수집
./gradlew :app:bundleRelease
# Debug: JSON이 있으면 SDK 포함, 없으면 no-op. 기본 수집·동의 UI 비활성
./gradlew :app:assembleDebug
# 동의 UI와 DebugView를 개발용 Debug에서 검증
./gradlew :app:assembleDebug -PfirebaseDebugCollectionEnabled=true
```

- [x] Firebase 포함 Debug 빌드, 생성 리소스와 merged Manifest 확인
- [x] Firebase 제외 Debug 빌드, Provider/프로젝트 설정 리소스 미포함 확인
- [x] Release Manifest의 자동 수집 설정 확인
- [ ] 기기 실행으로 Firebase 초기화·기존 카메라 기능 회귀 확인
- [ ] Analytics DebugView에서 자동 이벤트 확인(테스트 기기의 `debug.firebase.analytics.app` 설정 후, 검증 완료 시 `.none.`으로 해제)
- [x] 임시 테스트 버튼으로 크래시를 발생시키고 앱 재실행 후 Crashlytics 콘솔 수신 확인(사용자 확인, 2026-10-07). 테스트 버튼과 Debug 수집 기본 on 변경은 제거했다.

빌드 성공과 콘솔 수신 성공은 구분한다. SDK 추가만으로 촬영 성공/실패·필름 선택 이벤트 또는 catch된 예외가 자동 보고되지는 않는다. 저장 성공 `photo_saved`만 아래 계약으로 명시적으로 연결했고, 촬영 실패·필름 선택 이벤트는 아직 없다. 사진, 파일 URI/경로, 구매 토큰, 사용자 ID를 사용자 정의 로그에 넣지 않는다. Java/Kotlin 크래시 기본 연결이며 native GL 드라이버 크래시용 NDK 심볼 처리까지 완료한 것은 아니다.

2026-09-27: AGP 9.3.1에서 Firebase 포함/제외 `:app:assembleDebug` 모두 통과했다. 최종 일반 Debug Manifest는 수집 `false`, Release Manifest는 `true`, 검증 옵션을 켠 Debug Manifest는 `true`였다. 기본 연결 APK의 Google Services 리소스와 초기화 Provider, Firebase 제외 APK의 해당 항목 미포함을 확인했다. 광고 ID·AdServices 광고 ID/기여 분석 권한은 최종 Manifest에서 제외됐다. 검증용 옵션을 해제하고 일반 Debug APK로 다시 빌드했다. 기기 설치·실행·콘솔 수신 테스트는 아직 수행하지 않았다. **(Observed / Verification Limit)**

2026-10-08: 무료 출시 기본 구성에서 `:app:testDebugUnitTest`와 `:app:assembleRelease`를 통과했다. Release 의존성에는 Firebase SDK가 없고, 최종 Manifest에는 Firebase 초기화 Provider가 없으며 Analytics·Crashlytics 수집 메타데이터 값은 `false`다. **(Observed / v1 Default Build)**

Crashlytics 수집 철회는 SDK 계약에 따라 다음 프로세스 실행부터 적용되며, UI에 앱을 완전히 종료하고 다시 열어야 함을 알린다. 거부 상태에서 SDK가 초기화되면 `deleteUnsentReports()`로 미전송 보고서를 삭제한다. 수집 off는 이미 서버에 전송된 보고서의 삭제 요청이 아니다. SDK가 로컬에 보존한 미전송 보고서는 나중에 수집을 켤 때 전송될 수 있으므로, 운영 앱과 같은 설치 데이터에서 테스트 크래시를 반복하지 않는다. 별도 dev 프로젝트/테스트 설치 환경 분리는 출시 전에 검토한다. **(Firebase API Contract / Verify First)**

출시 전에 개인정보처리방침, Play 데이터 보안 신고, 배포 지역별 동의 요구를 검토한다. 현재는 운영 프로젝트 하나이며 별도 dev 프로젝트는 아직 생성하지 않았다. v1은 무료·무제한 촬영이며 Firebase를 촬영 허용 판단에 사용하지 않는다. **(Verify First / Scope Boundary)**

## 촬영 사용 분석 (2026-10-08)

상태: **Release에 Firebase 이벤트 구현을 포함하고, Analytics에 동의한 사용자의 저장 성공 이벤트를 전송한다.** 실제 저장된 사진을 기준으로 필름·가로/세로 방향·플래시 설정의 사용 비율을 파악한다. 필름 버튼을 누른 횟수나 셔터를 시도한 횟수는 이 지표에 포함하지 않는다. **(User Request / Current Implementation)**

### 이벤트 계약

`photo_saved`는 MediaStore에 JPEG가 게시되어 `CameraController.takePhoto()`가 정상 반환한 뒤 **사진 한 장당 한 번** 호출한다. 화면/볼륨 셔터는 같은 계약을 사용한다. 권한 거부·중복 입력·카메라/처리/저장 실패에는 호출하지 않는다. Analytics 거부 상태와 기본 Debug에서는 이벤트를 전송하지 않는다. Analytics 기록 오류가 사진 저장 결과와 사용자 안내를 바꾸지 않게 한다. **(Current Code / Implementation)**

| 매개변수 | 값 | 정의 |
| --- | --- | --- |
| `film` | `warm`, `dusk` | 셔터가 허용된 순간의 `selectedFilm`. 화면 표시 문구 대신 고정된 영문 값을 사용한다. |
| `orientation` | `portrait`, `landscape`, `square` | 필터 처리 후 실제 저장할 Bitmap의 너비·높이를 비교한 결과. 화면 방향으로 추정하지 않고 실제 저장할 Bitmap의 크기를 사용한다. |
| `flash_enabled` | `on`, `off` | 셔터가 허용된 순간의 플래시 설정. 실제 발광 여부를 뜻하지 않는다. |
| `flash_available` | `yes`, `no` | 그 순간 카메라에 플래시 유닛이 있는지. 플래시 지원 기기의 사진만 분모로 삼아 사용률을 볼 수 있게 한다. |

사진·썸네일·URI/파일 경로·EXIF·위치·사용자 ID·구매 토큰은 이벤트에 넣지 않는다. 필름별 사진 수와 함께 필름별 사용자 수도 보고, 기본 시작 필름 `WARM`의 영향까지 고려해 해석한다. 선택 자체가 궁금해질 때만 별도 `film_selected` 이벤트를 후속 검토한다. **(Data Minimization / Analysis Plan)**

### 구현 내용과 콘솔 작업

1. [x] `CaptureProcessor`에서 저장할 최종 Bitmap의 너비·높이로 `orientation`을 계산하고, 저장된 `Uri`와 함께 `SavedPhoto`로 반환한다. 이 값을 `CameraController`를 거쳐 `CameraViewModel`에 전달한다. 저장된 JPEG를 다시 열어 읽거나 기기 방향 센서의 기본값으로 추정하지 않는다.
2. [x] `CameraViewModel.capturePhoto()`에서 촬영 허용 직후 필름·플래시 설정·플래시 지원 여부를 함께 스냅샷으로 고정한다. `takePhoto()`의 성공 반환 직후 `photo_saved`를 한 번 호출한다. Analytics 예외는 저장 성공 흐름과 분리한다.
3. [x] 공통 `CaptureAnalytics` 인터페이스와 조건부 Firebase/no-op 구현을 분리했다. Release는 Firebase 구현을 기본 컴파일하며 JSON이 없는 Debug만 no-op을 사용한다. Firebase 자동 초기화는 제거했고 수집은 런타임 선택에 따라 활성화한다.
4. [x] GA4 `newfeelcamera` 속성에 `film`(촬영 필름), `orientation`(사진 방향), `flash_enabled`(촬영 시 플래시 설정), `flash_available`(플래시 지원 여부)를 이벤트 범위 맞춤 측정기준으로 등록하고 목록에서 네 항목을 확인했다(2026-10-08). 데이터가 들어오면 `photo_saved` 이벤트 수를 각 값으로 나눠 보되, 필름별 사용자 수와 플래시 지원 기기 기준 사용률도 확인한다.

### 완료 기준과 검증

- [x] 최종 Bitmap의 너비·높이 분류(세로/가로/정사각형) 단위 테스트. [ ] 저장 성공 이벤트의 한 번 호출과 저장 실패 분기는 기기 수신 검증 전까지 코드 경로로만 확인했다.
- 2026-10-08 당시 `:app:testDebugUnitTest` 및 Firebase 포함/제외 Debug 빌드가 통과했다. 현재는 SDK 포함 옵션을 제거했으며, JSON이 없는 공개 소스의 Debug no-op 경로만 보존한다.
- [x] Firebase 포함 `:app:assembleRelease` 통과. Release R8 축소와 Hilt 바인딩까지 빌드로 확인했다(2026-10-08). 빌드 성공은 실제 이벤트 전송·콘솔 수신의 증거가 아니다.
- 실제 이벤트 수신은 테스트 기기에서 Debug 수집을 임시로 켜고 Firebase DebugView로 별도 확인한다. 화면/볼륨 셔터, 두 필름, 세로/가로, 플래시 on/off와 지원하지 않는 기기를 가능한 범위에서 대조한다. 보고서 반영에는 시간이 걸리므로 DebugView 확인과 집계 보고서 확인을 구분한다. **Android 에뮬레이터 실행·ADB·설치·체크리스트는 해당 작업에 대한 사용자 명시적 허가 전까지 수행하지 않는다.**
- Firebase 수집을 다시 켜기 전에 이벤트와 SDK 자동 수집 내용을 개인정보처리방침과 Play 데이터 보안 신고에 반영한다. 사진 내용은 전송하지 않는다는 점과 SDK가 수집하는 정보는 구분해 설명한다.

## 공식 근거

- [Android 연결·BoM·KTX 안내](https://firebase.google.com/docs/android/setup)
- [Crashlytics 연결과 테스트 크래시](https://firebase.google.com/docs/crashlytics/android/get-started)
- [Analytics 수집/광고 ID 설정](https://firebase.google.com/docs/analytics/android/configure-data-collection)
- [Android Analytics 이벤트와 맞춤 매개변수](https://firebase.google.com/docs/analytics/android/events)
- [GA4 이벤트 범위 맞춤 측정기준](https://support.google.com/analytics/answer/14239696)
- [Firebase Analytics DebugView](https://firebase.google.com/docs/analytics/debugview)
- [Crashlytics 수집 제어](https://firebase.google.com/docs/crashlytics/android/customize-crash-reports)

## Play 데이터 보안 신고와 게시 전 확인 (2026-10-09)

- 현재 Firebase 사용에는 **대략적인 위치, 앱 상호작용, 비정상 종료 로그, 진단, 기기 또는 기타 ID** 수집을 신고한다. Analytics의 IP 기반 위치와 app-instance ID, Crashlytics UUID, Installations·Sessions 간접 의존성도 포함한다. 정확한 위치·사진 내용은 전송하지 않는다.
- 위 항목은 사용자 선택에 따라 수집되므로 선택사항이다. 용도는 분석이며 전송은 HTTPS/TLS로 암호화한다. 제3자 공유는 Google이 개발자 대신 처리하는 서비스 제공업체로만 동작하는 구성인지 확인하고 답한다. Google Signals, Ads 연결·내보내기, GA4 데이터 공유 설정이 켜져 있으면 이 판단을 다시 확인한다.
- SDK 포함 이후에는 Play의 수집 질문을 ‘아니요’로 신고할 수 없다. `docs/privacy-policy-draft.md`의 새 내용을 공개 Notion 방침에 반영한 뒤 출시한다. 앱 설정의 선택 철회는 서버에 이미 수집된 데이터 삭제와 다르다. 사용자 식별자를 따로 보관하지 않으므로 문의만으로 개별 분석 데이터를 찾아 삭제할 수 있다고 약속하지 않는다.
- [Analytics 데이터 공개](https://support.google.com/analytics/answer/11582702?hl=ko), [Firebase SDK·Installations·Sessions 데이터 공개](https://firebase.google.com/docs/android/play-data-disclosure), [Crashlytics 수집 변경 계약](https://firebase.google.com/docs/reference/android/com/google/firebase/crashlytics/FirebaseCrashlytics).

2026-10-09 검증: 추가 옵션 없는 `:app:bundleRelease` 및 `:app:testDebugUnitTest`를 통과했다. 동의 선택의 저장·복원·철회를 검증하는 2개 테스트를 포함해 단위 테스트 11개가 통과했다. Release AAB의 DEX와 R8 매핑에서 Analytics, Crashlytics, 런타임 수집 제어 구현의 포함을 확인했다. 최종 Manifest에 자동 초기화 Provider와 광고 ID·결제 권한이 없고, 수집·광고 동의 기본값은 모두 false다. Runtime의 Release 수집 허용 값은 true이며 저장된 사용자 선택이 활성화를 결정한다. Crashlytics 매핑 업로드 작업과 Release Lint도 통과했다. 생성한 검증 AAB는 미서명 상태다. 실제 기기·콘솔 수신 검증은 수행하지 않았다. **(Observed / Verification Limit)**

현재 1.0.4 (5) 코드에서 단위 테스트 11개, Release Lint(오류 0개·경고 45개), Debug APK·Release AAB 빌드를 확인했다. 로컬 Firebase 설정을 사용했으며 Crashlytics 매핑 업로드는 제외했다. 커밋할 파일만 복사한 별도 환경에서도 Firebase 설정 파일 없이 Debug APK 빌드와 단위 테스트 11개를 통과했고, `FIREBASE_CONFIGURED`·`TELEMETRY_ALLOWED`가 모두 false인 것을 확인했다. 이 결과는 기기에서 동의 변경·이벤트 수신을 확인한 결과와 구분한다. [현재 검증 기록](../portfolio/validation.md#빌드와-자동-테스트-기록)
