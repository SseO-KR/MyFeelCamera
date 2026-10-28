#!/usr/bin/env bash
# Run only on an explicitly authorized emulator. GitHub Actions provisions it.
set -euo pipefail

cd "$(dirname "$0")/.."
app_package=com.sseo.feelcamera
test_package="${app_package}.test"
test_namespace="${app_package}.testing"
permission_class="${app_package}.ui.camera.CameraPermissionE2ETest"
restart_class="${app_package}.ui.settings.SettingsRestartE2ETest"
suite_mode="${1:-standard}"
case "$suite_mode" in standard|core|extended) ;; *) echo 'Usage: bash scripts/run_android_ci.sh [standard|core|extended]' >&2; exit 2 ;; esac
fixture_camera=true
if [[ "$suite_mode" == standard ]]; then fixture_camera=false; fi

mkdir -p app/build/ci-device
adb wait-for-device
if [[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ]]; then
  echo 'This suite requires an Android emulator. core/extended also require the fixed camera input.' >&2
  exit 2
fi
# Emulator의 자체 adb -e 설정은 무선 연결 기기가 있으면 대상을 고르지 못할 수 있다.
# 이 스크립트는 ANDROID_SERIAL로 지정한 기기를 깨우고 검사 중 화면 꺼짐을 방지한다.
adb shell settings put system screen_off_timeout 2147483647
adb shell input keyevent KEYCODE_WAKEUP
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant "$app_package" android.permission.CAMERA

logcat_pid=''
cleanup() {
  if [[ -n "$logcat_pid" ]]; then kill "$logcat_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

run_suite() {
  # A crash must never reuse a successful report left by an earlier run.
  local label="$1-$(python3 -c 'import uuid; print(uuid.uuid4().hex[:8])')"
  local expected_count="$2"
  shift 2
  local output="app/build/ci-device/$label"
  mkdir -p "$output"
  # A new instrumentation process is started for each phase; app data is retained.
  adb shell am force-stop "$app_package"
  adb logcat -c
  adb logcat -v threadtime > "$output/logcat.txt" &
  logcat_pid=$!
  local status=0
  adb shell am instrument -w -r \
    -e listener "$test_namespace.CiResultListener" \
    -e fixtureCamera "$fixture_camera" -e artifactLabel "$label" \
    "$@" "$test_package/androidx.test.runner.AndroidJUnitRunner" \
    | tee "$output/instrumentation.txt" || status=$?
  kill "$logcat_pid" 2>/dev/null || true
  wait "$logcat_pid" 2>/dev/null || true
  logcat_pid=''
  # Keep logs even when the app crashes before producing a report.
  adb pull "/sdcard/Android/data/$app_package/files/test-artifacts/$label" app/build/ci-device/ || true
  if [[ "$status" -ne 0 ]]; then return "$status"; fi
  python3 - "$output/junit.xml" "$expected_count" <<'PY'
import sys
from pathlib import Path
import xml.etree.ElementTree as ET
path = Path(sys.argv[1])
expected = int(sys.argv[2])
if not path.is_file():
    raise SystemExit(f"No instrumentation report: {path}. Check instrumentation.txt and logcat.txt.")
suite = ET.parse(path).getroot()
tests = int(suite.get("tests", "0"))
failures = int(suite.get("failures", "0")) + int(suite.get("errors", "0"))
skipped = int(suite.get("skipped", "0"))
print(f"{path.parent.name}: {tests} tests, {failures} failures, {skipped} skipped")
if tests != expected:
    raise SystemExit(f"Expected {expected} tests, got {tests}. Check the test selection.")
if failures or skipped:
    raise SystemExit(1)
PY
}

if [[ "$suite_mode" == standard ]]; then
  # Confirmed camera-independent suites only. New E2E classes cannot enter CI accidentally.
  standard_classes="$app_package.camera.capture.CaptureGeometryRenderingTest"
  standard_classes+=",$app_package.camera.capture.FilmRenderingTest"
  standard_classes+=",$app_package.camera.capture.PhotoSaveIntegrationTest"
  standard_classes+=",$app_package.camera.gl.CameraRendererLifecycleTest"
  standard_classes+=",$app_package.camera.gl.PreviewRotationTest"
  standard_classes+=",$app_package.ui.camera.CameraScreenUiTest"
  standard_classes+=",$app_package.ui.settings.SettingsUiTest"
  run_suite standard 31 -e class "$standard_classes"
else
  excluded_annotations="$test_namespace.ProcessRestartTest"
  expected_count=37
  if [[ "$suite_mode" == core ]]; then
    excluded_annotations+=",$test_namespace.ExtendedCameraTest"
    expected_count=33
  fi
  run_suite "$suite_mode" "$expected_count" -e notAnnotation "$excluded_annotations" -e notClass "$permission_class"
fi

if [[ "$suite_mode" == extended ]]; then
  # Revoke outside instrumentation: revocation terminates the app process.
  adb shell pm revoke "$app_package" android.permission.CAMERA
  adb shell pm clear-permission-flags "$app_package" android.permission.CAMERA user-set user-fixed
  run_suite permission 1 -e class "$permission_class"
  adb shell pm grant "$app_package" android.permission.CAMERA
  run_suite settings-write 1 -e class "$restart_class#writeSettingsBeforeProcessRestart"
  run_suite settings-read 1 -e class "$restart_class#readSettingsAfterProcessRestart"
fi
