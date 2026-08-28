package com.pbcam.app.ui

import android.graphics.Bitmap
import android.view.OrientationEventListener
import android.view.TextureView
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource.Factory
import com.pbcam.app.data.CameraSource
import com.pbcam.app.data.PreviewState
import com.pbcam.app.data.RecordingState
import com.pbcam.app.ui.components.AdminPanel
import com.pbcam.app.ui.viewmodel.DashboardUiState
import com.pbcam.app.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onExportHistory: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recordingDurationSeconds: Long by viewModel.recordingDurationSeconds.collectAsStateWithLifecycle()
    val lastPreviewFrame: Bitmap? by viewModel.lastPreviewFrame.collectAsStateWithLifecycle()
    
    val configuration = LocalConfiguration.current
    val isTablet = configuration.smallestScreenWidthDp >= 600
    
    val controlSize = if (isTablet) 72.dp else 56.dp
    val iconSize = if (isTablet) 32.dp else 24.dp
    val sidePadding = if (isTablet) 32.dp else 16.dp
    val headerVerticalPadding = if (isTablet) 12.dp else 4.dp
    
    val headerShadow = Shadow(
        color = Color.Black.copy(alpha = 0.9f),
        offset = Offset(3f, 3f),
        blurRadius = 6f
    )

    var showAdminPanel by remember { mutableStateOf(false) }
    var showPasscodeDialog by remember { mutableStateOf(false) }
    var showShutdownDialog by remember { mutableStateOf(false) }
    var showWaiverDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    var currentRotation by remember { mutableIntStateOf(0) }
    
    DisposableEffect(Unit) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val rotation = when (orientation) {
                    in 45 until 135 -> 3
                    in 135 until 225 -> 2
                    in 225 until 315 -> 1
                    else -> 0
                }
                if (rotation != currentRotation) {
                    currentRotation = rotation
                }
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }

    var lastRecordingState by remember { mutableStateOf(uiState.recordingState) }
    LaunchedEffect(uiState.recordingState) {
        if (lastRecordingState != RecordingState.IDLE && uiState.recordingState == RecordingState.IDLE) {
            showWaiverDialog = true
        }
        lastRecordingState = uiState.recordingState
    }

    val isRecording = uiState.recordingState != RecordingState.IDLE
    val isPreviewActive = uiState.previewState == PreviewState.PLAYING
    val isPaused = uiState.recordingState == RecordingState.PAUSED
    val isConfigReady = uiState.isConfigReady
    
    // FEED logic: Only show the live camera layer during explicit preview.
    // On pause or recording, we hide the feed to allow the Frozen Frame (Layer 1.5) 
    // to take over and show the last valid court state.
    val showFeed = isPreviewActive
    val showRetainedFrame = (!showFeed) && (lastPreviewFrame != null)

    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        .border(
            width = if (isRecording) 2.dp else 0.dp,
            color = if (isRecording) {
                val infiniteTransition = rememberInfiniteTransition(label = "border_pulse")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 0.2f,
                    animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse),
                    label = "border_alpha"
                )
                Color.Red.copy(alpha = alpha)
            } else Color.Transparent,
            shape = RoundedCornerShape(0.dp)
        )
    ) {
        
        if (isConfigReady && showFeed) {
            Box(modifier = Modifier.fillMaxSize().zIndex(1f)) {
                if (uiState.cameraSource == CameraSource.RTSP) {
                    val previewUrl = if (uiState.rtspSubUrl.length > 7) uiState.rtspSubUrl else uiState.rtspUrl
                    RtspPreview(
                        url = previewUrl,
                        isPaused = !isPreviewActive,
                        isKeyboardVisible = isKeyboardVisible,
                        onFrameCaptured = { frame -> frame?.let { viewModel.updateLastFrame(it) } }
                    )
                } else {
                    InternalCameraPreview(
                        isPaused = !isPreviewActive,
                        isKeyboardVisible = isKeyboardVisible,
                        onFrameCaptured = { frame -> frame?.let { viewModel.updateLastFrame(it) } }
                    )
                }
            }
        }

        if (showRetainedFrame) {
            Box(modifier = Modifier.fillMaxSize().zIndex(1.5f)) {
                lastPreviewFrame?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Frozen Frame",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {}
            }
        }

        Box(modifier = Modifier.fillMaxSize().zIndex(2f)) {
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .padding(horizontal = 24.dp, vertical = headerVerticalPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(16.dp)
                    )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (isTablet) 12.dp else 8.dp)) {
                            Text(
                                "SeenMyPickle",
                                style = (if (isTablet) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge).copy(shadow = headerShadow),
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            PickleballIcon(modifier = Modifier.size(if (isTablet) 32.dp else 24.dp))
                            StatusPill(isConfigReady = uiState.isConfigReady, shadow = headerShadow)
                            
                            VerticalDivider(
                                modifier = Modifier.height(if (isTablet) 24.dp else 16.dp).width(1.dp),
                                color = Color.White.copy(alpha = 0.2f)
                            )
                            Text(
                                text = uiState.courtTag.uppercase(),
                                style = (if (isTablet) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium).copy(shadow = headerShadow),
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        // --- SUBTLE TV PAIRING ID ---
                        Text(
                            text = "TV PAIRING ID: ${uiState.deviceId}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                shadow = headerShadow,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            ),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val progress = uiState.uploadProgress
                    if (progress != null) {
                        Column(modifier = Modifier.padding(top = if (isTablet) 8.dp else 4.dp).width(if (isTablet) 300.dp else 200.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (isTablet) 8.dp else 4.dp)) {
                                val infiniteTransition = rememberInfiniteTransition(label = "header_pulse")
                                val alpha by infiniteTransition.animateFloat(
                                    initialValue = 1f,
                                    targetValue = 0.3f,
                                    animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
                                    label = "alpha"
                                )

                                val isFailed = uiState.uploadMessage == "FAILED"
                                
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .border(1.dp, Color.Black.copy(alpha = 0.5f), CircleShape)
                                        .padding(1.dp)
                                        .clip(CircleShape)
                                        .background(if (isFailed) Color.Red else MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                                )
                                
                                Text(
                                    text = (uiState.uploadMessage).uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(shadow = headerShadow),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isFailed) Color.Red else MaterialTheme.colorScheme.primary
                                )

                                if (isFailed && uiState.failedPipelineSessionId != null) {
                                    Spacer(Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { viewModel.retryUpload(uiState.failedPipelineSessionId!!) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, "Retry", tint = Color.Red, modifier = Modifier.size(18.dp))
                                    }
                                    
                                    Spacer(Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { viewModel.clearFailedState() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, "Dismiss", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .offset(y = 1.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.3f))
                                )
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    color = if (uiState.uploadMessage == "FAILED") Color.Red else MaterialTheme.colorScheme.primary,
                                    trackColor = Color.White.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }

                    IconButton(onClick = { showShutdownDialog = true }) {
                        Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = "Shutdown",
                            tint = Color.Red,
                            modifier = Modifier.size(32.dp).shadow(elevation = 8.dp, shape = CircleShape)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.ime)
                    .navigationBarsPadding()
                    .displayCutoutPadding()
                    .padding(sidePadding),
                contentAlignment = if (isKeyboardVisible) Alignment.Center else Alignment.BottomEnd
            ) {
                RecordingControlCard(
                    uiState = uiState,
                    viewModel = viewModel,
                    isTablet = isTablet,
                    onStart = onStartRecording,
                    onStop = onStopRecording
                )
            }

            // --- NAVIGATION & UTILITY CONTROLS (Bottom Start) ---
            // Increased zIndex to ensure it's layered ON TOP of everything
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .displayCutoutPadding()
                    .padding(sidePadding)
                    .zIndex(10f),
                color = Color.Black.copy(alpha = 0.4f),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (isTablet) 16.dp else 8.dp)
                ) {
                    DashboardIconButton(
                        icon = Icons.Default.Settings, 
                        size = controlSize,
                        iconSize = iconSize,
                        onClick = { showPasscodeDialog = true }
                    )
                    DashboardIconButton(
                        icon = if (uiState.isPreviewMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        size = controlSize,
                        iconSize = iconSize,
                        onClick = { viewModel.toggleMute() }
                    )
                    DashboardIconButton(
                        icon = if (isPreviewActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                        size = controlSize,
                        iconSize = iconSize,
                        onClick = { 
                            if (isPreviewActive) viewModel.stopPreview()
                            else viewModel.startPreview()
                        }
                    )
                }
            }

            if (isRecording && uiState.cameraSource != CameraSource.RTSP) {
                val seconds = recordingDurationSeconds
                val hrs = seconds / 3600
                val mins = (seconds % 3600) / 60
                val secs = seconds % 60
                val timeStr = if (hrs > 0) String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs) else String.format(Locale.US, "%02d:%02d", mins, secs)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(top = headerVerticalPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                        border = BorderStroke(2.dp, Color.Red.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 0.2f,
                                animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
                                label = "dot_alpha"
                            )
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color.Red.copy(alpha = alpha)))
                            
                            val bannerText = if (isPaused) {
                                "MATCH PAUSED"
                            } else {
                                "MATCH LIVE • $timeStr"
                            }

                            Text(
                                text = bannerText,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isPaused) Color.Yellow else Color.White,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }
            }

            if (isRecording && uiState.cameraSource == CameraSource.RTSP) {
                val seconds = recordingDurationSeconds
                val hrs = seconds / 3600
                val mins = (seconds % 3600) / 60
                val secs = seconds % 60
                val timeStr = if (hrs > 0) String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs) else String.format(Locale.US, "%02d:%02d", mins, secs)

                Box(
                    modifier = Modifier.fillMaxSize().zIndex(1.7f),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = if (isPaused) "MATCH PAUSED" else "MATCH LIVE",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isPaused) Color.Yellow else Color.White,
                                letterSpacing = 2.sp
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Hd,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "LIVE PREVIEW PAUSED",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (isPaused) {
                                    Icon(
                                        Icons.Default.Pause,
                                        contentDescription = null,
                                        tint = Color.Yellow,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "MATCH PAUSED",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Black,
                                        color = Color.Yellow
                                    )
                                } else {
                                    val infiniteTransition = rememberInfiniteTransition(label = "card_rec_pulse")
                                    val alpha by infiniteTransition.animateFloat(
                                        initialValue = 1f,
                                        targetValue = 0.2f,
                                        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
                                        label = "card_dot_alpha"
                                    )
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.Red.copy(alpha = alpha)))
                                    Text(
                                        text = "RECORDING $timeStr",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Black,
                                        color = Color.Red
                                    )
                                }
                            }

                            Text(
                                text = "Prioritizing hardware resources for maximum recording quality.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        if (showPasscodeDialog) {
            PasscodeEntryDialog(
                lockoutTime = uiState.lockoutEndTime,
                onVerify = viewModel::verifyPasscode,
                onSuccess = { 
                    showPasscodeDialog = false
                    showAdminPanel = true 
                    viewModel.startAdminSession()
                },
                onDismiss = { showPasscodeDialog = false }
            )
        }

        if (showShutdownDialog) {
            AlertDialog(
                onDismissRequest = { showShutdownDialog = false },
                title = { Text("Exit Application") },
                text = { Text("Are you sure you want to shut down the SeeMyPickle monitor?") },
                confirmButton = {
                    Button(onClick = { android.os.Process.killProcess(android.os.Process.myPid()) }) { Text("EXIT") }
                },
                dismissButton = {
                    TextButton(onClick = { showShutdownDialog = false }) { Text("CANCEL") }
                }
            )
        }

        if (showAdminPanel) {
            Surface(
                modifier = Modifier.fillMaxSize().zIndex(200f),
                color = MaterialTheme.colorScheme.surface
            ) {
                AdminPanel(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    viewModel = viewModel,
                    rtspUrl = uiState.rtspUrl,
                    rtspSubUrl = uiState.rtspSubUrl,
                    courtTag = uiState.courtTag,
                    cameraSource = uiState.cameraSource,
                    isAuthenticated = uiState.isAuthenticated,
                    authenticatedEmail = uiState.authenticatedEmail,
                    sessions = uiState.sessions,
                    onUpdatePasscode = viewModel::updatePasscode,
                    onSignIn = onSignIn,
                    onSignOut = onSignOut,
                    onDeleteSession = viewModel::deleteSession,
                    onExportHistory = onExportHistory,
                    onDismiss = { 
                        viewModel.logoutAdmin()
                        showAdminPanel = false 
                    }
                )
            }
        }

        if (showWaiverDialog && !showAdminPanel) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Recording Notification") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "Your recording is now processing in the background. You will receive an email notification shortly once your footage is ready for viewing."
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { 
                            showWaiverDialog = false
                            viewModel.startPreview()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("I AGREE") }
                }
            )
        }
    }
}

@Composable
fun PasscodeEntryDialog(lockoutTime: Long?, onVerify: (String) -> Boolean, onSuccess: () -> Unit, onDismiss: () -> Unit) {
    var passcode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val isLocked = lockoutTime != null && now < lockoutTime

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(if (LocalConfiguration.current.screenWidthDp >= 600) 0.6f else 0.95f)
            .padding(8.dp),
        title = { 
            Text(
                "Admin Authorization", 
                style = MaterialTheme.typography.titleLarge, 
                fontWeight = FontWeight.Black,
                color = Color.White
            ) 
        },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT SIDE: Status & PIN Boxes
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isLocked) {
                        val remaining = ((lockoutTime - now) / 1000).toInt()
                        Icon(Icons.Default.LockClock, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                        Text("Too many failed attempts.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("Try again in $remaining seconds", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    } else {
                        Text(
                            "Enter the 4-digit administrator passcode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )

                        // VISUAL PIN BOXES
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(4) { index ->
                                val char = passcode.getOrNull(index)
                                val isFocused = passcode.length == index
                                
                                Surface(
                                    modifier = Modifier
                                        .size(width = 48.dp, height = 56.dp)
                                        .border(
                                            width = if (isFocused) 2.dp else 1.dp,
                                            color = if (error) Color.Red 
                                                   else if (isFocused) Color(0xFF99FF00) // PickleGreen
                                                   else Color.White.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(8.dp)
                                        ),
                                    color = Color.Black.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (char != null) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (error) {
                            Text(
                                "Incorrect passcode", 
                                color = MaterialTheme.colorScheme.error, 
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // RIGHT SIDE: Custom Keypad (Only if not locked)
                if (!isLocked) {
                    Column(
                        modifier = Modifier.weight(1.2f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AdminNumericKeypad(
                            onNumberClick = { num ->
                                if (passcode.length < 4) {
                                    val nextPass = passcode + num
                                    passcode = nextPass
                                    error = false
                                    if (nextPass.length == 4) {
                                        if (onVerify(nextPass)) {
                                            onSuccess()
                                        } else {
                                            error = true
                                            passcode = ""
                                        }
                                    }
                                }
                            },
                            onDeleteClick = {
                                if (passcode.isNotEmpty()) {
                                    passcode = passcode.dropLast(1)
                                    error = false
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = { },
        dismissButton = {
            TextButton(onClick = onDismiss) { 
                Text("CANCEL", fontWeight = FontWeight.Bold) 
            }
        }
    )
}

@Composable
fun AdminNumericKeypad(onNumberClick: (String) -> Unit, onDeleteClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "DEL"),
        )

        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    if (key == "") {
                        Spacer(modifier = Modifier.size(width = 72.dp, height = 44.dp))
                    } else {
                        KeypadButton(
                            text = key,
                            isDelete = key == "DEL",
                            onClick = { if (key == "DEL") onDeleteClick() else onNumberClick(key) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KeypadButton(text: String, isDelete: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(width = 72.dp, height = 44.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isDelete) Color.DarkGray.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f),
        tonalElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isDelete) {
                Icon(Icons.AutoMirrored.Filled.Backspace, null, tint = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Text(text, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
        }
    }
}


@Composable
fun DashboardIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, size: androidx.compose.ui.unit.Dp, iconSize: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Black.copy(alpha = 0.5f),
        shape = CircleShape,
        modifier = Modifier.size(size).shadow(elevation = 8.dp, shape = CircleShape)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
fun StatusPill(isConfigReady: Boolean, shadow: Shadow) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaValue by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse),
        label = "alpha"
    )

    Surface(
        color = if (isConfigReady) Color(0xFF2E7D32).copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.1f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, (if (isConfigReady) Color(0xFF99FF00) else Color.Red).copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(alphaValue)
                    .clip(CircleShape)
                    .background(if (isConfigReady) Color(0xFF99FF00) else Color.Red)
            )
            Text(
                text = if (isConfigReady) "READY" else "CONFIG REQUIRED",
                style = MaterialTheme.typography.labelMedium.copy(shadow = shadow),
                fontWeight = FontWeight.Black,
                color = if (isConfigReady) Color(0xFF99FF00) else Color.Red
            )
        }
    }
}

@Composable
fun InternalCameraPreview(isPaused: Boolean, isKeyboardVisible: Boolean, onFrameCaptured: (Bitmap?) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    
    // Periodically capture frame
    LaunchedEffect(isPaused, isKeyboardVisible) {
        if (!isPaused) {
            while (true) {
                delay(1.seconds)
                if (!isKeyboardVisible) {
                    onFrameCaptured(previewView.bitmap)
                }
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            if (isPaused) {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
                return@AndroidView
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().apply {
                    surfaceProvider = view.surfaceProvider
                }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                } catch (e: Exception) {
                    android.util.Log.e("Dashboard", "Use case binding failed", e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )
}

@UnstableApi
@Composable
fun RtspPreview(url: String, isPaused: Boolean, isKeyboardVisible: Boolean, onFrameCaptured: (Bitmap?) -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            val params = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, true)
                .build()
            trackSelectionParameters = params
        }
    }

    var isLoading by remember { mutableStateOf(true) }
    var errorOccurred by remember { mutableStateOf(false) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isLoading = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) errorOccurred = false
            }
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("RtspPreview", "Player Error: ${error.message}")
                errorOccurred = true
                isLoading = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    LaunchedEffect(url, isPaused) {
        if (url.isBlank() || isPaused) {
            exoPlayer.pause()
            return@LaunchedEffect
        }
        
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMimeType(MimeTypes.APPLICATION_RTSP)
            .build()
            
        // HARDENED RECONNECTION LOOP
        var attempt = 0
        while (attempt < 3) {
            val mediaSource = Factory()
                .setForceUseRtpTcp(true)
                .createMediaSource(mediaItem)
                
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.play()
            
            // Wait for failure or success
            delay(5.seconds)
            if (exoPlayer.playbackState == Player.STATE_READY) break
            attempt++
            android.util.Log.w("RtspPreview", "Connection attempt $attempt failed, retrying...")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        var textureViewRef by remember { mutableStateOf<TextureView?>(null) }

        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    exoPlayer.setVideoTextureView(this)
                    textureViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { tv ->
                exoPlayer.setVideoTextureView(tv)
            }
        )

        LaunchedEffect(url, isPaused, textureViewRef, isKeyboardVisible) {
            if (!isPaused && url.isNotBlank()) {
                while (true) {
                    delay(2.seconds)
                    if (!isKeyboardVisible) {
                        textureViewRef?.let { tv ->
                            if (tv.isAvailable) {
                                val bitmap = tv.bitmap
                                if (bitmap != null) {
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        onFrameCaptured(bitmap)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isLoading && !errorOccurred && !isPaused) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        if (errorOccurred && !isPaused) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Error, null, tint = Color.Red, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Camera Connection Lost", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Reconnecting...", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun RecordingControlCard(uiState: DashboardUiState, viewModel: DashboardViewModel, isTablet: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    val isRecording = uiState.recordingState != RecordingState.IDLE
    val isPaused = uiState.recordingState == RecordingState.PAUSED
    val isConfigReady = uiState.isConfigReady

    Card(
        modifier = Modifier
            .width(if (isTablet) 400.dp else 320.dp)
            .shadow(
                elevation = if (isRecording) 32.dp else 12.dp, 
                shape = RoundedCornerShape(16.dp),
                spotColor = if (isRecording) Color.Red else Color.Black,
                ambientColor = if (isRecording) Color.Red else Color.Black
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.6f),
            contentColor = Color.White
        ),
        border = BorderStroke(1.dp, if (isRecording) Color.Red.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(if (isTablet) 24.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(if (isTablet) 20.dp else 12.dp)) {
            if (!isRecording) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = uiState.alertEmail,
                        onValueChange = { viewModel.updateAlertEmail(it) },
                        label = { Text(if (uiState.selectedEmails.size >= 5) "Recipient limit reached (5)" else "Email address") },
                        placeholder = { Text("Email address") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = uiState.selectedEmails.size < 5,
                        isError = uiState.alertEmail.isNotBlank() && !uiState.isEmailValid,
                        supportingText = {
                            if (uiState.alertEmail.isNotBlank() && !uiState.isEmailValid) {
                                Text("Please enter a valid email address", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                            disabledBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                            disabledLabelColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            disabledTextColor = Color.White.copy(alpha = 0.3f)
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { viewModel.addEmail(uiState.alertEmail) }),
                        trailingIcon = {
                            if ((uiState.isEmailValid) && (uiState.selectedEmails.size < 5)) {
                                IconButton(onClick = { viewModel.addEmail(uiState.alertEmail) }) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Player",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    )

                    if (uiState.selectedEmails.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.selectedEmails.forEach { email ->
                                AssistChip(
                                    onClick = { viewModel.removeEmail(email) },
                                    label = { Text(email, fontSize = 10.sp, color = Color.White) },
                                    trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp), tint = Color.White) },
                                    colors = AssistChipDefaults.assistChipColors(labelColor = Color.White, containerColor = Color.White.copy(alpha = 0.1f)),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                                )
                            }
                        }
                    }

                    val isEmailReady = uiState.selectedEmails.isNotEmpty() || uiState.isEmailValid
                    Button(
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        enabled = isConfigReady && isEmailReady,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Red,
                            contentColor = Color.Black,
                            disabledContainerColor = Color.Red.copy(alpha = 0.3f),
                            disabledContentColor = Color.Black.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(32.dp)
                    ) {
                        Icon(
                            Icons.Default.FiberManualRecord, 
                            contentDescription = null, 
                            tint = if (isConfigReady && isEmailReady) Color.Black else Color.Black.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("START MATCH", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onStop,
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(Modifier.width(8.dp))
                        Text("STOP", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { if (isPaused) viewModel.resumeRecording() else viewModel.pauseRecording() },
                        modifier = Modifier.width(64.dp).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isPaused) Color(0xFF2E7D32) else Color(0xFFE65100))
                    ) {
                        Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null)
                    }
                }
            }
        }
    }
}

@Composable
fun PickleballIcon(modifier: Modifier) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2
        drawCircle(color = Color(0xFF99FF00), radius = radius)
        for (angle in 0 until 360 step 60) {
            val x = center.x + (radius * 0.55f * Math.cos(Math.toRadians(angle.toDouble()))).toFloat()
            val y = center.y + (radius * 0.55f * Math.sin(Math.toRadians(angle.toDouble()))).toFloat()
            drawCircle(color = Color.Black.copy(alpha = 0.3f), radius = radius * 0.15f, center = Offset(x, y))
        }
        drawCircle(color = Color.Black.copy(alpha = 0.3f), radius = radius * 0.15f, center = center)
    }
}
