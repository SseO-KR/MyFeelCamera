# 앱 소개 이미지 제작 방식

[프로젝트 소개](../../README.md)

새 Play 계정의 등록 대상은 `com.sseo.feelcamera`, 버전 `1.0.0 (1)`입니다. 기존 캡처와 ZIP은 `com.newfeelcamera`의 과거 자료이며 새 패키지를 실행한 증거로 취급하지 않습니다. 메타데이터의 `app`은 촬영 당시 앱을, `target_app`은 이번 등록 대상을 나타냅니다. 이미지 제작 스크립트는 현재 Gradle 설정에서 등록 대상 정보를 읽습니다.

README의 WARM·DUSK 이미지는 직접 촬영한 사진을 보여주기 위해 만든 앱 소개용 합성 이미지입니다. 에뮬레이터에서 캡처한 앱 UI를 바탕으로, 프리뷰 영역에 직접 찍은 사진을 덮어씌웠습니다.

| 이미지 | 제작 방식 |
| --- | --- |
| [WARM](en-US/phone/01-warm.png) | 에뮬레이터 UI와 직접 촬영한 사진 합성 |
| [DUSK](en-US/phone/02-dusk.png) | 에뮬레이터 UI와 직접 촬영한 사진 합성 |
| [설정](en-US/phone/04-settings.png) | 에뮬레이터에서 캡처한 설정 화면 |

이 이미지는 앱의 화면 구성과 사진을 함께 소개하는 용도입니다. 같은 순간의 실시간 프리뷰와 저장 결과를 비교하려면 별도로 연결된 촬영 자료가 필요합니다. 기존 기술 검증 자료는 [앱 화면·저장 JPEG 출처](../../docs/assets/app/README.md)에 정리했습니다.

UI의 원본 캡처 환경은 앱 1.0.3 (4)·Android API 36 에뮬레이터이며 현재 코드 버전 1.0.0 (1)과 구분합니다. [캡처 환경·파일 정보](capture-manifest.json)의 `capture`는 원본 UI 캡처 단계를, `readme_composition`은 이후 소개 이미지 제작 방식을 기록합니다. 다른 태블릿 이미지의 제작 방식까지 이 설명으로 확대하지 않습니다.

## 캡처 패키지 재생성

Node.js, `sharp`, 시스템 `zip` 명령이 필요합니다. `sharp`가 기본 모듈 경로에 없으면 `SHARP_MODULE`에 모듈 경로를 지정합니다. 아래 명령은 이미 저장된 캡처 파일만 읽으며 에뮬레이터를 실행하지 않습니다.

```sh
node design/play-store/build-capture-package.cjs /path/to/captured/screenshots
```

입력은 `phone-01-warm.png`처럼 기기 그룹(`phone`, `tablet-7`, `tablet-10`)과 화면(`01-warm`, `02-dusk`, `03-grid`, `04-settings`)을 조합한 12개 파일입니다. 경로를 생략하면 과거 캡처 폴더 `build/reports/newfeel-emulator-check/store-20261009/screenshots`를 읽습니다. 스크립트의 캡처 환경 기록은 위 1.0.3 (4) 자료를 기준으로 합니다.

출력 이미지·매니페스트·미리보기와 `feelcamera-play-store-complete-en.zip`은 프로젝트 루트의 `build/play-store-capture-package/`에 생성합니다. 출력 매니페스트는 원본 UI 캡처의 정보를 기록합니다. 이 폴더의 README 소개 이미지, `capture-manifest.json`과 `asset-manifest.json`의 합성·출처 기록, 보관 ZIP은 재생성 과정에서 수정하지 않습니다.

보존 동작과 생성된 ZIP은 다음 회귀 검사로 확인합니다.

```sh
node --test design/play-store/build-capture-package.test.cjs
```
