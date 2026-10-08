---
name: newfeel-emulator-check
description: Run one evidence-based Android emulator QA pass for NewFeelCamera's preview, WARM/DUSK films, saved photos, rotation, shutters, settings, lifecycle, and unlimited free capture. Use when asked to check this project's basic app behavior or run its emulator checklist.
---

# 필카메라 에뮬레이터 테스트

이 프로젝트의 `AGENTS.md`에 따라, 해당 작업에 대한 사용자의 명시적 허가가 있을 때만 에뮬레이터 실행·조작, 대상 ADB 명령, APK 설치·앱 실행, 스크린샷 및 이 체크리스트를 수행한다. 허가 전에는 빌드·정적 검사·단위 테스트까지만 진행한다.

Run all applicable cases in `docs/emulator-test-checklist.md` from one invocation. Treat that document as the maintained source for test IDs, actions, and pass criteria. A build or green unit tests alone do not establish that the camera app works. Record each check as **PASS**, **FAIL**, or **UNVERIFIED**; never turn an unavailable camera feed, missing sensor control, or uninspected photo into PASS. Continue independent checks after a failure when the app still runs.

## Preparation

1. Work from this repository's root. Read the current `docs/00-product-requirements.md`, `docs/03-rendering-and-lut.md`, and relevant app code for changed behavior. The current product is rear-camera only with WARM and DUSK; flash, zoom, and in-app gallery controls are visual placeholders. Do not report those controls as tested features until code connects them.
2. Identify the exact emulator, API level, build variant, Git revision, and existing app/photo state. Use the available emulator UI control for taps, swipes, rotation, key presses, and screenshots. Use build/test and device tools for logs, image files, and metadata when available. If a tool cannot operate the emulator, mark the affected cases UNVERIFIED with the concrete reason.
3. Create `build/reports/newfeel-emulator-check/<run-id>/`. Save `report.md`, emulator screenshots, pulled JPEGs, useful logs, and command results there. `build/` is ignored by Git. Do not overwrite an earlier run.
4. Prefer a dedicated test AVD or restorable snapshot. Record existing photos. Never clear user app data or delete user photos to make room. Use a disposable AVD for permission reset, old 24-shot-data migration, or injected-failure cases. If isolation is unavailable, test what is safe and mark the rest UNVERIFIED.
5. Build the current app and run `:app:testDebugUnitTest` and `:app:connectedDebugAndroidTest` on the target emulator when possible. Install the APK built from the inspected working tree. Capture build/test outcomes separately from the live-app outcomes. If build or install fails, report that as a failure and assess any still-accessible existing app only as a different build.

## One-pass checklist

Follow P01–P14 in `docs/emulator-test-checklist.md`. For every capture, note the selected film, expected physical orientation, screenshot of the preview, and exact saved JPEG. The app writes JPEGs to `Pictures/NewFeelCamera`; match new files by timestamp and verify their content, not merely their presence.

## Evidence and verdict

- Give every ID a status, observed result, evidence file path, and any reproduction steps. For P04/P05, include a matrix for the eight film/direction combinations; indicate when a physical sensor orientation was unavailable.
- Compare preview screenshots with the corresponding decoded JPEG visually. Where a fixed scene and reference are available, compare a few stable color regions as supporting evidence, while allowing exposure and JPEG variation. Do not invent a pixel-perfect threshold without calibration.
- Use image metadata and visual landmarks together for rotation. The app renders crop and rotation into JPEG pixels, so EXIF orientation may be absent or normal; that is not by itself a defect.
- Summarize failures first, then passes and unverified cases. Include the build/test result, tested emulator/build, and links to `report.md` and the most useful screenshots/photos. Distinguish emulator evidence from checks that still need a real device, especially sensor behavior, camera color, and GPU differences.
- Write the report and user-facing summary in Korean unless the user requests another language.
- Finish even if some checks cannot be automated. State exactly what was observed and what prevented the remaining checks. Do not change production code during a verification-only request unless the user also asks for a fix.
