# Firebase 운영 모니터링 연결

## 범위와 결정

- 프로젝트 `newfeelcamera`, Android 패키지 `com.newfeelcamera`에 Analytics와 Crashlytics 기본 SDK를 연결한다. Auth, Firestore, Storage, Performance, Remote Config, 결제·무료 한도 변경은 포함하지 않는다. **(User Request / Implementation Decision)**
- `app/google-services.json`은 로컬에만 두고 기존 `.gitignore`를 유지한다. 공개 소스 빌드에서는 이 파일이 없으면 Firebase 플러그인 적용과 SDK 의존성을 함께 생략한다. `-PfirebaseEnabled=false`로도 같은 경로를 검증할 수 있다. 앱 소스에는 아직 Firebase API 직접 호출을 추가하지 않는다. **(Implementation Decision)**
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
- [ ] 테스트 전용 크래시를 발생시키고 앱 재실행 후 Crashlytics 콘솔에서 수신 확인. 이 연결 단계에서는 강제 크래시 코드를 앱에 넣지 않는다.

빌드 성공과 콘솔 수신 성공은 구분한다. SDK 추가만으로 촬영 성공/실패·필름 선택 이벤트 또는 catch된 예외가 자동 보고되지는 않는다. 다음 단계에서 필요한 지점만 명시적으로 연결한다. 사진, 파일 URI/경로, 구매 토큰, 사용자 ID를 사용자 정의 로그에 넣지 않는다. Java/Kotlin 크래시 기본 연결이며 native GL 드라이버 크래시용 NDK 심볼 처리까지 완료한 것은 아니다.

2026-09-27: AGP 9.3.1에서 Firebase 포함/제외 `:app:assembleDebug` 모두 통과했다. 최종 일반 Debug Manifest는 수집 `false`, Release Manifest는 `true`, 검증 옵션을 켠 Debug Manifest는 `true`였다. 기본 연결 APK의 Google Services 리소스와 초기화 Provider, Firebase 제외 APK의 해당 항목 미포함을 확인했다. 광고 ID·AdServices 광고 ID/기여 분석 권한은 최종 Manifest에서 제외됐다. 검증용 옵션을 해제하고 일반 Debug APK로 다시 빌드했다. 기기 설치·실행·콘솔 수신 테스트는 아직 수행하지 않았다. **(Observed / Verification Limit)**

Crashlytics 자동 수집 off는 보고서 영구 삭제 정책이 아니다. SDK가 로컬에 보존한 미전송 보고서는 나중에 수집을 켤 때 전송될 수 있으므로, 운영 앱과 같은 설치 데이터에서 테스트 크래시를 반복하지 않는다. 별도 dev 프로젝트/테스트 설치 환경 분리는 출시 전에 검토한다. **(Firebase API Contract / Verify First)**

출시 전에 개인정보처리방침, Play 데이터 보안 신고, 배포 지역별 동의 요구를 검토한다. 현재는 운영 프로젝트 하나이며 별도 dev 프로젝트는 아직 생성하지 않았다. 무료 한도·구매 권한의 source of truth는 Firebase가 아니다. **(Verify First / Scope Boundary)**

## 공식 근거

- [Android 연결·BoM·KTX 안내](https://firebase.google.com/docs/android/setup)
- [Crashlytics 연결과 테스트 크래시](https://firebase.google.com/docs/crashlytics/android/get-started)
- [Analytics 수집/광고 ID 설정](https://firebase.google.com/docs/analytics/android/configure-data-collection)
- [Crashlytics 수집 제어](https://firebase.google.com/docs/crashlytics/android/customize-crash-reports)
