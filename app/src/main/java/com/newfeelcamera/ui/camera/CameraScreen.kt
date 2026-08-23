package com.newfeelcamera.ui.camera

import android.view.Surface
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.newfeelcamera.camera.CameraController
import com.newfeelcamera.ui.camera.layout.CameraLayoutMode
import com.newfeelcamera.ui.camera.layout.cameraLayoutModeFor
import com.newfeelcamera.ui.design.Accent
import com.newfeelcamera.ui.design.Background
import com.newfeelcamera.ui.design.Hair
import com.newfeelcamera.ui.design.Ink
import com.newfeelcamera.ui.design.InkDim
import com.newfeelcamera.ui.design.PreviewBackground
import com.newfeelcamera.ui.design.Rail

@Composable
fun CameraScreen(
    cameraController: CameraController,
    hasCameraPermission: Boolean,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val displayRotation = LocalView.current.display?.rotation ?: Surface.ROTATION_0

    val previewView = remember {
        mutableStateOf<PreviewView?>(null)
    }

    val currentPreviewView = previewView.value

    val context = LocalContext.current

    LaunchedEffect(
        lifecycleOwner,
        currentPreviewView,
        hasCameraPermission,
    ) {
        if (hasCameraPermission && currentPreviewView != null) {

            cameraController.bind(
                lifecycleOwner = lifecycleOwner,
                surfaceProvider = currentPreviewView.surfaceProvider,
            )

        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
    ) {
        CameraControlLayout(
            layoutMode = cameraLayoutModeFor(
                width = maxWidth,
                height = maxHeight,
            ),
            displayRotation = displayRotation,
            showCameraPreview = hasCameraPermission,
            onPreviewReady = { previewView.value = it },
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
            onShutterClick = {
                cameraController.takePhoto(
                    onPhotoSaved = { uri ->
                        Toast.makeText(context, "저장 완료: &uri", Toast.LENGTH_SHORT).show()
                    },
                    onError = { error ->
                        Toast.makeText(context, "촬영 실패: ${error.message}", Toast.LENGTH_SHORT).show()
                    },
                )
            },
        )
    }
}

@Composable
private fun CameraControlLayout(
    layoutMode: CameraLayoutMode,
    displayRotation: Int,
    showCameraPreview: Boolean,
    onPreviewReady: (PreviewView) -> Unit,
    onShutterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (layoutMode) {
        CameraLayoutMode.Portrait -> PortraitCameraLayout(
            startOnTop = displayRotation != Surface.ROTATION_180,
            showCameraPreview = showCameraPreview,
            onPreviewReady = onPreviewReady,
            modifier = modifier,
            onShutterClick = onShutterClick
        )

        CameraLayoutMode.Landscape -> LandscapeCameraLayout(
            startOnLeft = displayRotation != Surface.ROTATION_270,
            showCameraPreview = showCameraPreview,
            onPreviewReady = onPreviewReady,
            onShutterClick = onShutterClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun PortraitCameraLayout(
    startOnTop: Boolean,
    showCameraPreview: Boolean,
    onPreviewReady: (PreviewView) -> Unit,
    onShutterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (startOnTop) {
            StartControlRail(
                isVertical = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(Rail),
            )
        } else {
            EndControlRail(
                isVertical = false,
                onShutterClick = onShutterClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(Rail),
            )
        }

        CameraStage(
            previewAspectRatio = 3f / 4f,
            showCameraPreview = showCameraPreview,
            onPreviewReady = onPreviewReady,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        if (startOnTop) {
            EndControlRail(
                isVertical = false,
                onShutterClick = onShutterClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(Rail),
            )
        } else {
            StartControlRail(
                isVertical = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(Rail),
            )
        }
    }
}

@Composable
private fun LandscapeCameraLayout(
    startOnLeft: Boolean,
    showCameraPreview: Boolean,
    onPreviewReady: (PreviewView) -> Unit,
    onShutterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (startOnLeft) {
            StartControlRail(
                isVertical = true,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(72.dp)
                    .background(Rail),
            )
        } else {
            EndControlRail(
                isVertical = true,
                onShutterClick = onShutterClick,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(72.dp)
                    .background(Rail),
            )
        }

        CameraStage(
            previewAspectRatio = 3f / 2f,
            showCameraPreview = showCameraPreview,
            onPreviewReady = onPreviewReady,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )

        if (startOnLeft) {
            EndControlRail(
                isVertical = true,
                onShutterClick = onShutterClick,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(72.dp)
                    .background(Rail),
            )
        } else {
            StartControlRail(
                isVertical = true,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(72.dp)
                    .background(Rail),
            )
        }
    }
}

@Composable
private fun CameraStage(
    previewAspectRatio: Float,
    showCameraPreview: Boolean,
    onPreviewReady: (PreviewView) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ShotCounter()
        Spacer(modifier = Modifier.height(12.dp))
        PreviewSlot(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .fillMaxWidth()
                .aspectRatio(previewAspectRatio),
            showCameraPreview = showCameraPreview,
            onPreviewReady = onPreviewReady,
        )
    }
}

@Composable
private fun StartControlRail(
    isVertical: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isVertical) {
        Column(
            modifier = modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            RailLabel(text = "FILM")
            FilmDial()
            ZoomControl(isVertical = true)
        }
    } else {
        Row(
            modifier = modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RailLabel(text = "FILM")
                FilmDial()
            }
            ZoomControl(isVertical = false)
        }
    }
}

@Composable
private fun EndControlRail(
    isVertical: Boolean,
    onShutterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isVertical) {
        Column(
            modifier = modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            EndRailActions(onShutterClick = onShutterClick)
        }
    } else {
        Row(
            modifier = modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EndRailActions(onShutterClick = onShutterClick)
        }
    }
}

@Composable
private fun EndRailActions(
    onShutterClick: () -> Unit,
) {
    RailAction(label = "FLASH") {
        Text(
            text = "ϟ",
            color = Ink,
            fontSize = 24.sp,
        )
    }
    ShutterButton(onClick = onShutterClick)
    RailAction(label = "GALLERY") {
        GalleryThumbnail()
    }
    RailAction(label = "SETTINGS") {
        SettingsGlyph()
    }
}

@Composable
private fun ShotCounter() {
    Row(
        modifier = Modifier
            .background(
                color = Color(0x73000000),
                shape = RoundedCornerShape(20.dp),
            )
            .border(
                width = 1.dp,
                color = Hair,
                shape = RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(Accent, CircleShape),
        )
        MonoText(
            text = "01",
            color = Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        MonoText(text = "/", color = InkDim, fontSize = 13.sp)
        MonoText(text = "24", color = InkDim, fontSize = 13.sp)
        Box(
            modifier = Modifier
                .height(14.dp)
                .width(1.dp)
                .background(Hair),
        )
        CondensedText(
            text = "WARM",
            color = InkDim,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
        )
    }
}

/** CameraX PreviewView가 들어오기 전까지 화면 비율과 오버레이를 검증하는 자리. */
@Composable
private fun PreviewSlot(
    showCameraPreview: Boolean,
    onPreviewReady: (PreviewView) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    PreviewView(context).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }.also(onPreviewReady)
                }
            )
        } else {
            PreviewPlaceholderScene(Modifier.fillMaxSize())
        }

        RuleOfThirdsGrid(Modifier.fillMaxSize())

        FocusReticle(modifier = Modifier.align(Alignment.Center))

        MonoText(
            text = "EV +0.0",
            color = Ink.copy(alpha = 0.55f),
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 10.dp, top = 8.dp),
        )
        MonoText(
            text = "1.0X",
            color = Ink.copy(alpha = 0.55f),
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 10.dp, top = 8.dp),
        )
        MonoText(
            text = "REAR CAMERA",
            color = Ink.copy(alpha = 0.28f),
            fontSize = 9.sp,
            letterSpacing = 2.4.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
        )
    }
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
private fun FilmDial() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DialCircle {
            CondensedText(
                text = "W",
                color = Accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        MonoText(text = "WARM", color = InkDim, fontSize = 8.sp)
    }
}

@Composable
private fun ZoomControl(isVertical: Boolean) {
    if (isVertical) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ZoomIncreaseButton()
            ZoomValue()
            ZoomDecreaseButton()
            RailLabel(text = "ZOOM")
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZoomIncreaseButton()
                ZoomValue()
                ZoomDecreaseButton()
            }
            RailLabel(text = "ZOOM")
        }
    }
}

@Composable
private fun ZoomIncreaseButton() {
    DialCircle {
        Text(text = "+", color = Ink, fontSize = 22.sp)
    }
}

@Composable
private fun ZoomValue() {
    MonoText(
        text = "1.0X",
        color = Ink,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun ZoomDecreaseButton() {
    DialCircle {
        Text(text = "−", color = Ink, fontSize = 22.sp)
    }
}

@Composable
private fun RailAction(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DialCircle(content = content)
        RailLabel(text = label)
    }
}

@Composable
private fun DialCircle(
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(Color(0x05FFFFFF), CircleShape)
            .border(1.dp, Hair, CircleShape),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun ShutterButton(
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clickable(onClick = onClick)
                .border(3.dp, Ink.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Color.White, CircleShape),
            )
        }
        RailLabel(text = "SHUTTER")
    }
}

@Composable
private fun GalleryThumbnail() {
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
    )
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
private fun RailLabel(text: String) {
    MonoText(
        text = text,
        color = InkDim,
        fontSize = 8.sp,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Center,
    )
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
) {
    Text(
        text = text,
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
