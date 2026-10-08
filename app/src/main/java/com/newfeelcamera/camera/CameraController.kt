package com.newfeelcamera.camera

import android.content.Context
import android.util.Log
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.core.takePicture
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.newfeelcamera.camera.capture.CaptureException
import com.newfeelcamera.camera.capture.CaptureProcessor
import com.newfeelcamera.camera.capture.SavedPhoto
import com.newfeelcamera.camera.filter.FilmPreset
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.Closeable
import javax.inject.Inject

class CameraController @Inject constructor (
    @param:ApplicationContext private val appContext: Context,
    private val captureProcessor: CaptureProcessor,
): Closeable {

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var zoomStateLiveData: LiveData<ZoomState>? = null
    private var zoomStateObserver: Observer<ZoomState>? = null

    private var previewTargetRotation = Surface.ROTATION_0
    private var imageCaptureTargetRotation = Surface.ROTATION_0
    private var requestedZoomRatio = 1f
    private var pendingZoomRatio: Float? = null
    private var flashEnabled = false

    fun bind(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        onCameraStateChanged: (ZoomState, Boolean) -> Unit,
    ){
        val providerFuture = ProcessCameraProvider.getInstance(appContext)

        providerFuture.addListener({
            val cameraProvider = providerFuture.get()

            val previewUseCase = Preview.Builder()
                .setTargetRotation(previewTargetRotation)
                .build()
                .apply {
                    setSurfaceProvider(surfaceProvider)
                }

            val capture = ImageCapture.Builder()
                .setTargetRotation(imageCaptureTargetRotation)
                .setOutputFormat(ImageCapture.OUTPUT_FORMAT_JPEG)
                .setFlashMode(if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                .build()

            clearZoomObserver()
            camera = null
            pendingZoomRatio = null
            cameraProvider.unbindAll()

            val boundCamera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                previewUseCase,
                capture,
            )

            preview = previewUseCase
            imageCapture = capture
            camera = boundCamera

            val hasFlashUnit = boundCamera.cameraInfo.hasFlashUnit()
            if (!hasFlashUnit) {
                flashEnabled = false
                capture.flashMode = ImageCapture.FLASH_MODE_OFF
            }

            val zoomState = boundCamera.cameraInfo.zoomState
            var restoreZoom = true
            val observer = Observer<ZoomState> { state ->
                onCameraStateChanged(state, hasFlashUnit)
                if (restoreZoom) {
                    restoreZoom = false
                    requestedZoomRatio = requestedZoomRatio.coerceIn(
                        state.minZoomRatio,
                        state.maxZoomRatio,
                    )
                    if (requestedZoomRatio != state.zoomRatio) {
                        requestZoom(boundCamera, requestedZoomRatio)
                    }
                }
            }
            zoomStateLiveData = zoomState
            zoomStateObserver = observer
            zoomState.observe(lifecycleOwner, observer)
        },
            ContextCompat.getMainExecutor(appContext)
        )
    }

    private fun isSurfaceRotation(rotation: Int): Boolean =
        when (rotation) {
            Surface.ROTATION_0,
            Surface.ROTATION_90,
            Surface.ROTATION_180,
            Surface.ROTATION_270,
                -> true

            else -> false
        }

    fun updateImageCaptureTargetRotation(rotation: Int) {
        if (!isSurfaceRotation(rotation)) return
        if (imageCaptureTargetRotation == rotation) return

        imageCaptureTargetRotation = rotation
        imageCapture?.targetRotation = rotation
    }

    fun updatePreviewTargetRotation(rotation: Int) {
        if (!isSurfaceRotation(rotation)) return
        if (previewTargetRotation == rotation) return

        previewTargetRotation = rotation
        preview?.targetRotation = rotation
    }

    fun changeZoomBy(delta: Float) {
        val boundCamera = camera ?: return
        val zoomState = boundCamera.cameraInfo.zoomState.value ?: return
        val currentRatio = pendingZoomRatio ?: zoomState.zoomRatio
        val nextRatio = (currentRatio + delta).coerceIn(
            zoomState.minZoomRatio,
            zoomState.maxZoomRatio,
        )
        if (nextRatio == currentRatio) return

        requestZoom(boundCamera, nextRatio)
    }

    private fun requestZoom(boundCamera: Camera, nextRatio: Float) {
        requestedZoomRatio = nextRatio
        pendingZoomRatio = nextRatio
        val result = boundCamera.cameraControl.setZoomRatio(nextRatio)
        result.addListener({
            if (camera !== boundCamera || pendingZoomRatio != nextRatio) return@addListener
            pendingZoomRatio = null
            try {
                result.get()
            } catch (error: Exception) {
                requestedZoomRatio = boundCamera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                Log.w(TAG, "Unable to change zoom ratio to $nextRatio", error)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    fun setFlashEnabled(enabled: Boolean): Boolean {
        if (camera?.cameraInfo?.hasFlashUnit() != true) return false
        flashEnabled = enabled
        imageCapture?.flashMode =
            if (enabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        return true
    }

    suspend fun takePhoto(
        film: FilmPreset,
        onShutter: () -> Unit,
    ): SavedPhoto {
        val capture = imageCapture
            ?: throw CaptureException.CameraNotReady()

        Log.d(TAG, "takePhoto film=$film")

        onShutter()

        val imageProxy = try {
            capture.takePicture()
        } catch (error: ImageCaptureException) {
            throw CaptureException.CameraCaptureFailed(error)
        }

        return captureProcessor.processAndSave(
            imageProxy = imageProxy,
            film = film,
        )
    }

    override fun close() {
        clearZoomObserver()
        camera = null
        pendingZoomRatio = null
        captureProcessor.close()
    }

    private fun clearZoomObserver() {
        zoomStateObserver?.let { observer ->
            zoomStateLiveData?.removeObserver(observer)
        }
        zoomStateObserver = null
        zoomStateLiveData = null
    }

    companion object {
        private const val TAG = "CameraController"
    }
}
