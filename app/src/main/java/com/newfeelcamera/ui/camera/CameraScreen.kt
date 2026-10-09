package com.newfeelcamera.ui.camera

import android.hardware.display.DisplayManager
import android.net.Uri
import android.util.Size
import android.view.Surface
import androidx.camera.core.Preview
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.newfeelcamera.camera.filter.FilmPreset
import com.newfeelcamera.R
import com.newfeelcamera.camera.gl.CameraGlSurfaceView
import com.newfeelcamera.ui.camera.layout.CameraLayoutMode
import com.newfeelcamera.ui.camera.layout.cameraLayoutModeFor
import com.newfeelcamera.ui.design.Accent
import com.newfeelcamera.ui.design.Background
import com.newfeelcamera.ui.design.Hair
import com.newfeelcamera.ui.design.Ink
import com.newfeelcamera.ui.design.InkDim
import com.newfeelcamera.ui.design.PreviewBackground
import com.newfeelcamera.ui.design.Rail
import com.newfeelcamera.ui.labelRes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun CameraPreviewBindingEffect(
    hasCameraPermission: Boolean,
    surfaceProvider: Preview.SurfaceProvider?,
    onBindCamera: (LifecycleOwner, Preview.SurfaceProvider) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(
        lifecycleOwner,
        surfaceProvider,
        hasCameraPermission,
    ) {
        if (hasCameraPermission && surfaceProvider != null) {
            onBindCamera(
                lifecycleOwner,
                surfaceProvider,
            )
        }
    }
}

@Composable
private fun rememberDisplayRotation(): Int {
    val context = LocalContext.current
    val view = LocalView.current

    val displayManager = remember(context) {
        context.getSystemService(DisplayManager::class.java)
    }

    var displayRotation by remember(view) {
        mutableIntStateOf(
            view.display?.rotation ?: Surface.ROTATION_0,
        )
    }

    DisposableEffect(
        displayManager,
        view,
    ) {
        fun updateDisplayRotation() {
            displayRotation =
                view.display?.rotation ?: Surface.ROTATION_0
        }

        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit

            override fun onDisplayRemoved(displayId: Int) = Unit

            override fun onDisplayChanged(displayId: Int) {
                if (displayId == view.display?.displayId) {
                    updateDisplayRotation()
                }
            }
        }

        displayManager.registerDisplayListener(
            listener,
            null,
        )

        updateDisplayRotation()

        onDispose {
            displayManager.unregisterDisplayListener(listener)
        }
    }

    return displayRotation
}

@Composable
fun CameraScreen(
    uiState: CameraUiState,
    onBindCamera: (LifecycleOwner, Preview.SurfaceProvider) -> Unit,
    onDisplayRotationChanged: (Int) -> Unit,
    onFilmSelected: (FilmPreset) -> Unit,
    onZoomInClick: () -> Unit,
    onZoomOutClick: () -> Unit,
    onFlashClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onShutterClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!uiState.isSettingsLoaded) {
        Box(modifier = modifier.fillMaxSize().background(Background))
        return
    }

    val displayRotation = rememberDisplayRotation()
    val contentRotationDegrees = uiContentRotationDegrees(
        displayRotation = displayRotation,
        deviceTargetRotation = uiState.deviceTargetRotation,
    )

    LaunchedEffect(displayRotation) {
        onDisplayRotationChanged(displayRotation)
    }

    val surfaceProvider = remember {
        mutableStateOf<Preview.SurfaceProvider?>(null)
    }

    CameraPreviewBindingEffect(
        hasCameraPermission = uiState.hasCameraPermission,
        surfaceProvider = surfaceProvider.value,
        onBindCamera = onBindCamera,
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Rail)
            .safeDrawingPadding()
            .padding(bottom = 16.dp)
    ) {
        CameraControlLayout(
            layoutMode = cameraLayoutModeFor(
                width = maxWidth,
                height = maxHeight,
            ),
            displayRotation = displayRotation,
            contentRotationDegrees = contentRotationDegrees,
            previewMaxWidth = if (minOf(maxWidth, maxHeight) >= 600.dp) 900.dp else 360.dp,
            showCameraPreview = uiState.hasCameraPermission,
            onSurfaceProviderReady = { surfaceProvider.value = it },
            isCapturing = uiState.isCapturing,
            selectedFilm = uiState.selectedFilm,
            showGrid = uiState.showGrid,
            zoomRatio = uiState.zoomRatio,
            minZoomRatio = uiState.minZoomRatio,
            maxZoomRatio = uiState.maxZoomRatio,
            hasFlashUnit = uiState.hasFlashUnit,
            isFlashEnabled = uiState.isFlashEnabled,
            latestPhotoUri = uiState.latestPhotoUri,
            onFilmSelected = onFilmSelected,
            onZoomInClick = onZoomInClick,
            onZoomOutClick = onZoomOutClick,
            onFlashClick = onFlashClick,
            onGalleryClick = onGalleryClick,
            modifier = Modifier.fillMaxSize(),
            onShutterClick = onShutterClick,
            onSettingsClick = onSettingsClick,
        )
    }
}

@Composable
private fun CameraControlLayout(
    layoutMode: CameraLayoutMode,
    displayRotation: Int,
    contentRotationDegrees: Float,
    previewMaxWidth: Dp,
    showCameraPreview: Boolean,
    onSurfaceProviderReady: (Preview.SurfaceProvider) -> Unit,
    isCapturing: Boolean,
    selectedFilm: FilmPreset,
    showGrid: Boolean,
    zoomRatio: Float,
    minZoomRatio: Float,
    maxZoomRatio: Float,
    hasFlashUnit: Boolean,
    isFlashEnabled: Boolean,
    latestPhotoUri: Uri?,
    onFilmSelected: (FilmPreset) -> Unit,
    onZoomInClick: () -> Unit,
    onZoomOutClick: () -> Unit,
    onFlashClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onShutterClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPortrait = layoutMode == CameraLayoutMode.Portrait
    val horizontalRailHeight = 96.dp
    val verticalRailWidth = 104.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)

    val startRailAlignment = when (layoutMode) {
        CameraLayoutMode.Portrait -> {
            if (displayRotation == Surface.ROTATION_180) Alignment.BottomCenter else Alignment.TopCenter
        }

        CameraLayoutMode.Landscape -> {
            if (displayRotation == Surface.ROTATION_270) Alignment.CenterEnd else Alignment.CenterStart
        }
    }

    val endRailAlignment = when (layoutMode) {
        CameraLayoutMode.Portrait -> {
            if (displayRotation == Surface.ROTATION_180) Alignment.TopCenter else Alignment.BottomCenter
        }

        CameraLayoutMode.Landscape -> {
            if (displayRotation == Surface.ROTATION_270) Alignment.CenterStart else Alignment.CenterEnd
        }
    }

    Box(modifier = modifier.background(Background)) {
        // 이 호출 위치를 고정해 방향 전환에도 PreviewSlot의 AndroidView identity를 유지한다.
        CameraStage(
            previewAspectRatio = if (isPortrait) 3f / 4f else 3f / 2f,
            previewMaxWidth = previewMaxWidth,
            displayRotation = displayRotation,
            contentRotationDegrees = contentRotationDegrees,
            showCameraPreview = showCameraPreview,
            selectedFilm = selectedFilm,
            showGrid = showGrid,
            zoomRatio = zoomRatio,
            onSurfaceProviderReady = onSurfaceProviderReady,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isPortrait) 0.dp else verticalRailWidth,
                    vertical = if (isPortrait) horizontalRailHeight else 0.dp,
                ),
        )

        StartControlRail(
            isVertical = !isPortrait,
            contentRotationDegrees = contentRotationDegrees,
            selectedFilm = selectedFilm,
            onFilmSelected = onFilmSelected,
            zoomRatio = zoomRatio,
            minZoomRatio = minZoomRatio,
            maxZoomRatio = maxZoomRatio,
            onZoomInClick = onZoomInClick,
            onZoomOutClick = onZoomOutClick,
            modifier = Modifier
                .align(startRailAlignment)
                .then(
                    if (isPortrait) {
                        Modifier
                            .fillMaxWidth()
                            .height(horizontalRailHeight)
                    } else {
                        Modifier
                            .fillMaxHeight()
                            .width(verticalRailWidth)
                    },
                )
                .background(Rail),
        )

        EndControlRail(
            isVertical = !isPortrait,
            contentRotationDegrees = contentRotationDegrees,
            isCapturing = isCapturing,
            hasFlashUnit = hasFlashUnit,
            isFlashEnabled = isFlashEnabled,
            latestPhotoUri = latestPhotoUri,
            onFlashClick = onFlashClick,
            onGalleryClick = onGalleryClick,
            onShutterClick = onShutterClick,
            onSettingsClick = onSettingsClick,
            modifier = Modifier
                .align(endRailAlignment)
                .then(
                    if (isPortrait) {
                        Modifier
                            .fillMaxWidth()
                            .height(horizontalRailHeight)
                    } else {
                        Modifier
                            .fillMaxHeight()
                            .width(verticalRailWidth)
                    },
                )
                .background(Rail),
        )
    }
}

@Composable
private fun CameraStage(
    previewAspectRatio: Float,
    previewMaxWidth: Dp,
    displayRotation: Int,
    contentRotationDegrees: Float,
    showCameraPreview: Boolean,
    selectedFilm: FilmPreset,
    showGrid: Boolean,
    zoomRatio: Float,
    onSurfaceProviderReady: (Preview.SurfaceProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        val previewWidth = minOf(maxWidth, maxHeight * previewAspectRatio, previewMaxWidth)
        PreviewSlot(
            modifier = Modifier
                .size(width = previewWidth, height = previewWidth / previewAspectRatio),
            displayRotation = displayRotation,
            showCameraPreview = showCameraPreview,
            contentRotationDegrees = contentRotationDegrees,
            selectedFilm = selectedFilm,
            showGrid = showGrid,
            zoomRatio = zoomRatio,
            onSurfaceProviderReady = onSurfaceProviderReady,
        )
    }
}

@Composable
private fun StartControlRail(
    isVertical: Boolean,
    contentRotationDegrees: Float,
    selectedFilm: FilmPreset,
    onFilmSelected: (FilmPreset) -> Unit,
    zoomRatio: Float,
    minZoomRatio: Float,
    maxZoomRatio: Float,
    onZoomInClick: () -> Unit,
    onZoomOutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isVertical) {
        Column(
            modifier = modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            RailLabel(
                text = stringResource(R.string.film),
                modifier = Modifier.graphicsLayer(rotationZ = contentRotationDegrees),
            )
            FilmDial(
                selectedFilm = selectedFilm,
                onFilmSelected = onFilmSelected,
                contentRotationDegrees = contentRotationDegrees,
                )
            ZoomControl(
                isVertical = true,
                contentRotationDegrees = contentRotationDegrees,
                zoomRatio = zoomRatio,
                minZoomRatio = minZoomRatio,
                maxZoomRatio = maxZoomRatio,
                onZoomInClick = onZoomInClick,
                onZoomOutClick = onZoomOutClick,
            )
        }
    } else {
        Row(
            modifier = modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.graphicsLayer(rotationZ = contentRotationDegrees),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RailLabel(text = stringResource(R.string.film))
                FilmDial(
                    selectedFilm = selectedFilm,
                    onFilmSelected = onFilmSelected,
                )
            }
            ZoomControl(
                isVertical = false,
                contentRotationDegrees = contentRotationDegrees,
                zoomRatio = zoomRatio,
                minZoomRatio = minZoomRatio,
                maxZoomRatio = maxZoomRatio,
                onZoomInClick = onZoomInClick,
                onZoomOutClick = onZoomOutClick,
            )
        }
    }
}

@Composable
private fun EndControlRail(
    isVertical: Boolean,
    contentRotationDegrees: Float,
    isCapturing: Boolean,
    hasFlashUnit: Boolean,
    isFlashEnabled: Boolean,
    latestPhotoUri: Uri?,
    onFlashClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onShutterClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isVertical) {
        Column(
            modifier = modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            EndRailActions(
                isCapturing = isCapturing,
                hasFlashUnit = hasFlashUnit,
                isFlashEnabled = isFlashEnabled,
                latestPhotoUri = latestPhotoUri,
                onFlashClick = onFlashClick,
                onGalleryClick = onGalleryClick,
                contentRotationDegrees = contentRotationDegrees,
                onShutterClick = onShutterClick,
                onSettingsClick = onSettingsClick,
            )
        }
    } else {
        Row(
            modifier = modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EndRailActions(
                isCapturing = isCapturing,
                hasFlashUnit = hasFlashUnit,
                isFlashEnabled = isFlashEnabled,
                latestPhotoUri = latestPhotoUri,
                onFlashClick = onFlashClick,
                onGalleryClick = onGalleryClick,
                contentRotationDegrees = contentRotationDegrees,
                onShutterClick = onShutterClick,
                onSettingsClick = onSettingsClick,
            )
        }
    }
}

@Composable
private fun EndRailActions(
    isCapturing: Boolean,
    hasFlashUnit: Boolean,
    isFlashEnabled: Boolean,
    latestPhotoUri: Uri?,
    onFlashClick: () -> Unit,
    onGalleryClick: () -> Unit,
    contentRotationDegrees: Float,
    onShutterClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    FlashControl(
        hasFlashUnit = hasFlashUnit,
        isFlashEnabled = isFlashEnabled,
        contentRotationDegrees = contentRotationDegrees,
        enabled = hasFlashUnit && !isCapturing,
        onClick = onFlashClick,
    )
    ShutterButton(
        isCapturing = isCapturing,
        onClick = onShutterClick,
    )
    RailAction(
        accessibilityLabel = if (latestPhotoUri == null) {
            stringResource(R.string.no_photos_yet)
        } else {
            stringResource(R.string.open_latest_photo)
        },
        contentRotationDegrees = contentRotationDegrees,
        enabled = latestPhotoUri != null,
        onClick = onGalleryClick,
    ) {
        GalleryThumbnail(latestPhotoUri)
    }
    RailAction(
        accessibilityLabel = stringResource(R.string.settings),
        contentRotationDegrees = contentRotationDegrees,
        onClick = onSettingsClick,
    ) {
        SettingsGlyph()
    }
}

@Composable
private fun FlashControl(
    hasFlashUnit: Boolean,
    isFlashEnabled: Boolean,
    contentRotationDegrees: Float,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.flash)
    val unavailableLabel = stringResource(R.string.flash_unavailable)
    val glyphColor = if (!hasFlashUnit) InkDim else if (isFlashEnabled) Accent else Ink
    DialCircle(
        modifier = Modifier
            .semantics {
                contentDescription = label
                if (!hasFlashUnit) stateDescription = unavailableLabel
            }
            .toggleable(
                value = isFlashEnabled,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { onClick() },
            ),
    ) {
        Canvas(
            modifier = Modifier.size(28.dp).graphicsLayer(rotationZ = contentRotationDegrees),
        ) {
            val bolt = Path().apply {
                moveTo(size.width * 0.60f, size.height * 0.10f)
                lineTo(size.width * 0.25f, size.height * 0.55f)
                lineTo(size.width * 0.45f, size.height * 0.55f)
                lineTo(size.width * 0.35f, size.height * 0.90f)
                lineTo(size.width * 0.80f, size.height * 0.40f)
                lineTo(size.width * 0.55f, size.height * 0.40f)
                close()
            }
            drawPath(path = bolt, color = glyphColor)
            if (!isFlashEnabled) {
                drawLine(
                    color = glyphColor,
                    start = Offset(size.width * 0.15f, size.height * 0.15f),
                    end = Offset(size.width * 0.85f, size.height * 0.85f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** CameraX PreviewView가 들어오기 전까지 화면 비율과 오버레이를 검증하는 자리. */
@Composable
private fun PreviewSlot(
    showCameraPreview: Boolean,
    displayRotation: Int,
    contentRotationDegrees: Float,
    onSurfaceProviderReady: (Preview.SurfaceProvider) -> Unit,
    selectedFilm: FilmPreset,
    showGrid: Boolean,
    zoomRatio: Float,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val glViewState = remember {
        mutableStateOf<CameraGlSurfaceView?>(null)
    }

    DisposableEffect(
        lifecycleOwner,
        glViewState.value,
    ) {
        val glView = glViewState.value ?: return@DisposableEffect onDispose {  }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                Lifecycle.Event.ON_PAUSE -> glView.onPause()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glView.onPause()
        }
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(8.dp),
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(RoundedCornerShape(8.dp))
            .background(PreviewBackground),
    ) {
        if (showCameraPreview) {
            AndroidView(
                modifier = Modifier.matchParentSize(),
                factory = { context ->
                    CameraGlSurfaceView(context).also { glView ->
                        glViewState.value = glView
                        onSurfaceProviderReady(glView.surfaceProvider)
                    }
                },
                onReset = null,
                onRelease = { glView -> glView.release() },
                update = { glView ->
                    glView.setDisplayRotation(displayRotation)
                    glView.setFilmPreset(selectedFilm)
                }
            )
        } else {
            PreviewPlaceholderScene(Modifier.fillMaxSize())
        }

        if (showGrid) {
            RuleOfThirdsGrid(Modifier.fillMaxSize())
        }

        FocusReticle(modifier = Modifier.align(Alignment.Center))

        RotationAwareContent(
            rotationDegrees = contentRotationDegrees,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 10.dp, top = 8.dp),
        ) {
            PreviewInfoBadge(text = stringResource(R.string.exposure_zero))
        }
        RotationAwareContent(
            rotationDegrees = contentRotationDegrees,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 10.dp, top = 8.dp),
        ) {
            PreviewInfoBadge(
                text = stringResource(R.string.zoom_ratio, zoomRatio),
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        RotationAwareContent(
            rotationDegrees = contentRotationDegrees,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
        ) {
            PreviewInfoBadge(text = stringResource(R.string.rear_camera))
        }
    }
}

@Composable
private fun PreviewInfoBadge(text: String, modifier: Modifier = Modifier) {
    MonoText(
        text = text,
        color = Ink,
        fontSize = 11.sp,
        modifier = modifier
            .background(Background, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun PreviewPlaceholderScene(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF201A12),
                    Color(0xFF100D0A),
                    Color(0xFF050505),
                ),
            ),
        ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 34.dp, top = 70.dp)
                .size(104.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Accent.copy(alpha = 0.38f), Color.Transparent),
                    ),
                    shape = CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 18.dp, top = 24.dp)
                .size(84.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFC88C46).copy(alpha = 0.3f),
                            Color.Transparent,
                        ),
                    ),
                    shape = CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xB8000000)),
                    ),
                ),
        )
    }
}

@Composable
private fun RuleOfThirdsGrid(
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Row(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f))
            GridLine(Modifier
                .fillMaxHeight()
                .width(1.dp))
            Spacer(Modifier.weight(1f))
            GridLine(Modifier
                .fillMaxHeight()
                .width(1.dp))
            Spacer(Modifier.weight(1f))
        }
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f))
            GridLine(Modifier
                .fillMaxWidth()
                .height(1.dp))
            Spacer(Modifier.weight(1f))
            GridLine(Modifier
                .fillMaxWidth()
                .height(1.dp))
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun GridLine(modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Ink.copy(alpha = 0.15f)))
}

@Composable
private fun FocusReticle(
    modifier: Modifier = Modifier,
) {
    val arm = 12.dp
    val stroke = 1.dp
    val reticleColor = Accent.copy(alpha = 0.8f)

    Box(modifier = modifier.size(64.dp)) {
        ReticleArm(arm, stroke, reticleColor, Modifier.align(Alignment.TopStart))
        ReticleArm(stroke, arm, reticleColor, Modifier.align(Alignment.TopStart))
        ReticleArm(arm, stroke, reticleColor, Modifier.align(Alignment.TopEnd))
        ReticleArm(stroke, arm, reticleColor, Modifier.align(Alignment.TopEnd))
        ReticleArm(arm, stroke, reticleColor, Modifier.align(Alignment.BottomStart))
        ReticleArm(stroke, arm, reticleColor, Modifier.align(Alignment.BottomStart))
        ReticleArm(arm, stroke, reticleColor, Modifier.align(Alignment.BottomEnd))
        ReticleArm(stroke, arm, reticleColor, Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun ReticleArm(
    width: Dp,
    height: Dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier
        .size(width, height)
        .background(color))
}

@Composable
private fun FilmDial(
    selectedFilm: FilmPreset,
    onFilmSelected: (FilmPreset) -> Unit,
    contentRotationDegrees: Float = 0f,
) {
    val filmName = stringResource(selectedFilm.labelRes)
    val accessibilityLabel = stringResource(R.string.film)
    val nextFilm = when (selectedFilm){
        FilmPreset.WARM -> FilmPreset.DUSK
        FilmPreset.DUSK -> FilmPreset.WARM
    }
    val changeFilmLabel = stringResource(R.string.switch_to_film, stringResource(nextFilm.labelRes))

    Column(
        modifier = Modifier
            .semantics {
                contentDescription = accessibilityLabel
                stateDescription = filmName
            }
            .clickable(
                role = Role.Button,
                onClickLabel = changeFilmLabel,
                onClick = { onFilmSelected(nextFilm) },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DialCircle {
            CondensedText(
                text = selectedFilm.name.first().toString(),
                color = Accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer(rotationZ = contentRotationDegrees),
            )
        }
        MonoText(
            text = filmName,
            color = InkDim,
            fontSize = 8.sp,
            modifier = Modifier
                .graphicsLayer(rotationZ = contentRotationDegrees)
                .clearAndSetSemantics { },
        )
    }
}

@Composable
private fun ZoomControl(
    isVertical: Boolean,
    contentRotationDegrees: Float,
    zoomRatio: Float,
    minZoomRatio: Float,
    maxZoomRatio: Float,
    onZoomInClick: () -> Unit,
    onZoomOutClick: () -> Unit,
) {
    val canZoomIn = zoomRatio < maxZoomRatio - 0.01f
    val canZoomOut = zoomRatio > minZoomRatio + 0.01f
    if (isVertical) {
        Column(
            modifier = Modifier.semantics { isTraversalGroup = true },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ZoomIncreaseButton(contentRotationDegrees, canZoomIn, onZoomInClick)
            ZoomValue(contentRotationDegrees, zoomRatio)
            ZoomDecreaseButton(contentRotationDegrees, canZoomOut, onZoomOutClick)
        }
    } else {
        Row(
            modifier = Modifier.semantics { isTraversalGroup = true },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZoomIncreaseButton(contentRotationDegrees, canZoomIn, onZoomInClick)
            ZoomValue(contentRotationDegrees, zoomRatio)
            ZoomDecreaseButton(contentRotationDegrees, canZoomOut, onZoomOutClick)
        }
    }
}

@Composable
private fun ZoomIncreaseButton(
    contentRotationDegrees: Float,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.zoom_in)
    DialCircle(
        modifier = Modifier
            .semantics { contentDescription = label }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = "+",
            color = if (enabled) Ink else InkDim,
            fontSize = 22.sp,
            modifier = Modifier.graphicsLayer(rotationZ = contentRotationDegrees),
        )
    }
}

@Composable
private fun ZoomValue(contentRotationDegrees: Float, zoomRatio: Float) {
    val zoomLabel = stringResource(R.string.zoom_level, zoomRatio)
    MonoText(
        text = stringResource(R.string.zoom_ratio, zoomRatio),
        color = Ink,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .graphicsLayer(rotationZ = contentRotationDegrees)
            .clearAndSetSemantics {
                contentDescription = zoomLabel
                liveRegion = LiveRegionMode.Polite
            },
    )
}

@Composable
private fun ZoomDecreaseButton(
    contentRotationDegrees: Float,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.zoom_out)
    DialCircle(
        modifier = Modifier
            .semantics { contentDescription = label }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = "−",
            color = if (enabled) Ink else InkDim,
            fontSize = 22.sp,
            modifier = Modifier.graphicsLayer(rotationZ = contentRotationDegrees),
        )
    }
}

@Composable
private fun RailAction(
    accessibilityLabel: String,
    contentRotationDegrees: Float,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    DialCircle(
        modifier = Modifier
            .semantics { contentDescription = accessibilityLabel }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Box(
            modifier = Modifier.graphicsLayer(rotationZ = contentRotationDegrees),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun DialCircle(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .background(Color(0x05FFFFFF), CircleShape)
            .border(1.dp, Hair, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun ShutterButton(
    isCapturing: Boolean,
    onClick: () -> Unit,
) {
    val isEnabled = !isCapturing
    val accessibilityLabel = stringResource(R.string.shutter)
    val savingLabel = stringResource(R.string.saving)

    Box(
        modifier = Modifier
            .size(68.dp)
            .semantics {
                contentDescription = accessibilityLabel
                if (isCapturing) stateDescription = savingLabel
            }
            .clickable(
                enabled = isEnabled,
                role = Role.Button,
                onClick = onClick,
            )
            .border(3.dp, Ink.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(
                    color = if (isEnabled) Color.White else Ink.copy(alpha = 0.35f),
                    shape = CircleShape,
                ),
        )
    }
}

@Composable
private fun GalleryThumbnail(uri: Uri?) {
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = null
        if (uri != null) {
            value = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.loadThumbnail(uri, Size(96, 96), null).asImageBitmap()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    null
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF7B5338), Color(0xFF1B1713)),
                ),
            )
            .border(1.dp, Hair, RoundedCornerShape(5.dp)),
    ) {
        thumbnail?.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SettingsGlyph() {
    Text(
        text = "⚙",
        color = Ink,
        fontSize = 21.sp,
    )
}

@Composable
private fun RailLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    MonoText(
        text = text,
        color = InkDim,
        fontSize = 8.sp,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

internal fun uiContentRotationDegrees(
    displayRotation: Int,
    deviceTargetRotation: Int,
): Float {
    // 두 입력 모두 Surface 회전 규약이다. Window가 이미 돌려준 만큼을 뺀다.
    val combinedRotation =
        surfaceRotationDegrees(deviceTargetRotation) - surfaceRotationDegrees(displayRotation)

    return ((combinedRotation + 540) % 360 - 180).toFloat()
}

@Composable
private fun RotationAwareContent(
    rotationDegrees: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = content,
    ) { measurables, constraints ->
        val normalizedRotation =
            ((rotationDegrees.toInt() % 360) + 360) % 360
        val swapsWidthAndHeight =
            normalizedRotation == 90 || normalizedRotation == 270

        val placeable = measurables.single().measure(
            Constraints(
                maxWidth = if (swapsWidthAndHeight) constraints.maxHeight else constraints.maxWidth,
                maxHeight = if (swapsWidthAndHeight) constraints.maxWidth else constraints.maxHeight,
            ),
        )

        val measuredWidth = if (swapsWidthAndHeight) {
            placeable.height
        } else {
            placeable.width
        }
        val measuredHeight = if (swapsWidthAndHeight) {
            placeable.width
        } else {
            placeable.height
        }

        val layoutWidth = measuredWidth.coerceIn(
            minimumValue = constraints.minWidth,
            maximumValue = constraints.maxWidth,
        )
        val layoutHeight = measuredHeight.coerceIn(
            minimumValue = constraints.minHeight,
            maximumValue = constraints.maxHeight,
        )

        layout(layoutWidth, layoutHeight) {
            placeable.placeRelativeWithLayer(
                x = (layoutWidth - placeable.width) / 2,
                y = (layoutHeight - placeable.height) / 2,
            ) {
                rotationZ = rotationDegrees
            }
        }
    }
}

private fun surfaceRotationDegrees(rotation: Int): Int =
    when (rotation) {
        Surface.ROTATION_0 -> 0
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }

@Composable
private fun MonoText(
    text: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
    letterSpacing: androidx.compose.ui.unit.TextUnit = 0.sp,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
) {
    Text(
        text = text,
        maxLines = maxLines,
        softWrap = softWrap,
        modifier = modifier,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize,
        fontWeight = fontWeight,
        letterSpacing = letterSpacing,
        textAlign = textAlign,
    )
}

@Composable
private fun CondensedText(
    text: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
    letterSpacing: androidx.compose.ui.unit.TextUnit = 0.sp,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontFamily = FontFamily.SansSerif,
        fontSize = fontSize,
        fontWeight = fontWeight,
        letterSpacing = letterSpacing,
    )
}
