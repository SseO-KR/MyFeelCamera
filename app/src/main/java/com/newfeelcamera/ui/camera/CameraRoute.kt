package com.newfeelcamera.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.view.OrientationEventListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.UseCase
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DeviceOrientationEffect(
    onTargetRotationChanged: (Int) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnTargetRotationChanged by rememberUpdatedState(onTargetRotationChanged)

    DisposableEffect(
        context,
        lifecycleOwner,
    ) {
        var lastTargetRotation: Int? = null

        val orientationListener =
            object : OrientationEventListener(context) {
                override fun onOrientationChanged(orientation: Int) {
                    if (orientation == ORIENTATION_UNKNOWN) return

                    val targetRotation =
                        UseCase.snapToSurfaceRotation(orientation)

                    if (lastTargetRotation == targetRotation) return
                    lastTargetRotation = targetRotation

                    currentOnTargetRotationChanged(targetRotation)
                }
            }

        fun enableOrientationListener() {
            if (orientationListener.canDetectOrientation()) {
                orientationListener.enable()
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    enableOrientationListener()
                }

                Lifecycle.Event.ON_STOP -> {
                    orientationListener.disable()
                }

                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        if (
            lifecycleOwner.lifecycle.currentState
                .isAtLeast(Lifecycle.State.STARTED)
        ) {
            enableOrientationListener()
        }

        onDispose {
            orientationListener.disable()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
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

    DeviceOrientationEffect { targetRotation ->
        viewModel.onIntent(CameraAction.DeviceOrientationChanged(targetRotation))
    }

    CameraScreen(
        uiState = uiState,
        onBindCamera = viewModel::bindCamera,
        onDisplayRotationChanged = { rotation ->
            viewModel.onIntent(
                CameraAction.DisplayRotationChanged(rotation),
            )
        },
        onShutterClick = {
            viewModel.onIntent(CameraAction.ShutterClicked)
        },
    )
}
