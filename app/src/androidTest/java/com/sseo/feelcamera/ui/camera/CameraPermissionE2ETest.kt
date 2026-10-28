package com.sseo.feelcamera.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.sseo.feelcamera.camera.filter.FilmPreset
import com.sseo.feelcamera.testing.CameraE2ESupport
import java.util.regex.Pattern
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** CI가 별도 instrumentation 실행 전에 권한을 취소한다. 실행 중인 프로세스에서 취소하지 않는다. */
@RunWith(AndroidJUnit4::class)
class CameraPermissionE2ETest : CameraE2ESupport() {
    @Test fun denialBlocksShutterAndGrantingPermissionRecoversRealCamera() {
        assertEquals("Run the permission suite with CAMERA revoked before instrumentation starts",
            PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.CAMERA))
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("shutter").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("shutter").assertIsNotEnabled()
        compose.onNodeWithTag("camera_recovery").performClick()
        val deny = device.wait(Until.findObject(By.res(Pattern.compile(".*:id/permission_deny_button"))), 10_000)
        assertNotNull("Camera permission dialog did not appear", deny)
        deny.click()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("camera_recovery").fetchSemanticsNodes().singleOrNull()
                ?.config?.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) == false
        }
        compose.onNodeWithTag("shutter").assertIsNotEnabled()
        compose.onNodeWithTag("camera_recovery").performClick()
        val allow = device.wait(Until.findObject(By.res(Pattern.compile(
            ".*:id/permission_allow_(foreground_only_)?button"))), 10_000)
        assertNotNull("Permission retry did not offer allow", allow)
        allow.click()
        waitForCamera(FilmPreset.WARM)
        takePhoto(FilmPreset.WARM)
    }
}
