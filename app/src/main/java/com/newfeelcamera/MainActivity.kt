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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.newfeelcamera.camera.CameraController
import com.newfeelcamera.ui.camera.CameraScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current

            val cameraController = remember {
                CameraController(this@MainActivity.applicationContext)
            }

            var hasCameraPermission = remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED,
                )
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                hasCameraPermission.value = granted
            }

            LaunchedEffect(Unit) {
                if (!hasCameraPermission.value) {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            CameraScreen(
                cameraController = cameraController,
                hasCameraPermission = hasCameraPermission.value,
            )
        }
    }
}
