package com.newfeelcamera.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CameraRoute(
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onIntent(
            CameraAction.CameraPermissionChanged(granted),
        )
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

        viewModel.onIntent(
            CameraAction.CameraPermissionChanged(granted),
        )

        if (!granted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    CameraScreen(
        uiState = uiState,
        onBindCamera = viewModel::bindCamera,
        onShutterClick = {
            viewModel.onIntent(CameraAction.ShutterClicked)
        },
    )
}
