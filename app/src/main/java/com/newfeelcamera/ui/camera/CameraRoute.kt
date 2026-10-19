package com.newfeelcamera.ui.camera

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.OrientationEventListener
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.UseCase
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.newfeelcamera.R
import com.newfeelcamera.ui.design.FeelCameraDialog
import kotlinx.coroutines.flow.filterNotNull

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
    onCameraPermissionHandled: () -> Unit,
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalActivity.current
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }
    var permissionIntroDismissed by rememberSaveable { mutableStateOf(false) }
    var permissionRequestInFlight by rememberSaveable { mutableStateOf(false) }
    var permissionRecovery by remember { mutableStateOf(CameraPermissionRecovery.RequestPermission) }

    fun updatePermission(granted: Boolean) {
        if (granted) hasRequestedPermission = false
        viewModel.onIntent(CameraAction.CameraPermissionChanged(granted))
        permissionRecovery = cameraPermissionRecovery(
            hasRequestedPermission = hasRequestedPermission || uiState.hasRequestedCameraPermission,
            shouldShowRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } == true,
        )
    }

    fun hasCameraPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(viewModel, lifecycleOwner, context) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.pendingNotice.filterNotNull().collect { notice ->
                Toast.makeText(context, notice.messageRes, Toast.LENGTH_SHORT).show()
                viewModel.noticeShown(notice.id)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRequestInFlight = false
        updatePermission(granted)
        viewModel.saveCameraPermissionState(hasRequestedPermission = !granted)
        onCameraPermissionHandled()
    }

    fun requestCameraPermission() {
        if (permissionRequestInFlight) return
        permissionIntroDismissed = true
        hasRequestedPermission = true
        permissionRequestInFlight = true
        viewModel.saveCameraPermissionState(hasRequestedPermission = true)
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val showPermissionIntro = uiState.isSettingsLoaded &&
        !uiState.hasCameraPermission &&
        !uiState.hasSeenCameraPermissionIntro &&
        !permissionIntroDismissed

    LaunchedEffect(uiState.isSettingsLoaded, uiState.hasRequestedCameraPermission) {
        updatePermission(hasCameraPermission())
    }

    LaunchedEffect(
        uiState.isSettingsLoaded,
        uiState.hasCameraPermission,
        uiState.hasSeenCameraPermissionIntro,
        uiState.hasRequestedCameraPermission,
        showPermissionIntro,
        permissionRequestInFlight,
    ) {
        if (!uiState.isSettingsLoaded) return@LaunchedEffect
        if (uiState.hasCameraPermission &&
            (!uiState.hasSeenCameraPermissionIntro || uiState.hasRequestedCameraPermission)
        ) {
            viewModel.saveCameraPermissionState(hasRequestedPermission = false)
        }
        if (!showPermissionIntro && !permissionRequestInFlight) {
            onCameraPermissionHandled()
        }
    }

    DeviceOrientationEffect { targetRotation ->
        viewModel.onIntent(CameraAction.DeviceOrientationChanged(targetRotation))
    }

    LifecycleResumeEffect(viewModel) {
        updatePermission(hasCameraPermission())
        viewModel.refreshLatestPhoto()
        val unregister = registerVolumeShutterHandler {
            viewModel.onIntent(CameraAction.ShutterClicked)
        }
        onPauseOrDispose { unregister() }
    }

    CameraScreen(
        uiState = uiState,
        onBindCamera = viewModel::bindCamera,
        permissionRecovery = permissionRecovery,
        permissionRequestInFlight = permissionRequestInFlight || !uiState.isSettingsLoaded,
        onRecoverCameraPermission = {
            if (permissionRecovery == CameraPermissionRecovery.OpenAppSettings) {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:${context.packageName}".toUri(),
                )
                try {
                    context.startActivity(intent)
                } catch (error: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.app_settings_open_failed, Toast.LENGTH_SHORT).show()
                } catch (error: SecurityException) {
                    Toast.makeText(context, R.string.app_settings_open_failed, Toast.LENGTH_SHORT).show()
                }
            } else {
                requestCameraPermission()
            }
        },
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
                    Toast.makeText(context, R.string.no_photo_viewer, Toast.LENGTH_SHORT).show()
                } catch (error: SecurityException) {
                    Toast.makeText(context, R.string.photo_open_failed, Toast.LENGTH_SHORT).show()
                }
            }
        },
        onShutterClick = {
            viewModel.onIntent(CameraAction.ShutterClicked)
        },
        onSettingsClick = onOpenSettings,
    )

    if (showPermissionIntro) {
        FeelCameraDialog(
            title = stringResource(R.string.camera_permission_intro_title),
            message = stringResource(R.string.camera_permission_intro_message),
            confirmLabel = stringResource(R.string.start_camera),
            dismissLabel = stringResource(R.string.later),
            onConfirm = ::requestCameraPermission,
            onDismiss = {
                permissionIntroDismissed = true
                viewModel.saveCameraPermissionState(hasRequestedPermission = false)
            },
        )
    }
}
