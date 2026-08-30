package com.pbcam.tv.ui

import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
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

            // Layer 2.5: Interactive Bottom Control Cluster (Replays & Admin)
            BottomControlCluster(uiState, viewModel)

            // --- REPLAY LIST DIALOG ---
            if (uiState.isReplayListOpen) {
                TvReplayListDialog(uiState, viewModel)
            }

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

            // --- REPLAY AVAILABLE BANNER NOTIFICATION WITH SLIDE-IN ANIMATION ---
            AnimatedVisibility(
                visible = uiState.showReplayAvailableBanner && uiState.status == "IDLE" && !uiState.isAutoReplayActive,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).zIndex(350f)
            ) {
                ReplayAvailableBanner(uiState, viewModel)
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

            var showUnpairWarning by remember { mutableStateOf(false) }

            // --- URGENT: TABLET OFFLINE POPUP ---
            if (!uiState.isTabletOnline) {
                OfflineAlertOverlay(
                    uiState = uiState, 
                    viewModel = viewModel,
                    onUnpairRequest = { showUnpairWarning = true }
                )
            }

            // --- OBSCURE FEED BACKDROP FOR DIALOGS ---
            if (uiState.isSettingsOpen || uiState.isReplayListOpen || showUnpairWarning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.90f))
                        .zIndex(390f)
                )
            }

            // --- ADMIN PANEL DIALOG ---
            if (uiState.isSettingsOpen) {
                AdminPanelDialog(
                    uiState = uiState,
                    onDismiss = { viewModel.toggleSettings(false) },
                    onUnpair = { showUnpairWarning = true },
                    onToggleStreamQuality = { viewModel.toggleStreamQuality() }
                )
            }

            // --- UNPAIR WARNING CONFIRMATION DIALOG ---
            if (showUnpairWarning) {
                UnpairWarningDialog(
                    onConfirmUnpair = {
                        showUnpairWarning = false
                        viewModel.toggleSettings(false)
                        viewModel.unpairDevice()
                    },
                    onDismiss = { showUnpairWarning = false }
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
fun BottomControlCluster(uiState: TvUiState, viewModel: TvDashboardViewModel) {
    var isReplayFocused by remember { mutableStateOf(false) }
    var isSettingsFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // REPLAY LIST LAUNCHER BUTTON
            Surface(
                onClick = { viewModel.toggleReplayList(true) },
                color = if (isReplayFocused) Color(0xFF99FF00) else Color.Black.copy(alpha = 0.6f),
                shape = CircleShape,
                modifier = Modifier
                    .size(64.dp)
                    .onFocusChanged { isReplayFocused = it.isFocused }
                    .shadow(elevation = 12.dp, shape = CircleShape)
                    .border(2.dp, if (isReplayFocused) Color.White else Color.Transparent, CircleShape),
                contentColor = if (isReplayFocused) Color.Black else Color.White
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.VideoLibrary,
                        contentDescription = "Recorded Plays Replay List",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // SETTINGS BUTTON
            Surface(
                onClick = { viewModel.toggleSettings(true) },
                color = if (isSettingsFocused) Color(0xFF99FF00) else Color.Black.copy(alpha = 0.6f),
                shape = CircleShape,
                modifier = Modifier
                    .size(64.dp)
                    .onFocusChanged { isSettingsFocused = it.isFocused }
                    .shadow(elevation = 12.dp, shape = CircleShape)
                    .border(2.dp, if (isSettingsFocused) Color.White else Color.Transparent, CircleShape),
                contentColor = if (isSettingsFocused) Color.Black else Color.White
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
}

@Composable
fun TvReplayListDialog(uiState: TvUiState, viewModel: TvDashboardViewModel) {
    val focusRequester = remember { FocusRequester() }
    val timeSdf = SimpleDateFormat("MMM dd, h:mm a", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Manila")
    }

    Dialog(onDismissRequest = { viewModel.toggleReplayList(false) }) {
        Surface(
            color = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .width(680.dp)
                .heightIn(max = 520.dp)
                .zIndex(450f),
            border = BorderStroke(2.dp, Color(0xFF99FF00))
        ) {
            Column(modifier = Modifier.padding(28.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Color(0xFF99FF00), modifier = Modifier.size(28.dp))
                        Text("RECORDED PLAYS", color = Color(0xFF99FF00), fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }
                    IconButton(onClick = { viewModel.toggleReplayList(false) }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.recentSessions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("No recorded plays available.", color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        itemsIndexed(uiState.recentSessions) { index, session ->
                            ReplayItemCard(
                                session = session,
                                timeStr = if (session.startTime > 0L) timeSdf.format(Date(session.startTime)) else "Recent Match",
                                focusRequester = if (index == 0) focusRequester else null,
                                onPlay = { viewModel.playSelectedSession(session) }
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }
}

@Composable
fun ReplayItemCard(
    session: TvReplaySession,
    timeStr: String,
    focusRequester: FocusRequester?,
    onPlay: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onPlay,
        color = if (isFocused) Color(0xFF99FF00) else Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, if (isFocused) Color.White else Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onFocusChanged { isFocused = it.isFocused }
            .shadow(elevation = if (isFocused) 12.dp else 2.dp, shape = RoundedCornerShape(16.dp)),
        contentColor = if (isFocused) Color.Black else Color.White
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.email.ifBlank { "Court Player" },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFocused) Color.Black else Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(timeStr, fontSize = 13.sp, color = if (isFocused) Color.Black.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.6f))
                    if (session.duration > 0L) {
                        Text("•  ${formatDuration(session.duration)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isFocused) Color.Black else Color(0xFF99FF00))
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = if (isFocused) Color.Black else Color(0xFF99FF00),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play Replay",
                            tint = if (isFocused) Color(0xFF99FF00) else Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "PLAY",
                            color = if (isFocused) Color(0xFF99FF00) else Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

fun sanitizeRtspUrl(url: String): String {
    if (url.isBlank() || url == "NONE") return "NONE"
    return try {
        if (url.contains("@")) {
            val prefix = url.substringBefore("://") + "://"
            val afterAt = url.substringAfter("@")
            "$prefix***:***@$afterAt"
        } else {
            url
        }
    } catch (_: Exception) {
        "rtsp://***"
    }
}

@Composable
fun AdminPanelDialog(
    uiState: TvUiState,
    onDismiss: () -> Unit,
    onUnpair: () -> Unit,
    onToggleStreamQuality: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val tvMetrics = rememberTvScreenMetrics()
    var isStreamModeFocused by remember { mutableStateOf(false) }
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Manila")
    }
    
    val activeRtspRaw = if (uiState.useMainStream) {
        if (uiState.rtspUrl != "") uiState.rtspUrl else uiState.rtspSubUrl
    } else {
        if (uiState.rtspSubUrl != "") uiState.rtspSubUrl else uiState.rtspUrl
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.width(tvMetrics.dialogWidthDp).zIndex(420f),
            border = BorderStroke(1.5.dp, Color(0xFF99FF00).copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text("TV ADMIN PANEL", color = Color(0xFF99FF00), fontSize = 28.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(20.dp))
                
                // --- STREAM MODE OPTION (LAN MAIN STREAM VS SUB STREAM) ---
                Text("STREAM SOURCE SELECTION", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                Surface(
                    onClick = onToggleStreamQuality,
                    color = if (isStreamModeFocused) Color(0xFF99FF00) else Color(0xFF2B2B2B),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(2.dp, if (isStreamModeFocused) Color.White else Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isStreamModeFocused = it.isFocused },
                    contentColor = if (isStreamModeFocused) Color.Black else Color.White
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (uiState.useMainStream) "MODE: HIGHEST QUALITY (MAIN STREAM)" else "MODE: LOW LATENCY (SUB STREAM)",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = if (isStreamModeFocused) Color.Black else Color(0xFF99FF00)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (uiState.useMainStream) "Optimized for local LAN court Wi-Fi" else "Optimized for standard/low bandwidth",
                                fontSize = 12.sp,
                                color = if (isStreamModeFocused) Color.Black.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.6f)
                            )
                        }
                        Surface(
                            color = if (isStreamModeFocused) Color.Black else Color(0xFF99FF00),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = "TOGGLE",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = if (isStreamModeFocused) Color(0xFF99FF00) else Color.Black,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                
                // --- TECHNICAL DIAGNOSTICS ---
                Text("TECHNICAL DIAGNOSTICS", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                
                DiagnosticRow("Paired Device ID", uiState.pairedDeviceId)
                DiagnosticRow("Firebase Link", if (uiState.firebaseConnected) "CONNECTED" else "DISCONNECTED", if (uiState.firebaseConnected) Color(0xFF99FF00) else Color.Red)
                DiagnosticRow("Sync Status", uiState.debugInfo, if (uiState.isTabletOnline) Color(0xFF99FF00) else Color.Yellow)
                DiagnosticRow("Cloud Latency", if (uiState.lastUpdateTimestamp > 0L) sdf.format(Date(uiState.lastUpdateTimestamp)) else "N/A")
                DiagnosticRow("Active RTSP URL", sanitizeRtspUrl(activeRtspRaw))
                DiagnosticRow("Local Replay", if (uiState.localReplayUrl != "") "READY" else "NOT READY", if (uiState.localReplayUrl != "") Color(0xFF99FF00) else Color.White.copy(alpha = 0.5f))
                
                Spacer(modifier = Modifier.height(28.dp))
                
                // --- ACTIONS ---
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = onUnpair,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.weight(1f).height(54.dp).focusRequester(focusRequester)
                    ) {
                        Text("PAIR NEW DEVICE", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                        modifier = Modifier.weight(1f).height(54.dp)
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
        val sdf = SimpleDateFormat("h:mm a", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Manila")
        }
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
fun OfflineAlertOverlay(uiState: TvUiState, viewModel: TvDashboardViewModel, onUnpairRequest: () -> Unit) {
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
                    onClick = onUnpairRequest,
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
fun UnpairWarningDialog(
    onConfirmUnpair: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var isUnpairFocused by remember { mutableStateOf(false) }
    var isCancelFocused by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(2.dp, Color.Red),
            modifier = Modifier.width(560.dp).zIndex(700f)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("⚠️", fontSize = 64.sp)
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "WARNING: UNPAIR DEVICE?",
                    color = Color.Red,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Clicking unpair will disconnect this TV and WIPE ALL saved TV app settings (pairing ID, stream preferences, and sync configurations).",
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = onConfirmUnpair,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isUnpairFocused) Color.Red else Color(0xFF333333)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .focusRequester(focusRequester)
                            .onFocusChanged { isUnpairFocused = it.isFocused }
                            .border(2.dp, if (isUnpairFocused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            "WIPE & UNPAIR",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCancelFocused) Color.White else Color(0xFF333333)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .onFocusChanged { isCancelFocused = it.isFocused }
                            .border(2.dp, if (isCancelFocused) Color.Black else Color.Transparent, RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            "CANCEL",
                            color = if (isCancelFocused) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
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
        }
    }

    var playbackStatus by remember { mutableStateOf("Ready") }
    var errorDetailReason by remember { mutableStateOf("Initializing stream...") }
    var isFallbackToSubStream by remember(uiState.rtspUrl, uiState.rtspSubUrl, uiState.useMainStream) { mutableStateOf(false) }

    // 1. Calculate the active RTSP URL based on stream preference and error fallback
    val activeRtspUrl = remember(uiState.useMainStream, uiState.rtspUrl, uiState.rtspSubUrl, isFallbackToSubStream) {
        if (isFallbackToSubStream) {
            if (uiState.rtspSubUrl != "") uiState.rtspSubUrl else uiState.rtspUrl
        } else if (uiState.useMainStream) {
            if (uiState.rtspUrl != "") uiState.rtspUrl else uiState.rtspSubUrl
        } else {
            if (uiState.rtspSubUrl != "") uiState.rtspSubUrl else uiState.rtspUrl
        }
    }

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
                val causeMsg = error.cause?.message.orEmpty()
                val errorMsg = error.message.orEmpty()
                
                val parsedDetail = when {
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    causeMsg.contains("HEVC", ignoreCase = true) || causeMsg.contains("h265", ignoreCase = true) ||
                    errorMsg.contains("HEVC", ignoreCase = true) || errorMsg.contains("h265", ignoreCase = true) ->
                        "TV hardware lacks H.265/HEVC decoder. Set Dahua camera Stream 1 to H.264 in camera settings."
                    
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                    causeMsg.contains("timeout", ignoreCase = true) ->
                        "Camera Connection Timed Out. Please check camera power and LAN network cable."
                    
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    causeMsg.contains("404") || causeMsg.contains("403") ->
                        "HTTP Server Error. Please check tablet local replay server connection."
                    
                    else -> "Unable to reach or decode stream URL. Check camera network and settings."
                }

                android.util.Log.e("VideoPlayerLayer", "Playback Error ($parsedDetail): ${error.message}", error)
                errorDetailReason = parsedDetail

                if (uiState.useMainStream && !isFallbackToSubStream && uiState.rtspSubUrl != "" && activeRtspUrl == uiState.rtspUrl) {
                    android.util.Log.w("VideoPlayerLayer", "Main stream error on TV. Falling back to Sub Stream: ${uiState.rtspSubUrl}")
                    playbackStatus = "Main Stream Error (Auto-switching to Sub-Stream...)"
                    isFallbackToSubStream = true
                } else {
                    playbackStatus = "Source Error"
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    val activeUrl = remember(uiState.status, uiState.rtspUrl, uiState.rtspSubUrl, uiState.lastRecordingUrl, uiState.localReplayUrl, uiState.isAutoReplayActive, uiState.isTabletOnline, uiState.useMainStream, isFallbackToSubStream) {
        val rawUrl = if (!uiState.isTabletOnline && !uiState.isAutoReplayActive) ""
        else if (uiState.status == "RECORDING" || uiState.status == "PAUSED") {
            activeRtspUrl
        } else if (uiState.status == "IDLE") {
            if (uiState.isAutoReplayActive && uiState.localReplayUrl != "") uiState.localReplayUrl
            else if (uiState.isAutoReplayActive && uiState.lastRecordingUrl != "") uiState.lastRecordingUrl
            else if (activeRtspUrl != "") activeRtspUrl
            else if (uiState.localReplayUrl != "") uiState.localReplayUrl
            else if (uiState.lastRecordingUrl != "") uiState.lastRecordingUrl
            else activeRtspUrl
        } else ""

        // Convert Google Drive view URL (https://drive.google.com/file/d/XYZ/view)
        // to direct MP4 video stream URL (https://drive.google.com/uc?export=download&id=XYZ)
        val directUrl = if (rawUrl.contains("drive.google.com/file/d/")) {
            val fileId = rawUrl.substringAfter("drive.google.com/file/d/").substringBefore("/")
            "https://drive.google.com/uc?export=download&id=$fileId"
        } else {
            rawUrl
        }

        // Sanitize: Only allow real media protocols
        if (directUrl.startsWith("rtsp://") || directUrl.startsWith("http://") || directUrl.startsWith("https://")) {
            directUrl
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

        // Disable audio track only for RTSP to prevent RTP sync stalls; keep enabled for HTTP MP4 replay
        val isRtsp = activeUrl.startsWith("rtsp")
        val params = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, isRtsp)
            .build()
        exoPlayer.trackSelectionParameters = params
        
        val mediaItem = if (isRtsp) {
            MediaItem.Builder()
                .setUri(activeUrl)
                .setMimeType(MimeTypes.APPLICATION_RTSP)
                .build()
        } else {
            MediaItem.fromUri(activeUrl)
        }

        val mediaSource = if (isRtsp) {
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

        // High-Visibility Error & Failover Diagnostic Banner
        if ((playbackStatus == "Source Error" || isFallbackToSubStream) && !uiState.isReplayLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 120.dp),
                contentAlignment = Alignment.Center
            ) {
                StreamErrorBanner(
                    title = if (isFallbackToSubStream) "AUTOMATIC STREAM FAILOVER" else "STREAM PLAYBACK ERROR",
                    message = if (isFallbackToSubStream) 
                        "Main Stream failed ($errorDetailReason). Switched to Sub-Stream." 
                    else 
                        errorDetailReason,
                    isWarning = isFallbackToSubStream
                )
            }
        } else if (playbackStatus != "Playing" && playbackStatus != "Finished" && playbackStatus != "Stopped" && !uiState.isReplayLoading) {
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
                            color = Color.White,
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
fun StreamErrorBanner(
    title: String,
    message: String,
    isWarning: Boolean = true
) {
    Surface(
        color = Color(0xFF1E1E1E).copy(alpha = 0.95f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, if (isWarning) Color(0xFFFFD700) else Color.Red),
        modifier = Modifier
            .width(580.dp)
            .padding(16.dp)
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(if (isWarning) "⚡" else "⚠️", fontSize = 22.sp)
                Text(
                    title,
                    color = if (isWarning) Color(0xFFFFD700) else Color.Red,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                message,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Press SETTINGS gear on remote for technical diagnostics & camera config",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
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
    var isInputFocused by remember { mutableStateOf(false) }
    var isPairButtonFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val tvMetrics = rememberTvScreenMetrics()

    val brandShadow = Shadow(
        color = Color.Black.copy(alpha = 0.8f),
        offset = Offset(4f, 4f),
        blurRadius = 8f
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F10)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color(0xFF1B1C1E),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(2.dp, Color(0xFF99FF00).copy(alpha = 0.6f)),
            modifier = Modifier
                .width(tvMetrics.pairingCardWidthDp)
                .shadow(elevation = 24.dp, shape = RoundedCornerShape(28.dp))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // LOGO / BRANDING HEADER
                Image(
                    painter = painterResource(id = com.pbcam.tv.R.drawable.logo_main),
                    contentDescription = "SeenMyPickle Logo",
                    modifier = Modifier
                        .height(90.dp)
                        .shadow(elevation = 12.dp),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "COURT DISPLAY PAIRING",
                    color = Color(0xFF99FF00),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    style = LocalTextStyle.current.copy(shadow = brandShadow)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Enter the Pairing ID shown on the court tablet header",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // PAIRING ID INPUT FIELD
                OutlinedTextField(
                    value = deviceId,
                    onValueChange = { input ->
                        val clean = input.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(12)
                        deviceId = clean
                    },
                    placeholder = { 
                        Text(
                            "e.g. PB-A1B2-C3D4", 
                            color = Color.White.copy(alpha = 0.35f),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        ) 
                    },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        letterSpacing = 2.sp
                    ),
                    modifier = Modifier
                        .width(480.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { isInputFocused = it.isFocused }
                        .border(
                            2.dp, 
                            if (isInputFocused) Color(0xFF99FF00) else Color.White.copy(alpha = 0.2f), 
                            RoundedCornerShape(16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color(0xFF2B2C30),
                        unfocusedContainerColor = Color(0xFF222326)
                    )
                )

                Spacer(modifier = Modifier.height(28.dp))

                // PAIR BUTTON
                Button(
                    onClick = { if (deviceId.isNotBlank()) onPair(deviceId) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPairButtonFocused) Color(0xFF99FF00) else Color(0xFF333438)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .width(260.dp)
                        .height(60.dp)
                        .onFocusChanged { isPairButtonFocused = it.isFocused }
                        .border(
                            2.dp, 
                            if (isPairButtonFocused) Color.White else Color.Transparent, 
                            RoundedCornerShape(16.dp)
                        )
                        .shadow(elevation = if (isPairButtonFocused) 12.dp else 2.dp, shape = RoundedCornerShape(16.dp))
                ) {
                    Text(
                        "PAIR NOW", 
                        color = if (isPairButtonFocused) Color.Black else Color.White, 
                        fontWeight = FontWeight.Black, 
                        fontSize = 20.sp,
                        letterSpacing = 1.sp
                    )
                }

                // STATUS INDICATOR
                if (uiState.debugInfo.isNotBlank() && uiState.debugInfo != "Initializing...") {
                    Spacer(modifier = Modifier.height(24.dp))
                    Surface(
                        color = Color.Black.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (uiState.debugInfo.startsWith("Sync OK")) Color(0xFF99FF00) else Color.Yellow)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = uiState.debugInfo,
                                color = if (uiState.debugInfo.startsWith("Sync OK")) Color(0xFF99FF00) else Color.Yellow,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
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
fun ReplayAvailableBanner(uiState: TvUiState, viewModel: TvDashboardViewModel) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 90.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            onClick = { viewModel.acceptReplay() },
            color = if (isFocused) Color(0xFF99FF00) else Color.Black.copy(alpha = 0.85f),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(2.dp, if (isFocused) Color.White else Color(0xFF99FF00)),
            modifier = Modifier
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(20.dp)),
            contentColor = if (isFocused) Color.Black else Color.White
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "🎬", 
                    fontSize = 24.sp
                )
                
                Column {
                    Text(
                        "MATCH REPLAY AVAILABLE",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = if (isFocused) Color.Black else Color(0xFF99FF00)
                    )
                    Text(
                        "Press OK on remote to watch",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFocused) Color.Black.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.8f)
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isFocused) Color.Black else Color(0xFF99FF00))
                ) {
                    Text(
                        "${uiState.replayBannerCountdown}",
                        color = if (isFocused) Color(0xFF99FF00) else Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }

                IconButton(
                    onClick = { viewModel.dismissReplayBanner() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss Replay Notification",
                        tint = if (isFocused) Color.Black else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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
