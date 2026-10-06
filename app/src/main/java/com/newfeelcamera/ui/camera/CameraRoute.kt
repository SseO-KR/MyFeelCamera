package com.newfeelcamera.ui.camera

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.view.OrientationEventListener
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.UseCase
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
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
    onOpenSettings: () -> Unit,
    registerVolumeShutterHandler: (() -> Unit) -> (() -> Unit),
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 화면에 다시 들어올 때 이전 촬영 결과를 새 알림으로 표시하지 않는다.
    var lastNotifiedPhotoUri by remember { mutableStateOf(uiState.lastSavedPhotoUri) }
    var lastNotifiedError by remember { mutableStateOf(uiState.captureErrorMessage) }

    LaunchedEffect(uiState.lastSavedPhotoUri, uiState.captureErrorMessage) {
        val photoUri = uiState.lastSavedPhotoUri
        val errorMessage = uiState.captureErrorMessage

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            when {
                errorMessage != null && errorMessage != lastNotifiedError -> {
                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                }

                photoUri != null && photoUri != lastNotifiedPhotoUri -> {
                    Toast.makeText(context, "사진이 저장되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        lastNotifiedPhotoUri = photoUri
        lastNotifiedError = errorMessage
    }

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

    LifecycleResumeEffect(viewModel) {
        viewModel.refreshLatestPhoto()
        val unregister = registerVolumeShutterHandler {
            viewModel.onIntent(CameraAction.ShutterClicked)
        }
        onPauseOrDispose { unregister() }
    }

    CameraScreen(
        uiState = uiState,
        onBindCamera = viewModel::bindCamera,
        onDisplayRotationChanged = { rotation ->
            viewModel.onIntent(
                CameraAction.DisplayRotationChanged(rotation),
            )
        },
        onFilmSelected = { film ->
            viewModel.onIntent(CameraAction.FilmSelected(film))
        },
        onZoomInClick = { viewModel.onIntent(CameraAction.ZoomInClicked) },
        onZoomOutClick = { viewModel.onIntent(CameraAction.ZoomOutClicked) },
        onFlashClick = { viewModel.onIntent(CameraAction.FlashClicked) },
        onGalleryClick = {
            uiState.latestPhotoUri?.let { uri ->
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "image/jpeg")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(intent)
                } catch (error: ActivityNotFoundException) {
                    Toast.makeText(context, "사진을 열 앱이 없습니다.", Toast.LENGTH_SHORT).show()
                } catch (error: SecurityException) {
                    Toast.makeText(context, "사진을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onShutterClick = {
            viewModel.onIntent(CameraAction.ShutterClicked)
        },
        onSettingsClick = onOpenSettings,
    )
}
