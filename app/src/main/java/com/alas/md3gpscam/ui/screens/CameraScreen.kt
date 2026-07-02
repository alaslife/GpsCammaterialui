package com.alas.md3gpscam.ui.screens

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.SoundEffectConstants
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.QualitySelector
import androidx.camera.video.Quality
import androidx.camera.video.FallbackStrategy
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.alas.md3gpscam.data.database.PhotoEntity
import com.alas.md3gpscam.ui.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CameraMode {
    PHOTO, VIDEO
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onPhotoCaptured: (PhotoEntity) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val mediaActionSound = remember { MediaActionSound() }

    DisposableEffect(Unit) {
        onDispose {
            mediaActionSound.release()
        }
    }

    // Observe State
    val gridLinesEnabled by viewModel.gridLinesEnabled.collectAsState()
    val timerSeconds by viewModel.timerSeconds.collectAsState()
    val imageRatio by viewModel.imageRatio.collectAsState()
    val videoQuality by viewModel.videoQuality.collectAsState()
    val selectedTemplate by viewModel.selectedTemplate.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val deviceBearing by viewModel.deviceBearing.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val recentPhotos by viewModel.recentPhotos.collectAsState()
    val hapticFeedbackEnabled by viewModel.hapticFeedbackEnabled.collectAsState()
    val speedUnit by viewModel.speedUnit.collectAsState()
    val altitudeUnit by viewModel.altitudeUnit.collectAsState()
    val recordAudioInVideo by viewModel.recordAudioInVideo.collectAsState()

    // Camera Mode & Video states
    var cameraMode by remember { mutableStateOf(CameraMode.PHOTO) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDurationSeconds by remember { mutableStateOf(0) }
    var activeRecording: Recording? by remember { mutableStateOf(null) }

    // Camera settings
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }
    
    // Use Cases references
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var videoCapture: VideoCapture<Recorder>? by remember { mutableStateOf(null) }
    
    // Zoom control state
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    var cameraInfo by remember { mutableStateOf<androidx.camera.core.CameraInfo?>(null) }
    var zoomRatio by remember { mutableStateOf(1f) }

    var isCapturing by remember { mutableStateOf(false) }
    var countdownValue by remember { mutableStateOf(-1) }

    // Shutter button animation state
    var isShutterPressed by remember { mutableStateOf(false) }
    val shutterScale by animateFloatAsState(
        targetValue = if (isShutterPressed) 0.85f else 1.0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "shutter_scale"
    )

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Layout configuration depending on ratio
    val previewAspectRatio = when {
        cameraMode == CameraMode.VIDEO -> if (isLandscape) 16f / 9f else 9f / 16f
        imageRatio == "1:1" -> 1.0f
        imageRatio == "16:9" -> if (isLandscape) 16f / 9f else 9f / 16f
        else -> if (isLandscape) 4f / 3f else 3f / 4f // "4:3" default
    }

    val watermarkBottomPadding = when (imageRatio) {
        "1:1" -> 16.dp
        "16:9" -> 150.dp
        else -> 40.dp // "4:3" default
    }


    // Set up ProcessCameraProvider
    val previewView = remember { PreviewView(context) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // Bind Preview & Use Cases dynamically based on Photo/Video mode selection
    LaunchedEffect(cameraSelector, imageRatio, cameraMode) {
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()

            val ratioVal = when (imageRatio) {
                "16:9" -> androidx.camera.core.AspectRatio.RATIO_16_9
                "1:1" -> androidx.camera.core.AspectRatio.RATIO_4_3 // Crop to 1:1 at layout and bitmap levels
                else -> androidx.camera.core.AspectRatio.RATIO_4_3
            }

            val preview = Preview.Builder()
                .setTargetAspectRatio(ratioVal)
                .build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val useCases = mutableListOf<androidx.camera.core.UseCase>(preview)

            if (cameraMode == CameraMode.PHOTO) {
                val capture = ImageCapture.Builder()
                    .setTargetAspectRatio(ratioVal)
                    .setFlashMode(flashMode)
                    .build()
                imageCapture = capture
                useCases.add(capture)
            } else {
                val selectedQuality = when (videoQuality) {
                    "1080p" -> Quality.FHD
                    "720p" -> Quality.HD
                    "480p" -> Quality.SD
                    else -> Quality.HD
                }
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(selectedQuality, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                    .build()
                val vCapture = VideoCapture.withOutput(recorder)
                videoCapture = vCapture
                useCases.add(vCapture)
            }

            try {
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    *useCases.toTypedArray()
                )
                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo
                // Reset zoom when binding
                zoomRatio = 1f
                cameraControl?.setZoomRatio(1f)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Dynamic Flash update
    LaunchedEffect(flashMode) {
        imageCapture?.flashMode = flashMode
    }

    // Timer logic for Video duration
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                delay(1000)
                recordingDurationSeconds++
            }
        }
    }

    // Zoom helper
    val setZoomRatio: (Float) -> Unit = { ratio ->
        val safeRatio = ratio.coerceIn(1f, 8f)
        zoomRatio = safeRatio
        cameraControl?.setZoomRatio(safeRatio)
    }

    // Format Video length helper
    val formatDuration: (Int) -> String = { seconds ->
        val mins = seconds / 60
        val secs = seconds % 60
        "%02d:%02d".format(mins, secs)
    }

    // Capture Function (Photo)
    val triggerPhotoCapture: () -> Unit = {
        val captureAction = {
            isCapturing = true
            
            // Haptic trigger
            if (hapticFeedbackEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }

            // Save capturing file
            val tempFile = File(context.cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
            val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

            imageCapture?.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        // Play camera shutter sound
                        if (soundEnabled) {
                            mediaActionSound.play(MediaActionSound.SHUTTER_CLICK)
                        }
                        
                        viewModel.saveCapturedPhoto(Uri.fromFile(tempFile), deviceBearing) { photoEntity ->
                            isCapturing = false
                            onPhotoCaptured(photoEntity)
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        exception.printStackTrace()
                        isCapturing = false
                    }
                }
            )
        }

        if (timerSeconds > 0) {
            coroutineScope.launch {
                countdownValue = timerSeconds
                while (countdownValue > 0) {
                    if (hapticFeedbackEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    delay(1000)
                    countdownValue--
                }
                countdownValue = -1
                captureAction()
            }
        } else {
            captureAction()
        }
    }

    // Video Recording Function
    val triggerVideoRecording: () -> Unit = {
        if (isRecording) {
            // Stop recording
            if (soundEnabled) {
                mediaActionSound.play(MediaActionSound.STOP_VIDEO_RECORDING)
            }
            activeRecording?.stop()
            activeRecording = null
            isRecording = false
            if (hapticFeedbackEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        } else {
            // Start recording
            isRecording = true
            recordingDurationSeconds = 0
            if (soundEnabled) {
                mediaActionSound.play(MediaActionSound.START_VIDEO_RECORDING)
            }

            if (hapticFeedbackEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }

            val outputOptions = MediaStoreOutputOptions
                .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "GPSCAM_${System.currentTimeMillis()}")
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/GpsCamera")
                    }
                })
                .build()

            val recordingListener = androidx.core.util.Consumer<VideoRecordEvent> { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        // Started successfully
                    }
                    is VideoRecordEvent.Finalize -> {
                        isRecording = false
                        activeRecording = null
                        if (!event.hasError()) {
                            val savedUri = event.outputResults.outputUri
                            
                            viewModel.saveCapturedVideo(savedUri, deviceBearing) { photoEntity ->
                                onPhotoCaptured(photoEntity)
                            }
                        } else {
                            event.cause?.printStackTrace()
                        }
                    }
                }
            }

            try {
                val recording = videoCapture?.output
                    ?.prepareRecording(context, outputOptions)
                    ?.apply {
                        val recordPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
                        if (recordAudioInVideo && recordPermission == PackageManager.PERMISSION_GRANTED) {
                            withAudioEnabled()
                        }
                    }
                    ?.start(ContextCompat.getMainExecutor(context), recordingListener)
                
                activeRecording = recording
            } catch (e: Exception) {
                e.printStackTrace()
                isRecording = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val viewfinderModifier = (if (isLandscape) {
            Modifier
                .fillMaxHeight()
                .aspectRatio(previewAspectRatio)
                .align(Alignment.Center)
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(previewAspectRatio)
                .align(Alignment.Center)
        })
            .clip(MaterialTheme.shapes.large)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    setZoomRatio(zoomRatio * zoom)
                }
            }

        // Viewfinder with pinch-to-zoom support
        Box(
            modifier = viewfinderModifier
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // Grid Lines Overlay
            if (gridLinesEnabled) {
                GridLinesOverlay(modifier = Modifier.fillMaxSize())
            }

            // Real-time GPS Stamp Preview overlay
            val customNoteVal by viewModel.customNote.collectAsState()
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape) 0.45f else 1f)
                    .padding(horizontal = 16.dp)
                    .padding(
                        bottom = if (isLandscape) {
                            16.dp
                        } else if (cameraMode == CameraMode.VIDEO) {
                            150.dp
                        } else {
                            watermarkBottomPadding
                        }
                    )
                    .align(if (isLandscape) Alignment.BottomStart else Alignment.BottomCenter)
            ) {
                LiveStampPreview(
                    location = currentLocation,
                    templateId = selectedTemplate,
                    bearing = deviceBearing,
                    speedUnit = speedUnit,
                    altitudeUnit = altitudeUnit,
                    customNote = customNoteVal
                )
            }
        }

        // Soft scrim keeps controls readable without covering the viewfinder.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                    )
                )
        )

        // Top Control Bar Overlay (Flash, Aspect Ratio, Grid, Timer)
        Surface(
            color = Color.Black.copy(alpha = 0.58f),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopCenter)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(vertical = 4.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                // Flash Option (Only relevant/visible in photo mode)
                IconButton(
                    onClick = {
                        flashMode = when (flashMode) {
                            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                            else -> ImageCapture.FLASH_MODE_OFF
                        }
                    },
                    enabled = cameraMode == CameraMode.PHOTO
                ) {
                    Icon(
                        imageVector = when (flashMode) {
                            ImageCapture.FLASH_MODE_ON -> Icons.Rounded.FlashOn
                            ImageCapture.FLASH_MODE_AUTO -> Icons.Rounded.FlashAuto
                            else -> Icons.Rounded.FlashOff
                        },
                        contentDescription = "Flash Mode",
                        tint = if (cameraMode == CameraMode.PHOTO) Color.White else Color.Gray
                    )
                }

                // Grid lines Toggle
                IconButton(onClick = { viewModel.setGridLines(!gridLinesEnabled) }) {
                    Icon(
                        imageVector = if (gridLinesEnabled) Icons.Rounded.GridOn else Icons.Rounded.GridOff,
                        contentDescription = "Grid Lines",
                        tint = if (gridLinesEnabled) MaterialTheme.colorScheme.primary else Color.White
                    )
                }

                // Timer Option (Only relevant in photo mode)
                IconButton(
                    onClick = {
                        val nextTimer = when (timerSeconds) {
                            0 -> 3
                            3 -> 10
                            else -> 0
                        }
                        viewModel.setTimer(nextTimer)
                    },
                    enabled = cameraMode == CameraMode.PHOTO
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = "Timer",
                            tint = if (cameraMode == CameraMode.PHOTO) Color.White else Color.Gray
                        )
                        if (timerSeconds > 0 && cameraMode == CameraMode.PHOTO) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 10.dp, start = 10.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .size(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$timerSeconds",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }

                // Aspect Ratio Selector
                IconButton(onClick = {
                    val nextRatio = when (imageRatio) {
                        "4:3" -> "16:9"
                        "16:9" -> "1:1"
                        else -> "4:3"
                    }
                    viewModel.setRatio(nextRatio)
                }) {
                    Icon(
                        imageVector = when (imageRatio) {
                            "16:9" -> Icons.Rounded.AspectRatio
                            "1:1" -> Icons.Rounded.CropSquare
                            else -> Icons.Rounded.Crop
                        },
                        contentDescription = "Aspect Ratio",
                        tint = Color.White
                    )
                }
            }
        }

        // Blinking Video Recording HUD overlay
        if (isRecording) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 70.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BlinkingRecordDot()
                Text(
                    text = formatDuration(recordingDurationSeconds),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        // Compass
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 80.dp, end = 16.dp)
        ) {
            Compass(bearing = deviceBearing)
        }



        // Camera Mode Switcher (PHOTO / VIDEO slider picker)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 110.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.62f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (cameraMode == CameraMode.PHOTO) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable {
                                if (cameraMode != CameraMode.PHOTO && !isRecording) {
                                    cameraMode = CameraMode.PHOTO
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "PHOTO",
                            color = if (cameraMode == CameraMode.PHOTO) MaterialTheme.colorScheme.onPrimaryContainer else Color.LightGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (cameraMode == CameraMode.VIDEO) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable {
                                if (cameraMode != CameraMode.VIDEO) {
                                    cameraMode = CameraMode.VIDEO
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "VIDEO",
                            color = if (cameraMode == CameraMode.VIDEO) MaterialTheme.colorScheme.onPrimaryContainer else Color.LightGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // Shutter Button Panel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery Preview Button
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { onNavigateBack() }, // navigate to gallery/home
                    contentAlignment = Alignment.Center
                ) {
                    if (recentPhotos.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = recentPhotos.first().filePath,
                                contentDescription = "Gallery Thumbnail",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            if (recentPhotos.first().isVideo) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    } else {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = "Gallery", tint = Color.White)
                    }
                }

                // Capture Shutter Button (Pixel UI matching dynamic styling)
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .scale(shutterScale)
                        .clip(CircleShape)
                        .background(Color.White)
                        .pointerInput(cameraMode, isRecording) {
                            detectTapGestures(
                                onPress = {
                                    isShutterPressed = true
                                    try {
                                        awaitRelease()
                                    } finally {
                                        isShutterPressed = false
                                    }
                                },
                                onTap = {
                                    if (cameraMode == CameraMode.PHOTO) {
                                        if (!isCapturing && countdownValue == -1) {
                                            triggerPhotoCapture()
                                        }
                                    } else {
                                        triggerVideoRecording()
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (cameraMode == CameraMode.PHOTO) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .border(4.dp, Color.Black, CircleShape)
                                .background(Color.Transparent)
                        )
                    } else {
                        // Dynamic morph from circle red to square red stop button
                        val innerSize = if (isRecording) 32.dp else 48.dp
                        val shape = if (isRecording) RoundedCornerShape(8.dp) else CircleShape
                        Box(
                            modifier = Modifier
                                .size(innerSize)
                                .clip(shape)
                                .background(Color.Red)
                        )
                    }
                }

                // Camera Switch Flip Button
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable {
                            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                                CameraSelector.DEFAULT_FRONT_CAMERA
                            } else {
                                CameraSelector.DEFAULT_BACK_CAMERA
                            }
                            if (hapticFeedbackEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FlipCameraAndroid,
                        contentDescription = "Flip Camera",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Countdown Timer Overlay
        AnimatedVisibility(
            visible = countdownValue > 0,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$countdownValue",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }

        // Saving State Loading Overlay
        if (isCapturing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        val customNoteVal by viewModel.customNote.collectAsState()
        var showNoteDialog by remember { mutableStateOf(false) }

        // Note tag editor pill button
        if (!isCapturing && !isRecording && countdownValue == -1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 175.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .clickable { showNoteDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit Tag",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (customNoteVal.isBlank()) "Add Custom Note / Tag" else "Tag: $customNoteVal",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 180.dp)
                        )
                    }
                }
            }
        }

        if (showNoteDialog) {
            var tempNoteText by remember { mutableStateOf(customNoteVal) }
            AlertDialog(
                onDismissRequest = { showNoteDialog = false },
                title = { Text("Watermark Custom Tag", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Write a custom project tag, survey label, or note to overlay onto your captures.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = tempNoteText,
                            onValueChange = { tempNoteText = it },
                            placeholder = { Text("e.g. Site A - Survey") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                if (tempNoteText.isNotEmpty()) {
                                    IconButton(onClick = { tempNoteText = "" }) {
                                        Icon(Icons.Rounded.Clear, "Clear")
                                    }
                                }
                            }
                        )
                        Text("Suggestions:", style = MaterialTheme.typography.labelMedium)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("Survey", "Inspection", "Travel", "Work").forEach { suggestion ->
                                FilterChip(
                                    selected = tempNoteText == suggestion,
                                    onClick = { tempNoteText = suggestion },
                                    label = { Text(suggestion) }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.setCustomNote(tempNoteText)
                            showNoteDialog = false
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNoteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun BlinkingRecordDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "blink_dot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )

    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(Color.Red.copy(alpha = alpha))
    )
}

@Composable
fun LiveStampPreview(
    location: com.alas.md3gpscam.location.GpsLocation,
    templateId: String,
    bearing: Float,
    speedUnit: String,
    altitudeUnit: String,
    customNote: String? = null
) {
    val dateText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(location.time))
    val captureDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(location.time))
    val captureTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(location.time))
    val premiumDateText = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date(location.time))
    val premiumTimeText = SimpleDateFormat("hh:mm:ss a 'GMT'XXX", Locale.getDefault()).format(Date(location.time))
    
    val speedValue = if (speedUnit == "mph") location.speed * 2.23694f else location.speed * 3.6f
    val speedLabel = if (speedUnit == "mph") "mph" else "km/h"
    val speedText = "Speed: %.1f %s".format(speedValue, speedLabel)

    val altValue = if (altitudeUnit == "feet") location.altitude * 3.28084 else location.altitude
    val altLabel = if (altitudeUnit == "feet") "ft" else "m"
    val altText = "Alt: %.1f %s".format(altValue, altLabel)

    val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW", "N")
    val dirAbbrev = directions[(((bearing + 22.5) / 45).toInt()) % 8]
    val bearingText = "Heading: %s (%.0f°)".format(dirAbbrev, bearing)
    val note = if (customNote.isNullOrBlank()) null else customNote.trim()

    if (templateId == "basic") {
        MaterialGpsWatermark(
            location = location,
            date = captureDate,
            time = captureTime,
            altitude = altText,
            speed = speedText,
            heading = "$dirAbbrev  %.0f°".format(bearing),
            customNote = note
        )
        return
    }

    // Stamp Card drawing
    if (templateId == "retro_film") {
        val latDir = if (location.latitude >= 0) "N" else "S"
        val lonDir = if (location.longitude >= 0) "E" else "W"
        // Overlay for retro analog stamp direct print
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            if (note != null) {
                Text(
                    text = "TAG: ${note.uppercase(Locale.ROOT)}",
                    color = Color(255, 120, 0),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
            Text(
                text = "📍 %.4f %s  %.4f %s  BEAR: %.0f°".format(
                    kotlin.math.abs(location.latitude), latDir,
                    kotlin.math.abs(location.longitude), lonDir,
                    bearing
                ),
                color = Color(255, 120, 0),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Text(
                text = "%s  %s  %s".format(altText.uppercase(), speedText.uppercase(), dateText),
                color = Color(255, 120, 0),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    } else {
        val isPremium = templateId == "gps_map_camera" || templateId == "gps_qr_camera"
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = if (isPremium) Color(0xD90A0C12) else Color(0xAA0F111A)
                ),
                border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(if (isPremium) 14.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (templateId == "gps_map_camera" || templateId == "gps_qr_camera") {
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                        ) {
                            if (templateId == "gps_qr_camera") {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val w = size.width
                                    val h = size.height
                                    
                                    // White background
                                    drawRect(color = Color.White)
                                    
                                    // QR finder patterns (top-left, top-right, bottom-left)
                                    val finderSize = 24.dp.toPx()
                                    val stroke = 3.dp.toPx()
                                    
                                    val finders = listOf(
                                        Offset(4.dp.toPx(), 4.dp.toPx()),
                                        Offset(w - finderSize - 4.dp.toPx(), 4.dp.toPx()),
                                        Offset(4.dp.toPx(), h - finderSize - 4.dp.toPx())
                                    )
                                    
                                    for (pos in finders) {
                                        // Outer square
                                        drawRect(
                                            color = Color.Black,
                                            topLeft = pos,
                                            size = androidx.compose.ui.geometry.Size(finderSize, finderSize),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                                        )
                                        // Inner square
                                        val innerOffset = 6.dp.toPx()
                                        drawRect(
                                            color = Color.Black,
                                            topLeft = Offset(pos.x + innerOffset, pos.y + innerOffset),
                                            size = androidx.compose.ui.geometry.Size(finderSize - innerOffset * 2, finderSize - innerOffset * 2)
                                        )
                                    }
                                    
                                    // Simulated random QR pixels/dots
                                    val randomColor = Color.Black
                                    drawRect(color = randomColor, topLeft = Offset(w * 0.45f, h * 0.15f), size = androidx.compose.ui.geometry.Size(8.dp.toPx(), 12.dp.toPx()))
                                    drawRect(color = randomColor, topLeft = Offset(w * 0.6f, h * 0.3f), size = androidx.compose.ui.geometry.Size(12.dp.toPx(), 6.dp.toPx()))
                                    drawRect(color = randomColor, topLeft = Offset(w * 0.4f, h * 0.5f), size = androidx.compose.ui.geometry.Size(16.dp.toPx(), 8.dp.toPx()))
                                    drawRect(color = randomColor, topLeft = Offset(w * 0.7f, h * 0.65f), size = androidx.compose.ui.geometry.Size(8.dp.toPx(), 16.dp.toPx()))
                                    drawRect(color = randomColor, topLeft = Offset(w * 0.55f, h * 0.8f), size = androidx.compose.ui.geometry.Size(10.dp.toPx(), 10.dp.toPx()))
                                }
                            } else {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val w = size.width
                                    val h = size.height
                                    
                                    // Map land background
                                    drawRoundRect(
                                        color = Color(0xFFE8F5E9),
                                        size = size,
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx())
                                    )
                                    
                                    // Water body in corner
                                    val waterPath = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(0f, h)
                                        lineTo(w * 0.4f, h)
                                        quadraticTo(w * 0.2f, h * 0.8f, 0f, h * 0.6f)
                                        close()
                                    }
                                    drawPath(waterPath, color = Color(0xFFBBDEFB))
                                    
                                    // Roads
                                    drawLine(color = Color.White, start = Offset(0f, h * 0.3f), end = Offset(w, h * 0.7f), strokeWidth = 2.5.dp.toPx())
                                    drawLine(color = Color.White, start = Offset(w * 0.45f, 0f), end = Offset(w * 0.55f, h), strokeWidth = 2.5.dp.toPx())
                                    drawLine(color = Color(0xFFE0E0E0), start = Offset(0f, h * 0.6f), end = Offset(w, h * 0.2f), strokeWidth = 1.2.dp.toPx())
                                    
                                    // Pulsing blue dot in center
                                    val cx = w / 2f
                                    val cy = h / 2f
                                    drawCircle(color = Color(0x332196F3), radius = 8.dp.toPx(), center = Offset(cx, cy))
                                    drawCircle(color = Color.White, radius = 3.5.dp.toPx(), center = Offset(cx, cy))
                                    drawCircle(color = Color(0xFF2196F3), radius = 2.dp.toPx(), center = Offset(cx, cy))
                                    
                                    // Flag badge in top-left
                                    val country = getCountryFromAddress(location.address)
                                    drawFlagBadge(this, country, 3.dp.toPx(), 3.dp.toPx(), 14.dp.toPx(), 9.dp.toPx())
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        when (templateId) {
                            "gps_map_camera", "gps_qr_camera" -> {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        Icons.Rounded.LocationOn,
                                        null,
                                        tint = Color(0xFFFFCA28),
                                        modifier = Modifier.size(14.dp).padding(top = 1.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = location.address.ifBlank { "Unknown location" },
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 16.sp
                                    )
                                }

                                if (note != null) {
                                    Text(
                                        text = note,
                                        color = Color(0xFFFFCA28),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Text(
                                    text = "Lat %.5f°  •  Long %.5f°".format(location.latitude, location.longitude),
                                    color = Color.White.copy(alpha = 0.88f),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(altText.removePrefix("Alt: "), fontSize = 9.sp, maxLines = 1) },
                                        leadingIcon = { Icon(Icons.Rounded.Height, null, modifier = Modifier.size(13.dp)) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = Color.White.copy(alpha = 0.10f),
                                            labelColor = Color.White,
                                            leadingIconContentColor = Color(0xFFFFCA28)
                                        ),
                                        border = null,
                                        modifier = Modifier.height(28.dp)
                                    )
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(speedText.removePrefix("Speed: "), fontSize = 9.sp, maxLines = 1) },
                                        leadingIcon = { Icon(Icons.Rounded.Speed, null, modifier = Modifier.size(13.dp)) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = Color.White.copy(alpha = 0.10f),
                                            labelColor = Color.White,
                                            leadingIconContentColor = Color(0xFFFFCA28)
                                        ),
                                        border = null,
                                        modifier = Modifier.height(28.dp)
                                    )
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(bearingText.removePrefix("Heading: "), fontSize = 9.sp, maxLines = 1) },
                                        leadingIcon = { Icon(Icons.Rounded.Explore, null, modifier = Modifier.size(13.dp)) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = Color.White.copy(alpha = 0.10f),
                                            labelColor = Color.White,
                                            leadingIconContentColor = Color(0xFFFFCA28)
                                        ),
                                        border = null,
                                        modifier = Modifier.height(28.dp)
                                    )
                                }

                                Text(
                                    text = "$premiumDateText  •  $premiumTimeText",
                                    color = Color(0xFFBAC3FF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            "minimal" -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (note != null) "📌 $note  •  📍 %.4f°, %.4f°".format(location.latitude, location.longitude)
                                               else "📍 %.4f°, %.4f°".format(location.latitude, location.longitude),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text("$dirAbbrev  •  $dateText", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                }
                            }
                            "technical" -> {
                                Text("📡 GPS TELEMETRY LOG", color = Color(0xFFBAC3FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("LAT:  %.7f°".format(location.latitude), color = Color.White, fontSize = 11.sp)
                                Text("LONG: %.7f°".format(location.longitude), color = Color.White, fontSize = 11.sp)
                                Text("ALT:  %s  |  SPD: %s".format(altText, speedText), color = Color.White, fontSize = 11.sp)
                                Text("DIR:  %s".format(bearingText), color = Color.White, fontSize = 11.sp)
                                if (note != null) {
                                    Text("NOTE: ${note.uppercase(Locale.ROOT)}", color = Color(0xFFFFCA28), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            "detailed" -> {
                                Text(
                                    text = location.address,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (note != null) {
                                    Text(
                                        text = "📌 $note",
                                        color = Color(0xFFFFCA28),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Lat: %.6f°  Long: %.6f°".format(location.latitude, location.longitude),
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "%s  •  %s  •  %s".format(altText, speedText, bearingText),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = dateText,
                                    color = Color(0xFFBAC3FF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            "hud_compass" -> {
                                Text(
                                    text = location.address,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (note != null) {
                                    Text(
                                        text = "Tag: $note",
                                        color = Color(0xFFFFCA28),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Lat: %.5f°  Long: %.5f°".format(location.latitude, location.longitude),
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "%s  •  %s".format(altText, speedText),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "DIR: %s  •  %s".format(bearingText, dateText),
                                    color = Color(0xFFBAC3FF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            else -> { // basic
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Box(
                                        Modifier
                                            .size(7.dp)
                                            .background(Color(0xFF73E08A), CircleShape)
                                    )
                                    Text(
                                        text = if (location.accuracy > 0f)
                                            "GPS FIX  •  ±%.0f m".format(location.accuracy)
                                        else "ACQUIRING GPS",
                                        color = Color(0xFFFFCF40),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.8.sp
                                    )
                                }
                                Text(
                                    text = location.address,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "%.6f°, %.6f°".format(location.latitude, location.longitude),
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "%s  •  %s  •  %s".format(altText, speedText, bearingText),
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = captureTime,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(captureDate, color = Color(0xFFFFCF40), fontSize = 10.sp)
                            }
                        }
                    }
                    
                    // Show mini dial inside preview for HUD Compass
                    if (templateId == "hud_compass") {
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .background(Color.White.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val r = size.width / 2f
                                
                                // Compass dial
                                drawCircle(color = Color.White.copy(alpha = 0.5f), radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                                
                                // North pointer needle rotated by negative bearing
                                rotate(-bearing, pivot = Offset(cx, cy)) {
                                    val needlePath = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(cx, cy - r + 4.dp.toPx())
                                        lineTo(cx - 4.dp.toPx(), cy)
                                        lineTo(cx + 4.dp.toPx(), cy)
                                        close()
                                    }
                                    drawPath(path = needlePath, color = Color.Red)
                                    
                                    // Label N
                                    drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = Offset(cx, cy))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialGpsWatermark(
    location: com.alas.md3gpscam.location.GpsLocation,
    date: String,
    time: String,
    altitude: String,
    speed: String,
    heading: String,
    customNote: String? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xD91A1B1F),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        shadowElevation = 4.dp
    ) {
        Row {
            Box(
                Modifier
                    .width(5.dp)
                    .height(if (!customNote.isNullOrBlank()) 178.dp else 154.dp)
                    .background(Color(0xFFFFC107))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.LocationOn, null, tint = Color(0xFFFFCA28),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        location.address,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                if (!customNote.isNullOrBlank()) {
                    Text(
                        text = "📌 $customNote",
                        color = Color(0xFFFFCA28),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    "%.6f°, %.6f°".format(location.latitude, location.longitude),
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelMedium
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        time,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(date, color = Color(0xFFFFCA28), fontSize = 11.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StampMetric(Icons.Rounded.Speed, speed.removePrefix("Speed: "))
                    StampMetric(Icons.Rounded.Height, altitude.removePrefix("Alt: "))
                }
            }
            Column(
                modifier = Modifier
                    .width(90.dp)
                    .height(if (!customNote.isNullOrBlank()) 178.dp else 154.dp)
                    .background(Color.White.copy(alpha = 0.055f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                StampMetric(Icons.Rounded.Explore, heading)
                StampMetric(
                    Icons.Rounded.GpsFixed,
                    if (location.accuracy > 0) "±%.0f m".format(location.accuracy) else "Locking…"
                )
            }
        }
    }
}

@Composable
private fun StampMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color(0xFFFFCA28), modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            value,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
fun GridLinesOverlay(modifier: Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // Draw grid vertical lines
        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = Offset(width / 3f, 0f),
            end = Offset(width / 3f, height),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = Offset(2f * width / 3f, 0f),
            end = Offset(2f * width / 3f, height),
            strokeWidth = 1.dp.toPx()
        )

        // Draw grid horizontal lines
        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = Offset(0f, height / 3f),
            end = Offset(width, height / 3f),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = Offset(0f, 2f * height / 3f),
            end = Offset(width, 2f * height / 3f),
            strokeWidth = 1.dp.toPx()
        )
    }
}

fun getCountryFromAddress(address: String): String {
    val addrUpper = address.uppercase()
    return when {
        addrUpper.contains("UNITED STATES") || addrUpper.contains("USA") || addrUpper.contains("MOUNTAIN VIEW") || addrUpper.contains("GOOGLEPLEX") -> "USA"
        addrUpper.contains("INDIA") || addrUpper.contains("NEW DELHI") -> "India"
        addrUpper.contains("UNITED KINGDOM") || addrUpper.contains(" UK ") || addrUpper.contains("GREAT BRITAIN") || addrUpper.contains("LONDON") -> "UK"
        addrUpper.contains("GERMANY") || addrUpper.contains("DEUTSCHLAND") || addrUpper.contains("BERLIN") -> "Germany"
        addrUpper.contains("CANADA") || addrUpper.contains("TORONTO") || addrUpper.contains("OTTAWA") -> "Canada"
        addrUpper.contains("FRANCE") || addrUpper.contains("PARIS") -> "France"
        addrUpper.contains("JAPAN") || addrUpper.contains("TOKYO") -> "Japan"
        else -> "World"
    }
}

fun drawFlagBadge(drawScope: androidx.compose.ui.graphics.drawscope.DrawScope, country: String, left: Float, top: Float, width: Float, height: Float) {
    with(drawScope) {
        drawRect(color = Color.Gray.copy(alpha = 0.5f), topLeft = Offset(left - 1f, top - 1f), size = androidx.compose.ui.geometry.Size(width + 2f, height + 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
        
        when (country) {
            "USA" -> {
                val stripeHeight = height / 7f
                for (i in 0 until 7) {
                    val color = if (i % 2 == 0) Color(0xFFD32F2F) else Color.White
                    drawRect(color = color, topLeft = Offset(left, top + i * stripeHeight), size = androidx.compose.ui.geometry.Size(width, stripeHeight))
                }
                drawRect(color = Color(0xFF1976D2), topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(width * 0.45f, stripeHeight * 4f))
            }
            "India" -> {
                val sh = height / 3f
                drawRect(color = Color(0xFFFF9800), topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(width, sh))
                drawRect(color = Color.White, topLeft = Offset(left, top + sh), size = androidx.compose.ui.geometry.Size(width, sh))
                drawRect(color = Color(0xFF4CAF50), topLeft = Offset(left, top + sh * 2f), size = androidx.compose.ui.geometry.Size(width, sh))
                drawCircle(color = Color(0xFF0D47A1), radius = sh * 0.35f, center = Offset(left + width / 2f, top + sh * 1.5f))
            }
            "Germany" -> {
                val sh = height / 3f
                drawRect(color = Color.Black, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(width, sh))
                drawRect(color = Color(0xFFD32F2F), topLeft = Offset(left, top + sh), size = androidx.compose.ui.geometry.Size(width, sh))
                drawRect(color = Color(0xFFFFEB3B), topLeft = Offset(left, top + sh * 2f), size = androidx.compose.ui.geometry.Size(width, sh))
            }
            "France" -> {
                val sw = width / 3f
                drawRect(color = Color(0xFF0D47A1), topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(sw, height))
                drawRect(color = Color.White, topLeft = Offset(left + sw, top), size = androidx.compose.ui.geometry.Size(sw, height))
                drawRect(color = Color(0xFFD32F2F), topLeft = Offset(left + sw * 2f, top), size = androidx.compose.ui.geometry.Size(sw, height))
            }
            "Japan" -> {
                drawRect(color = Color.White, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(width, height))
                drawCircle(color = Color(0xFFD32F2F), radius = height * 0.28f, center = Offset(left + width / 2f, top + height / 2f))
            }
            "UK" -> {
                drawRect(color = Color(0xFF0D47A1), topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(width, height))
                drawLine(color = Color.White, start = Offset(left, top), end = Offset(left + width, top + height), strokeWidth = 2f)
                drawLine(color = Color.White, start = Offset(left + width, top), end = Offset(left, top + height), strokeWidth = 2f)
                drawLine(color = Color(0xFFD32F2F), start = Offset(left, top), end = Offset(left + width, top + height), strokeWidth = 1f)
                drawLine(color = Color(0xFFD32F2F), start = Offset(left + width, top), end = Offset(left, top + height), strokeWidth = 1f)
                drawRect(color = Color.White, topLeft = Offset(left + width * 0.35f, top), size = androidx.compose.ui.geometry.Size(width * 0.3f, height))
                drawRect(color = Color.White, topLeft = Offset(left, top + height * 0.35f), size = androidx.compose.ui.geometry.Size(width, height * 0.3f))
                drawRect(color = Color(0xFFD32F2F), topLeft = Offset(left + width * 0.42f, top), size = androidx.compose.ui.geometry.Size(width * 0.16f, height))
                drawRect(color = Color(0xFFD32F2F), topLeft = Offset(left, top + height * 0.42f), size = androidx.compose.ui.geometry.Size(width, height * 0.16f))
            }
            "Canada" -> {
                val sw = width / 4f
                drawRect(color = Color(0xFFD32F2F), topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(sw, height))
                drawRect(color = Color.White, topLeft = Offset(left + sw, top), size = androidx.compose.ui.geometry.Size(width - sw * 2f, height))
                drawRect(color = Color(0xFFD32F2F), topLeft = Offset(left + width - sw, top), size = androidx.compose.ui.geometry.Size(sw, height))
                val leafPath = androidx.compose.ui.graphics.Path().apply {
                    val cx = left + width / 2f
                    val cy = top + height / 2f
                    moveTo(cx, cy - 4f)
                    lineTo(cx + 3f, cy)
                    lineTo(cx, cy + 4f)
                    lineTo(cx - 3f, cy)
                    close()
                }
                drawPath(leafPath, color = Color(0xFFD32F2F))
            }
            else -> {
                drawRect(color = Color(0xFFB0BEC5), topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(width, height))
                drawCircle(color = Color(0xFF00ACC1), radius = height * 0.35f, center = Offset(left + width / 2f, top + height / 2f))
                drawCircle(color = Color.White.copy(alpha = 0.5f), radius = height * 0.22f, center = Offset(left + width / 2f, top + height / 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
            }
        }
    }
}

@Composable
fun Compass(bearing: Float, modifier: Modifier = Modifier) {
    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 28f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }

    Canvas(modifier = modifier.size(60.dp)) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2, size.height / 2)

        // Draw compass background
        drawCircle(
            color = Color.Black.copy(alpha = 0.5f),
            radius = radius
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.7f),
            radius = radius,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
        )

        // Rotate canvas to point North
        rotate(-bearing, center) {
            // Draw North indicator (red triangle)
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x, center.y - radius + 5)
                lineTo(center.x - 10, center.y - radius + 25)
                lineTo(center.x + 10, center.y - radius + 25)
                close()
            }
            drawPath(path, color = Color.Red)

            // Draw cardinal points
            drawContext.canvas.nativeCanvas.drawText("N", center.x, center.y - radius + 40, textPaint)
            drawContext.canvas.nativeCanvas.drawText("S", center.x, center.y + radius - 15, textPaint)
            drawContext.canvas.nativeCanvas.drawText("W", center.x - radius + 25, center.y + 10, textPaint)
            drawContext.canvas.nativeCanvas.drawText("E", center.x + radius - 25, center.y + 10, textPaint)
        }
    }
}
