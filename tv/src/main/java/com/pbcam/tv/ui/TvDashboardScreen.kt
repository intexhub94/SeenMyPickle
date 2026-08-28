package com.pbcam.tv.ui

import androidx.annotation.OptIn
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TvDashboardScreen(viewModel: TvDashboardViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isSplashScreenActive) {
        SplashView()
    } else if (!uiState.isPaired) {
        PairingScreen(uiState = uiState, onPair = { viewModel.pairDevice(it) })
    } else {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // Layer 0: Video Player
            VideoPlayerLayer(uiState, viewModel)

            // Layer 1: Branding and Utilities (Logo, Tagline)
            BrandingOverlay()

            // Layer 2: Top Status Banner (Detached)
            StatusBanner(uiState)

            // Layer 2.5: Interactive Settings / Admin (Bottom Left)
            SettingsButton(onOpen = { viewModel.toggleSettings(true) })

            // Layer 2.6: Replay Dismiss (Floating Hint)
            if (uiState.isAutoReplayActive && uiState.status == "IDLE" && !uiState.showReplayCompletePrompt && !uiState.isReplayLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        onClick = { viewModel.dismissReplay() }
                    ) {
                        Text(
                            "Press DOWN or click to exit Replay",
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // --- REPLAY LOADING ANIMATION ---
            if (uiState.isReplayLoading) {
                ReplayLoadingOverlay()
            }

            // --- REPLAY COMPLETE PROMPT ---
            if (uiState.showReplayCompletePrompt) {
                ReplayCompleteOverlay(uiState, viewModel)
            }

            // Layer 3: Match Info and Clock (Bottom Right)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    if (uiState.status == "RECORDING" || uiState.status == "PAUSED") {
                        MatchInfoOverlay(uiState)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    DigitalClockOverlay()
                }
            }

            // --- URGENT: TABLET OFFLINE POPUP ---
            if (!uiState.isTabletOnline) {
                OfflineAlertOverlay(uiState, viewModel)
            }

            // --- ADMIN PANEL DIALOG ---
            if (uiState.isSettingsOpen) {
                AdminPanelDialog(
                    uiState = uiState,
                    onDismiss = { viewModel.toggleSettings(false) },
                    onUnpair = { viewModel.unpairDevice() }
                )
            }

            // --- CONNECTION ERROR POPUP ---
            if (!uiState.firebaseConnected) {
                ConnectionErrorOverlay()
            }
        }
    }
}

@Composable
fun SettingsButton(onOpen: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Surface(
            onClick = onOpen,
            color = if (isFocused) Color(0xFF99FF00) else Color.Black.copy(alpha = 0.5f),
            shape = CircleShape,
            modifier = Modifier
                .size(64.dp)
                .onFocusChanged { isFocused = it.isFocused }
                .shadow(elevation = 8.dp, shape = CircleShape)
                .border(2.dp, if (isFocused) Color.White else Color.Transparent, CircleShape),
            contentColor = if (isFocused) Color.Black else Color.White
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Admin",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun AdminPanelDialog(uiState: TvUiState, onDismiss: () -> Unit, onUnpair: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.width(600.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text("TV ADMIN PANEL", color = Color(0xFF99FF00), fontSize = 28.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(24.dp))
                
                // --- TECHNICAL DIAGNOSTICS ---
                Text("TECHNICAL DIAGNOSTICS", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                
                DiagnosticRow("Paired Device ID", uiState.pairedDeviceId)
                DiagnosticRow("Firebase Link", if (uiState.firebaseConnected) "CONNECTED" else "DISCONNECTED", if (uiState.firebaseConnected) Color(0xFF99FF00) else Color.Red)
                DiagnosticRow("Sync Status", uiState.debugInfo, if (uiState.isTabletOnline) Color(0xFF99FF00) else Color.Yellow)
                DiagnosticRow("Cloud Latency", if (uiState.lastUpdateTimestamp > 0L) sdf.format(Date(uiState.lastUpdateTimestamp)) else "N/A")
                DiagnosticRow("Active RTSP URL", if (uiState.rtspSubUrl != "") uiState.rtspSubUrl else if (uiState.rtspUrl != "") uiState.rtspUrl else "NONE")
                DiagnosticRow("Local Replay", if (uiState.localReplayUrl != "") uiState.localReplayUrl else "NOT READY")
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // --- ACTIONS ---
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = onUnpair,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.weight(1f).height(56.dp).focusRequester(focusRequester)
                    ) {
                        Text("PAIR NEW DEVICE", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Text("CLOSE PANEL", color = Color.White)
                    }
                }
            }
        }
    }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
fun DiagnosticRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
        Text(
            value, 
            color = valueColor, 
            fontSize = 16.sp, 
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(start = 24.dp)
        )
    }
}

@Composable
fun DigitalClockOverlay() {
    val textShadow = Shadow(
        color = Color.Black.copy(alpha = 0.8f),
        offset = Offset(4f, 4f),
        blurRadius = 8f
    )

    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("h:mm a", Locale.US)
        while(true) {
            currentTime = sdf.format(Date())
            delay(10000)
        }
    }

    Surface(
        color = Color.Black.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
    ) {
        Text(
            text = currentTime,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge.copy(shadow = textShadow),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
    }
}

@Composable
fun OfflineAlertOverlay(uiState: TvUiState, viewModel: TvDashboardViewModel) {
    val retryFocusRequester = remember { FocusRequester() }
    var isRetryFocused by remember { mutableStateOf(false) }
    var isPairFocused by remember { mutableStateOf(false) }
    
    val brandShadow = Shadow(
        color = Color.Black.copy(alpha = 0.8f),
        offset = Offset(4f, 4f),
        blurRadius = 8f
    )

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).zIndex(400f),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF99FF00)),
            modifier = Modifier.width(600.dp)
        ) {
            Column(
                modifier = Modifier.padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "⚠️", 
                    fontSize = 80.sp,
                    style = LocalTextStyle.current.copy(shadow = brandShadow)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "URGENT: TABLET DISCONNECTED",
                    color = Color(0xFF99FF00), // PickleGreen
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    style = LocalTextStyle.current.copy(shadow = brandShadow)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    "Please ensure the SeenMyPickle app is OPEN and ACTIVE on the tablet paired with this TV.",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 28.sp,
                    style = LocalTextStyle.current.copy(shadow = brandShadow)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // RETRY BUTTON
                Button(
                    onClick = { viewModel.triggerRetry() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRetryFocused) Color(0xFF99FF00) else Color(0xFF333333)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .focusRequester(retryFocusRequester)
                        .onFocusChanged { isRetryFocused = it.isFocused }
                        .border(2.dp, if (isRetryFocused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
                ) {
                    Text(
                        "RETRY NOW", 
                        color = if (isRetryFocused) Color.Black else Color.White, 
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // PAIR NEW DEVICE BUTTON
                Button(
                    onClick = { viewModel.unpairDevice() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPairFocused) Color.Red else Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .onFocusChanged { isPairFocused = it.isFocused }
                        .border(1.dp, if (isPairFocused) Color.White else Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                ) {
                    Text(
                        "PAIR NEW DEVICE", 
                        color = Color.White, 
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // COUNTDOWN
                Text(
                    "Retrying in ${uiState.retryCountdown} seconds...",
                    color = Color.Yellow,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    style = LocalTextStyle.current.copy(shadow = brandShadow)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    "Paired Device ID: ${uiState.pairedDeviceId}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 16.sp,
                    style = LocalTextStyle.current.copy(shadow = brandShadow)
                )
            }
        }
    }
    
    LaunchedEffect(Unit) {
        retryFocusRequester.requestFocus()
    }
}

@Composable
fun ConnectionErrorOverlay() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).zIndex(300f),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, Color.Red),
            modifier = Modifier.width(500.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("⚠️", fontSize = 64.sp)
                Spacer(modifier = Modifier.height(24.dp))
                Text("CONNECTION LOST", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "The TV has lost connection to the Tablet. Please check your Wi-Fi or Internet connection.",
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    fontSize = 18.sp
                )
            }
        }
    }
}

@Composable
fun SplashView() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = com.pbcam.tv.R.drawable.logo_main),
                contentDescription = "Logo",
                modifier = Modifier.height(180.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "YOUR PLAY IS RECORDED.",
                color = Color(0xFF99FF00),
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerLayer(uiState: TvUiState, viewModel: TvDashboardViewModel) {
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

    var playbackStatus by remember { mutableStateOf("Ready") }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                playbackStatus = when (playbackState) {
                    Player.STATE_IDLE -> "Stopped"
                    Player.STATE_BUFFERING -> "Buffering..."
                    Player.STATE_READY -> "Playing"
                    Player.STATE_ENDED -> {
                        viewModel.onReplayEnded()
                        "Finished"
                    }
                    else -> "Unknown"
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playbackStatus = "Source Error (Check URL)"
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // 1. Calculate the active URL based on priority
    val activeUrl = remember(uiState.status, uiState.rtspUrl, uiState.rtspSubUrl, uiState.lastRecordingUrl, uiState.localReplayUrl, uiState.isAutoReplayActive, uiState.isTabletOnline) {
        val rawUrl = if (!uiState.isTabletOnline) ""
        else if (uiState.status == "RECORDING" || uiState.status == "PAUSED") {
            if (uiState.rtspSubUrl != "") uiState.rtspSubUrl else uiState.rtspUrl
        } else if (uiState.status == "IDLE") {
            if (uiState.isAutoReplayActive && uiState.localReplayUrl != "") uiState.localReplayUrl
            else if (uiState.rtspSubUrl != "") uiState.rtspSubUrl
            else if (uiState.localReplayUrl != "") uiState.localReplayUrl
            else if (uiState.lastRecordingUrl != "") uiState.lastRecordingUrl
            else uiState.rtspUrl
        } else ""

        // Sanitize: Only allow real media protocols
        if (rawUrl.startsWith("rtsp://") || rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl
        } else {
            ""
        }
    }

    // 2. Lifecycle Sync: Only restart player when the URL actually changes
    LaunchedEffect(activeUrl) {
        if (activeUrl.isBlank()) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            return@LaunchedEffect
        }

        playbackStatus = "Connecting to stream..."
        android.util.Log.d("VideoPlayerLayer", "Active URL: $activeUrl")
        
        val mediaItem = MediaItem.Builder()
            .setUri(activeUrl)
            .setMimeType(if (activeUrl.startsWith("rtsp")) MimeTypes.APPLICATION_RTSP else MimeTypes.VIDEO_MP4)
            .build()

        val mediaSource = if (activeUrl.startsWith("rtsp")) {
            RtspMediaSource.Factory()
                .setForceUseRtpTcp(true) // Force TCP for stability (Golden Build Standard)
                .setUserAgent("SeenMyPickleTV/1.0")
                .createMediaSource(mediaItem)
        } else {
            DefaultMediaSourceFactory(context)
                .createMediaSource(mediaItem)
        }

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Subtle Connection Status Overlay
        if (playbackStatus != "Playing" && playbackStatus != "Finished" && playbackStatus != "Stopped" && !uiState.isReplayLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 120.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (playbackStatus == "Connecting to stream..." || playbackStatus == "Buffering...") {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFF99FF00),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(
                            text = playbackStatus,
                            color = if (playbackStatus == "Source Error (Check URL)") Color.Red else Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BrandingOverlay() {
    val textShadow = Shadow(
        color = Color.Black.copy(alpha = 0.8f),
        offset = Offset(4f, 4f),
        blurRadius = 8f
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = com.pbcam.tv.R.drawable.logo_main),
                contentDescription = "SeenMyPickle Logo",
                modifier = Modifier
                    .height(80.dp)
                    .shadow(elevation = 12.dp),
                contentScale = ContentScale.Fit
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "YOUR PLAY IS RECORDED.",
                color = Color(0xFF99FF00),
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                style = MaterialTheme.typography.bodyLarge.copy(shadow = textShadow)
            )
        }
    }
}

@Composable
fun StatusBanner(uiState: TvUiState) {
    val infiniteTransition = rememberInfiniteTransition(label = "banner_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val (text, color, pulse) = when {
        uiState.status == "RECORDING" -> Triple("MATCH LIVE", Color.Red, true)
        uiState.status == "PAUSED" -> Triple("MATCH PAUSED", Color.Yellow, false)
        uiState.isAutoReplayActive && uiState.status == "IDLE" -> Triple("INSTANT REPLAY", Color(0xFFFFD700), true)
        (uiState.localReplayUrl != "" || uiState.lastRecordingUrl != "") && uiState.status == "IDLE" && uiState.rtspUrl == "" -> Triple("INSTANT REPLAY", Color(0xFFFFD700), true)
        else -> Triple("LIVE FEED", Color(0xFF99FF00), false)
    }

    val textShadow = Shadow(
        color = Color.Black.copy(alpha = 0.8f),
        offset = Offset(4f, 4f),
        blurRadius = 8f
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 20.dp)
                .alpha(if (pulse) alpha else 1.0f)
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pulse) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(
                    text = text,
                    color = color,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.bodyLarge.copy(shadow = textShadow)
                )
            }
        }

        if (uiState.courtTag != "") {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp)
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(16.dp))
            ) {
                Text(
                    text = uiState.courtTag.uppercase(java.util.Locale.US),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge.copy(shadow = textShadow)
                )
            }
        }
    }
}

@Composable
fun MatchInfoOverlay(uiState: TvUiState) {
    val textShadow = Shadow(
        color = Color.Black.copy(alpha = 0.9f),
        offset = Offset(4f, 4f),
        blurRadius = 10f
    )

    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = formatDuration(uiState.duration),
            color = Color.White,
            fontSize = 72.sp,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.displayLarge.copy(shadow = textShadow)
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        uiState.players.forEach { player ->
            val masked = maskEmail(player)
            Text(
                text = masked,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge.copy(shadow = textShadow)
            )
        }
    }
}

@Composable
fun PairingScreen(uiState: TvUiState, onPair: (String) -> Unit) {
    var deviceId by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)), 
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PickleView TV v11", color = Color(0xFF99FF00), fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Enter Tablet Device ID", color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp)
            Spacer(modifier = Modifier.height(32.dp))
            TextField(
                value = deviceId,
                onValueChange = { deviceId = it },
                label = { Text("Device ID (e.g. A1B2-C3D4)") },
                modifier = Modifier.width(450.dp),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { if (deviceId != "") onPair(deviceId) },
                modifier = Modifier.height(64.dp).width(200.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF99FF00))
            ) {
                Text("PAIR NOW", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            if (uiState.debugInfo.isNotBlank() && uiState.debugInfo != "Initializing...") {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = uiState.debugInfo,
                    color = if (uiState.debugInfo.startsWith("Sync OK")) Color(0xFF99FF00) else Color.Yellow,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}

fun maskEmail(email: String): String {
    val atIndex = email.indexOf("@")
    if (atIndex <= 1) return email
    val name = email.substring(0, atIndex)
    if (name.length <= 2) return email
    
    val first = name.substring(0, 1)
    val last = name.substring(name.length - 1)
    val domain = email.substring(atIndex)
    
    return first + "***" + last + domain
}

@Composable
fun ReplayLoadingOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black).zIndex(500f),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF99FF00).copy(alpha = alpha))
                    .border(4.dp, Color(0xFF99FF00), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_media_play),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.Black
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "PREPARING REPLAY...",
                color = Color(0xFF99FF00),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
fun ReplayCompleteOverlay(uiState: TvUiState, viewModel: TvDashboardViewModel) {
    val replayFocusRequester = remember { FocusRequester() }
    var isReplayFocused by remember { mutableStateOf(false) }
    var isLiveFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).zIndex(600f),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFD700)),
            modifier = Modifier.width(600.dp)
        ) {
            Column(
                modifier = Modifier.padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("WATCH AGAIN?", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(24.dp))
                
                // --- CIRCULAR COUNTDOWN ---
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                    CircularProgressIndicator(
                        progress = { uiState.replayPromptCountdown / 20f },
                        modifier = Modifier.size(100.dp),
                        color = Color(0xFFFFD700),
                        strokeWidth = 8.dp,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                    Text(
                        "${uiState.replayPromptCountdown}",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = { viewModel.restartReplay() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isReplayFocused) Color(0xFFFFD700) else Color(0xFF333333)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .focusRequester(replayFocusRequester)
                            .onFocusChanged { isReplayFocused = it.isFocused }
                            .border(2.dp, if (isReplayFocused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
                    ) {
                        Text("REPLAY", color = if (isReplayFocused) Color.Black else Color.White, fontWeight = FontWeight.Black)
                    }

                    Button(
                        onClick = { viewModel.dismissReplay() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLiveFocused) Color.White else Color(0xFF333333)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .onFocusChanged { isLiveFocused = it.isFocused }
                            .border(2.dp, if (isLiveFocused) Color.Black else Color.Transparent, RoundedCornerShape(12.dp))
                    ) {
                        Text("BACK TO LIVE", color = if (isLiveFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        replayFocusRequester.requestFocus()
    }
}
