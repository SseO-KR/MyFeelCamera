# 앱 이미지 출처

[프로젝트 소개](../../../README.md) · [검증 결과](../../portfolio/validation.md)

이 폴더는 기존 앱 점검에서 얻은 실제 화면과 저장 JPEG를 문서용으로 보존합니다. 생성형 이미지나 앱 화면을 합성한 목업은 사용하지 않았습니다. 원본 파일을 이름만 바꿔 복사했으며, 픽셀·색감·메타데이터는 수정하지 않았습니다. 문서의 HTML `width` 속성은 표시 크기만 조절합니다.

## 촬영 환경

- 날짜: 2026-10-08
- 앱: FeelCamera 1.0.1 (2), Debug
- 실행 식별자: `2026-10-08-release-fixes`
- 환경: Android API 36 / arm64 / SwiftShader / 1080×2400, 420 dpi
- 카메라: 에뮬레이터가 공급한 가상 그림. 실기기 풍경 사진이나 색 정확도 기준물이 아닙니다.
- 원본 위치: 로컬 `build/reports/newfeel-emulator-check/2026-10-08-release-fixes/`. 원본 폴더는 Git 추적 대상이 아닙니다.

## 파일 대응

| 문서 파일 | 원본 파일 | 크기 | 용도 |
| --- | --- | --- | --- |
| [camera-warm.png](camera-warm.png) | `warm_0-preview.png` | 1080×2400 | WARM 촬영 직전 화면 |
| [camera-dusk.png](camera-dusk.png) | `dusk_0-preview.png` | 1080×2400 | DUSK 촬영 직전 화면 |
| [settings.png](settings.png) | `09-settings-initial.png` | 1080×2400 | 시작 필름·격자·셔터음 설정 |
| [camera-rotated.png](camera-rotated.png) | `warm_90-preview.png` | 1080×2400 | 90° 센서 입력에서의 화면 |
| [photo-warm.jpg](photo-warm.jpg) | `warm_0.jpg` | 1392×1856 | WARM 세로 저장 사진 |
| [photo-dusk.jpg](photo-dusk.jpg) | `dusk_0.jpg` | 1392×1856 | DUSK 세로 저장 사진 |
| [photo-warm-landscape.jpg](photo-warm-landscape.jpg) | `warm_90.jpg` | 1856×1392 | WARM 가로 저장 사진 |

각 필름의 프리뷰 파일과 JPEG는 원본 보고서에서 같은 촬영 건으로 연결한 자료입니다. 서로 다른 필름 간 촬영은 별도 시점이므로 픽셀 단위 비교 기준으로 사용하지 않습니다.

## 원본 동일성

복사 시 원본과 아래 파일의 SHA-256 일치를 확인했습니다.

```text
9362658350d7adbf99eb40abc6923c70c189e5c4cd3273502d7a1a116058eca2  camera-warm.png
551f7e8b3b5b9bfb889af65998d5409e28113a2547329fdada98baf69d9cb582  camera-dusk.png
8a7184dfbd890657bc9b2f1d2167d07c3c4e258a0d6990828b2be543a9c84e05  settings.png
7cb0ec8e58443530ce55f18a2d36f97783b5a7e78aa7f62e9e27cbe98af05a7a  camera-rotated.png
cb6ce78bab34492b10fe3de4d2357b84cf40d2edcb6db2de8c64ae5c52c624df  photo-warm.jpg
3a3f9473d516c7a7a444f6c554e30995c9f9d8dad1b2c78738fbe6a075f10753  photo-dusk.jpg
3233eb2fb7483cbd657d80055cd3daec0ef8fed39482d87e7d24ae2eb23e430d  photo-warm-landscape.jpg
```

화면이 변경되면 검증한 새 버전의 캡처로 교체하고 날짜·앱 버전·촬영 환경·원본 대응표도 함께 갱신합니다. 새 캡처를 얻기 위한 에뮬레이터 조작은 [프로젝트 작업 지침](../../../AGENTS.md)을 따릅니다.
