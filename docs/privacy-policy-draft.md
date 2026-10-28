# FeelCamera 개인정보처리방침 초안

이 문서는 Firebase Analytics·Crashlytics를 포함하고 사용자 선택에 따라 수집하는 FeelCamera Release 빌드(`com.sseo.feelcamera`)와 이전 테스트 패키지를 기준으로 작성했다. 공개 개인정보처리방침과 패키지 정보·시행일을 함께 관리한다. 공개 전에 실제 배포 트랙의 모든 버전, GA4 보관·데이터 공유·Google Signals·Ads 연결 설정과 국외 처리 안내를 확인한다. 노션에 게시할 때는 이 안내 문단을 제외하고 아래 **개인정보처리방침**부터 복사한다.

---

# FeelCamera 개인정보처리방침

시행일: 2026년 10월 26일

FeelCamera(`com.sseo.feelcamera`, 이전 테스트 패키지 `com.newfeelcamera`)는 필름 효과를 적용해 사진을 촬영하고 기기에 저장하는 앱입니다. 개발자 표시명은 **SseO_KR**이며, 개인정보 관련 문의는 **skw4223@gmail.com**으로 받을 수 있습니다.

## 1. 앱이 접근하고 저장하는 정보

- **카메라:** 사진 촬영과 미리보기를 위해 사용자가 허용한 카메라 권한으로 영상에 접근합니다. 미리보기 영상은 촬영 기능을 위해 기기에서 처리합니다.
- **사진:** 촬영 결과는 기기의 공유 사진 저장 공간인 `Pictures/FeelCamera` 폴더에 JPEG로 저장됩니다. 앱은 이 폴더에 자신이 저장한 사진 중 가장 최근 사진을 찾아 화면에 표시합니다. 기기의 사진 앱이나 적절한 접근 권한을 가진 다른 앱에서도 저장된 사진을 볼 수 있습니다. FeelCamera는 사진 내용을 개발자 서버나 Firebase로 업로드하지 않으며, 사진 파일 경로를 분석 이벤트에 넣지 않습니다.
- **설정:** 시작 필름, 격자 표시, 셔터음, 사용 통계·오류 보고 선택을 앱의 기기 내 저장 공간에 보관합니다. 계정을 만들거나 이 설정을 개발자 서버로 전송하지 않습니다.

## 2. Google Play와 다른 앱

FeelCamera는 업데이트 가능 여부를 확인하기 위해 Google Play의 인앱 업데이트 기능을 사용합니다. 이 과정에서 Google Play는 업데이트 확인에 필요한 기기 메타데이터, 앱 버전, 설치된 모듈·애셋 팩 정보를 처리할 수 있습니다. 이 데이터는 Google Play가 자체 정책에 따라 처리하며, [Google 개인정보처리방침](https://policies.google.com/privacy)에서 관련 내용을 확인할 수 있습니다.

사용자가 앱의 최근 사진을 눌러 사진 보기 앱을 열면, 선택된 앱이 그 사진을 읽을 수 있도록 접근 권한을 전달합니다. 해당 앱이 사진을 처리하는 방식에는 그 앱의 정책이 적용됩니다. 기기에서 사진 백업 서비스를 켠 경우, 저장된 사진은 해당 서비스의 설정에 따라 백업될 수 있습니다.

Android의 백업 기능이 켜져 있으면 앱 설정이 사용자의 Google 계정 백업에 포함되고 복원될 수 있습니다. FeelCamera 개발자는 이 백업을 직접 운영하지 않습니다.

## 3. 선택에 따른 사용 통계와 오류 보고

앱은 Google Firebase Analytics와 Crashlytics를 포함합니다. 처음 실행할 때 **사용 통계**와 **오류 보고**를 각각 선택할 수 있으며, 기본 선택은 모두 꺼져 있습니다. 둘 다 허용하지 않으면 Firebase를 초기화하지 않습니다. 동의하지 않아도 모든 촬영 기능을 사용할 수 있고 설정에서 선택을 바꿀 수 있습니다.

- **사용 통계에 동의한 경우:** 앱 상호작용·세션·화면 조회, 앱 인스턴스 식별자, IP 주소에서 유추한 대략적인 위치 등 SDK 분석 데이터를 Google Analytics로 전송합니다. 촬영 성공 이벤트에는 필름 종류, 사진의 가로·세로 방향, 플래시 설정·지원 여부만 추가합니다. 사용 상황을 파악하고 기능을 개선하기 위한 목적이며 사진 내용은 포함하지 않습니다.
- **오류 보고에 동의한 경우:** 비정상 종료 스택, 앱·기기 상태 및 진단 정보, Crashlytics 설치 식별자, Firebase 설치 식별자와 세션 정보를 Google Firebase로 전송합니다. 기기에 보관되어 있던 미전송 오류 보고서가 함께 전송될 수 있습니다. 앱 오류와 영향을 받은 사용자 규모를 파악하고 수정하기 위한 목적입니다. 앱에 로그인 계정이나 사용자 이름을 연결하지 않습니다.
- **선택 변경:** 사용 통계를 끄면 이후 Analytics 이벤트 수집을 중단하고 기기에 저장된 Analytics 데이터를 초기화합니다. 오류 보고를 끄는 변경은 Firebase SDK의 동작에 따라 **앱을 완전히 종료한 뒤 다시 실행할 때부터** 적용됩니다. 수집 중단은 이미 서버에 전송된 데이터 삭제와는 별개입니다.

현재 앱에는 광고와 인앱결제가 없습니다. 광고 ID 수집과 광고 개인화를 사용하지 않으며 정확한 위치 권한을 요청하지 않습니다. 단, Analytics의 대략적인 위치 추정은 위치 권한 없이 IP 주소를 통해 이루어질 수 있습니다.

선택한 분석·오류 데이터는 HTTPS/TLS로 암호화되어 Google의 서비스 인프라로 전송되며 한국 외 지역에서 처리될 수 있습니다. 서비스와 처리 위치에 관한 정보는 [Firebase 개인정보 및 보안](https://firebase.google.com/support/privacy), [Firebase 데이터 처리 및 보안 약관](https://firebase.google.com/terms/data-processing-terms), [Google 개인정보처리방침](https://policies.google.com/privacy)을 확인할 수 있습니다.

## 4. 보관 및 삭제

촬영한 사진은 사용자가 기기의 사진 앱이나 파일 관리 기능으로 삭제할 때까지 기기에 남습니다. 앱을 삭제해도 `Pictures/FeelCamera`에 저장된 사진은 남을 수 있습니다. 앱 설정은 앱 데이터 삭제 또는 앱 제거 시 기기에서 삭제되지만, Android 백업을 사용했다면 기기 설정에 따라 복원될 수 있습니다.

Analytics의 이벤트 데이터 보관 설정은 2개월, 사용자 데이터 보관 설정은 14개월입니다. 새 사용자 활동이 있으면 사용자 데이터 보관 기간이 다시 시작되므로 계속 사용하는 동안 보관 기간이 연장될 수 있습니다. 이 설정은 표준 집계 보고서 전체의 보관 기간을 의미하지 않습니다. [Analytics 데이터 보관 안내](https://support.google.com/analytics/answer/7667196?hl=ko)

Firebase는 Crashlytics의 오류 스택과 관련 식별자를 90일간 보관한 뒤 운영·백업 시스템에서 삭제하는 절차를 시작합니다. SDK 설치·세션 서비스가 처리하는 데이터는 각 서비스의 보관 정책도 적용됩니다. 개발자가 별도 로그인 식별자를 연결하지 않으므로 문의 이메일만으로 개별 분석·오류 데이터를 찾아 삭제할 수 있다고 보장하지는 않습니다. [Firebase 보관 정책](https://firebase.google.com/support/privacy)

이메일로 문의하면 답변을 위해 이메일 주소와 문의 내용을 확인합니다. 문의 처리 목적이 끝나면 해당 내용을 삭제하며, 삭제 요청은 아래 이메일로 보낼 수 있습니다. Google Play 및 기기 백업 서비스가 처리하는 데이터는 각 서비스의 정책과 사용자 설정에 따릅니다.

## 5. 보호 조치와 문의

사진 처리는 기기에서 수행하며, 앱 설정은 앱의 기기 내 저장 공간에 보관합니다. 개발자가 사진과 설정을 저장하는 별도 서버는 운영하지 않습니다. 카메라 권한은 촬영 기능에 필요하며, 사용자는 Android 앱 설정에서 언제든지 권한을 변경할 수 있습니다.

개인정보 처리에 관한 질문이나 개발자가 보관하는 문의 이메일의 삭제 요청은 **skw4223@gmail.com**으로 보내주세요. 이 방침이 바뀌면 이 페이지의 시행일과 내용을 갱신합니다.

---

# FeelCamera Privacy Policy

Effective date: October 26, 2026

FeelCamera (`com.sseo.feelcamera`; previous test package: `com.newfeelcamera`) is a camera app that applies film effects and saves photos on your device. The developer name is **SseO_KR**. For privacy questions, contact **skw4223@gmail.com**.

## 1. Information the app accesses and stores

- **Camera:** With your permission, the app accesses the camera to show a preview and take photos. Preview frames are processed on your device for the camera feature.
- **Photos:** The app saves captured JPEG photos in the shared `Pictures/FeelCamera` folder on your device. It looks up the most recent photo it saved to show it in the app. Your photo app and other apps with appropriate access can also view saved photos. FeelCamera does not upload photo contents to the developer's servers or Firebase, or include photo file paths in analytics events.
- **Settings:** The app stores your starting film, grid, shutter sound, and usage statistics/crash reporting choices locally. It does not require an account or send these settings to a developer server.

## 2. Google Play and other apps

FeelCamera uses Google Play's in-app update feature to check whether an update is available. For this request, Google Play may process device metadata, the app version, and the list of installed modules and asset packs. Google Play handles this information under its own policies. See the [Google Privacy Policy](https://policies.google.com/privacy).

When you tap the recent photo to open it in another photo viewer, the app gives the selected viewer permission to read that photo. The viewer's own privacy policy applies to its handling of the photo. If you have enabled a photo backup service on your device, that service may back up saved photos according to your settings.

If Android backup is enabled, your app settings may be included in a backup associated with your Google account and may be restored later. The FeelCamera developer does not operate this backup service.

## 3. Optional usage statistics and crash reports

The app includes Google Firebase Analytics and Crashlytics. At first launch, you can choose **usage statistics** and **crash reports** separately. Both choices start off. Firebase is not initialized if you allow neither. Every camera feature works without consent, and you can change your choices in Settings.

- **With usage statistics consent:** The app sends interactions, sessions, screen views, an app-instance identifier, and approximate location inferred from IP addresses to Google Analytics. Each successful photo event adds only the film, photo orientation, and flash setting/availability. This helps understand usage and improve features. It does not include photo contents.
- **With crash reporting consent:** The app sends crash stack traces, app/device state and diagnostics, a Crashlytics installation identifier, and Firebase installation/session information to Google Firebase to identify and fix errors. Reports already stored on your device may also be sent when you allow crash reporting. No login account or user name is attached.
- **Changing choices:** Turning off usage statistics stops subsequent Analytics collection and resets local Analytics data. Turning off crash reports takes effect **after you fully close and reopen the app**, as specified by the Firebase SDK. Stopping collection does not delete data already sent to the service.

The app has no ads or in-app purchases. Advertising ID collection and ad personalization are disabled. It does not request precise location permissions, but Analytics may infer approximate location from IP addresses without location permissions.

Selected analytics/crash data is encrypted in transit using HTTPS/TLS and sent to Google's service infrastructure, where it may be processed outside South Korea. See [Firebase Privacy and Security](https://firebase.google.com/support/privacy), [Firebase Data Processing and Security Terms](https://firebase.google.com/terms/data-processing-terms), and the [Google Privacy Policy](https://policies.google.com/privacy) for information about these services and processing locations.

## 4. Retention and deletion

Captured photos remain on your device until you delete them using your photo or file management app. Photos in `Pictures/FeelCamera` may remain after you uninstall FeelCamera. Local app settings are removed from the device when you clear the app's data or uninstall it, but they may be restored if Android backup was enabled.

Analytics event data retention is set to 2 months and user data retention to 14 months. New user activity resets the user data retention period, so continued activity can extend it. These settings do not specify a retention limit for all standard aggregated reports. See [Analytics data retention](https://support.google.com/analytics/answer/7667196?hl=en).

Firebase retains Crashlytics crash stacks and associated identifiers for 90 days before beginning removal from live and backup systems. Installation/session services also apply their own retention policies. The developer does not attach a login identifier, so an email inquiry alone may not allow the developer to identify and delete individual analytics/crash records. See [Firebase retention information](https://firebase.google.com/support/privacy).

If you email the developer, your email address and message are used to respond to your inquiry. They are deleted when the inquiry has been handled. You can request deletion at the email address below. Data handled by Google Play or device backup services follows their policies and your device settings.

## 5. Safeguards and contact

Photo processing takes place on your device, and app settings are stored in the app's local storage. The developer does not operate a separate server that stores your photos or settings. Camera access is needed for photography; you can change the permission at any time in Android app settings.

For privacy questions or requests to delete inquiry emails held by the developer, contact **skw4223@gmail.com**. If this policy changes, its effective date and content will be updated on this page.
