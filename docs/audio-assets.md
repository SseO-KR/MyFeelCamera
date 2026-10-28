# 촬영음 출처

- `app/src/main/res/raw/shutter_click.wav`: Minolta XG-1 필름 카메라 촬영음을 녹음한 [Film Camera Click Shutter](https://freesound.org/people/drewtait/sounds/676375/)에서 셔터 구간을 추출해 시작과 끝을 페이드 처리했다. 원저작자: drewtait. 원본은 [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/)으로 공개되었다.
- 설정의 **무음 촬영**을 켜면 앱은 촬영음을 재생하지 않는다. 끄면 이전 음량으로 복원하며, 설정은 앱을 다시 실행해도 유지된다. 기존 `끔` 설정도 무음 상태로 유지한다.
- 무음 촬영이 꺼져 있고 `CameraInfo.mustPlayShutterSound()`가 `true`인 기기에서는 위 파일 대신 Android `MediaActionSound.SHUTTER_CLICK`을 재생한다. 무음 설정은 두 재생 경로에 모두 우선 적용한다.
