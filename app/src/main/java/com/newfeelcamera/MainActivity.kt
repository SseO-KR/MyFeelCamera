package com.newfeelcamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.newfeelcamera.camera.CameraController
import com.newfeelcamera.ui.camera.CameraScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            // CameraController는 여기(MainActivity)에서 생성해서 CameraScreen에 전달한다 —
            // CameraScreen 내부에서 생성하지 않는다.
            val cameraController = remember { CameraController(context.applicationContext) }

            var hasCameraPermission by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED,
                )
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted -> hasCameraPermission = granted }

            LaunchedEffect(Unit) {
                if (!hasCameraPermission) {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            if (hasCameraPermission) {
                CameraScreen(cameraController = cameraController)
            }
            // TODO(P0-CAMERA-03 / Roadmap Step 1):
            // 권한이 거부됐을 때 보여줄 UI(재요청 안내 등)는 아직 없다 — 화면이 비어있는 상태로 둔다.
            // 관련: 00-product-requirements.md §3 항목1.
        }
    }
}
