package com.pbcam.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import com.pbcam.app.data.WatermarkPosition
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pbcam.app.data.CameraSource
import com.pbcam.app.data.db.RecordingStatus
import com.pbcam.app.ui.CameraCredentialDialog
import com.pbcam.app.ui.LocalVideoPlayerDialog
import com.pbcam.app.ui.SessionList
import com.pbcam.app.ui.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AdminPanel(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel,
    rtspUrl: String,
    rtspSubUrl: String,
    courtTag: String,
    cameraSource: CameraSource,
    isAuthenticated: Boolean,
    authenticatedEmail: String?,
    sessions: List<com.pbcam.app.data.db.RecordingSession>,
    onUpdatePasscode: (String) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteSession: (com.pbcam.app.data.db.RecordingSession) -> Unit,
    onExportHistory: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    // Local edit states for "Zero-Latency" and change detection
    var localRtsp by remember(rtspUrl) { mutableStateOf(rtspUrl) }
    var localRtspSub by remember(rtspSubUrl) { mutableStateOf(rtspSubUrl) }
    var localCourt by remember(courtTag) { mutableStateOf(courtTag) }
    var localSource by remember(cameraSource) { mutableStateOf(cameraSource) }
    var localMinutes by remember(uiState.maxRecordingMinutes) { mutableStateOf(uiState.maxRecordingMinutes) }
    var localRecTimeout by remember(uiState.previewTimeoutRecMins) { mutableIntStateOf(uiState.previewTimeoutRecMins) }
    var localIdleTimeout by remember(uiState.previewTimeoutIdleMins) { mutableIntStateOf(uiState.previewTimeoutIdleMins) }
    var localRetention by remember(uiState.retentionDays) { mutableIntStateOf(uiState.retentionDays) }
    
    val hasChanges = localRtsp != rtspUrl || 
                    localRtspSub != rtspSubUrl ||
                    localCourt != courtTag || 
                    localSource != cameraSource ||
                    localMinutes != uiState.maxRecordingMinutes ||
                    localRecTimeout != uiState.previewTimeoutRecMins ||
                    localIdleTimeout != uiState.previewTimeoutIdleMins ||
                    localRetention != uiState.retentionDays
    
    var newPasscode by remember { mutableStateOf("") }
    var selectedCameraIp by remember { mutableStateOf<String?>(null) }
    var showCredentialDialog by remember { mutableStateOf(false) }
    var showSavedDialog by remember { mutableStateOf(false) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var showLicenseRenewalDialog by remember { mutableStateOf(false) }
    var renewalKey by remember { mutableStateOf("") }
    var selectedVideoPath by remember { mutableStateOf<String?>(null) }
    var currentFilter by remember { mutableStateOf<CameraSource?>(null) } 
    var showFullHistory by remember { mutableStateOf(false) }
    var showRecordedVideos by remember { mutableStateOf(false) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    BackHandler {
        if (showFullHistory) showFullHistory = false
        else if (showRecordedVideos) showRecordedVideos = false
        else if (hasChanges) showUnsavedChangesDialog = true 
        else onDismiss()
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("Purge History") },
            text = { Text("Are you sure you want to permanently delete all completed and failed recording logs? Active recordings and uploads will be skipped.") },
            confirmButton = {
                Button(
                    onClick = { 
                        viewModel.deleteAllLogs()
                        showDeleteAllConfirm = false 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("DELETE ALL") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) { Text("CANCEL") }
            }
        )
    }

    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = { Text("Exit Admin Mode") },
            text = { Text("Are you sure you want to logout from settings?") },
            confirmButton = {
                Button(onClick = { 
                    showLogoutConfirmDialog = false
                    viewModel.logoutAdmin()
                    onDismiss() 
                }) { Text("LOGOUT") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) { Text("CANCEL") }
            }
        )
    }

    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have modified settings. Do you want to save them before closing?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateMaxRecordingMinutes(localMinutes)
                    viewModel.updatePreviewTimeouts(localRecTimeout, localIdleTimeout)
                    viewModel.updateRetentionDays(localRetention)
                    viewModel.saveAdminSettings(newPasscode, localRtsp, localRtspSub, localCourt, localSource)
                    showUnsavedChangesDialog = false
                    viewModel.logoutAdmin()
                    onDismiss()
                }) { Text("SAVE") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showUnsavedChangesDialog = false
                    viewModel.logoutAdmin()
                    onDismiss() 
                }) { Text("DISCARD") }
            }
        )
    }

    Column(
        modifier = modifier.fillMaxSize().padding(if (isTablet) 16.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        
        // --- HEADER ---
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Admin Panel", 
                    style = if (isTablet) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium, 
                    fontWeight = FontWeight.Black
                )
                IconButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://seenmypickle-landing.web.app/"))
                            context.startActivity(intent)
                        } catch (e: Exception) {}
                    },
                    modifier = Modifier.size(if (isTablet) 48.dp else 32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Language, 
                        contentDescription = "Visit Website", 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(if (isTablet) 32.dp else 24.dp)
                    )
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (hasChanges) {
                    // TABLET-ONLY Header indicator
                    if (isTablet) {
                        Text(
                            "UNSAVED CHANGES", 
                            style = MaterialTheme.typography.labelLarge, 
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    // TABLET-ONLY Header SAVE Button (High Reachability)
                    if (isTablet) {
                        Button(
                            onClick = { 
                                viewModel.updateMaxRecordingMinutes(localMinutes)
                                viewModel.updatePreviewTimeouts(localRecTimeout, localIdleTimeout)
                                viewModel.updateRetentionDays(localRetention)
                                viewModel.saveAdminSettings(newPasscode, localRtsp, localRtspSub, localCourt, localSource)
                                showSavedDialog = true
                            },
                            modifier = Modifier.height(56.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Save, null)
                            Spacer(Modifier.width(8.dp))
                            Text("SAVE ALL", fontWeight = FontWeight.Black)
                        }
                    }
                }
                
                TextButton(
                    onClick = { 
                        if (hasChanges) showUnsavedChangesDialog = true 
                        else {
                            viewModel.logoutAdmin()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.height(if (isTablet) 48.dp else 36.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Logout", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // --- MAIN CONTENT AREA ---
        if (isTablet) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                // LEFT COLUMN: SETTINGS
                Column(
                    modifier = Modifier.weight(0.45f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    SettingsSection(
                        uiState = uiState,
                        viewModel = viewModel,
                        localRtsp = localRtsp,
                        localRtspSub = localRtspSub,
                        onRtspChange = { localRtsp = it },
                        onRtspSubChange = { localRtspSub = it },
                        localCourt = localCourt,
                        onCourtChange = { localCourt = it },
                        localSource = localSource,
                        onSourceChange = { localSource = it },
                        localMinutes = localMinutes,
                        onMinutesChange = { localMinutes = it },
                        localRecTimeout = localRecTimeout,
                        onRecTimeoutChange = { localRecTimeout = it },
                        localIdleTimeout = localIdleTimeout,
                        onIdleTimeoutChange = { localIdleTimeout = it },
                        localRetention = localRetention,
                        onRetentionChange = { localRetention = it },
                        newPasscode = newPasscode,
                        onPasscodeChange = { newPasscode = it },
                        onUpdatePasscode = onUpdatePasscode,
                        isAuthenticated = isAuthenticated,
                        onSignIn = onSignIn,
                        onSignOut = onSignOut,
                        onLicenseRenewal = { renewalKey = ""; showLicenseRenewalDialog = true },
                        onWatermarkPositionChange = viewModel::updateWatermarkPosition,
                        onWatermarkUpload = viewModel::saveCustomWatermark,
                        onWatermarkClear = viewModel::clearCustomWatermark
                    )
                }

                // RIGHT COLUMN: HISTORY
                Column(
                    modifier = Modifier.weight(0.55f).fillMaxHeight()
                ) {
                    HistorySection(
                        sessions = sessions,
                        pipelineProgress = uiState.pipelineProgress,
                        currentFilter = currentFilter,
                        onFilterChange = { currentFilter = it },
                        onExport = onExportHistory,
                        onRetry = { id ->
                            val session = sessions.find { it.id == id }
                            if (session?.status == RecordingStatus.COMPLETED) viewModel.resendNotification(id)
                            else viewModel.retryUpload(id)
                        },
                        onPlay = { path -> selectedVideoPath = path },
                        onDelete = onDeleteSession,
                        onDeleteAll = { showDeleteAllConfirm = true },
                        onViewAll = { showFullHistory = true },
                        onViewRecordings = { showRecordedVideos = true },
                        onRefresh = { viewModel.refresh() }
                    )
                }
            }
        } else {
            // PHONE LAYOUT
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SettingsSection(
                    uiState = uiState,
                    viewModel = viewModel,
                    localRtsp = localRtsp,
                    localRtspSub = localRtspSub,
                    onRtspChange = { localRtsp = it },
                    onRtspSubChange = { localRtspSub = it },
                    localCourt = localCourt,
                    onCourtChange = { localCourt = it },
                    localSource = localSource,
                    onSourceChange = { localSource = it },
                    localMinutes = localMinutes,
                    onMinutesChange = { localMinutes = it },
                    localRecTimeout = localRecTimeout,
                    onRecTimeoutChange = { localRecTimeout = it },
                    localIdleTimeout = localIdleTimeout,
                    onIdleTimeoutChange = { localIdleTimeout = it },
                    localRetention = localRetention,
                    onRetentionChange = { localRetention = it },
                    newPasscode = newPasscode,
                    onPasscodeChange = { newPasscode = it },
                    onUpdatePasscode = onUpdatePasscode,
                    isAuthenticated = isAuthenticated,
                    onSignIn = onSignIn,
                    onSignOut = onSignOut,
                    onLicenseRenewal = { renewalKey = ""; showLicenseRenewalDialog = true },
                    onWatermarkPositionChange = viewModel::updateWatermarkPosition,
                    onWatermarkUpload = viewModel::saveCustomWatermark,
                    onWatermarkClear = viewModel::clearCustomWatermark
                )

                Button(
                    onClick = { 
                        viewModel.updateMaxRecordingMinutes(localMinutes)
                        viewModel.updatePreviewTimeouts(localRecTimeout, localIdleTimeout)
                        viewModel.updateRetentionDays(localRetention)
                        viewModel.saveAdminSettings(newPasscode, localRtsp, localRtspSub, localCourt, localSource)
                        showSavedDialog = true
                    }, 
                    modifier = Modifier.fillMaxWidth().height(56.dp), 
                    enabled = hasChanges,
                    shape = MaterialTheme.shapes.medium
                ) { 
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("SAVE ALL CHANGES")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                HistorySection(
                    sessions = sessions,
                    pipelineProgress = uiState.pipelineProgress,
                    currentFilter = currentFilter,
                    onFilterChange = { currentFilter = it },
                    onExport = onExportHistory,
                    onRetry = { id ->
                        val session = sessions.find { it.id == id }
                        if (session?.status == RecordingStatus.COMPLETED) viewModel.resendNotification(id)
                        else viewModel.retryUpload(id)
                    },
                    onPlay = { path -> selectedVideoPath = path },
                    onDelete = onDeleteSession,
                    onDeleteAll = { showDeleteAllConfirm = true },
                    onViewAll = { showFullHistory = true },
                    onViewRecordings = { showRecordedVideos = true },
                    onRefresh = { viewModel.refresh() }
                )
            }
        }
    }

    if (showRecordedVideos) {
        RecordedVideosPane(
            sessions = sessions,
            pipelineProgress = uiState.pipelineProgress,
            onDeleteSession = onDeleteSession,
            onRetry = { id ->
                val session = sessions.find { it.id == id }
                if (session?.status == RecordingStatus.COMPLETED) viewModel.resendNotification(id)
                else viewModel.retryUpload(id)
            },
            onPlay = { path -> selectedVideoPath = path },
            onRefresh = { viewModel.refresh() },
            onBack = { showRecordedVideos = false }
        )
    }

    if (showFullHistory) {
        FullHistoryPane(
            sessions = sessions,
            pipelineProgress = uiState.pipelineProgress,
            onDeleteSession = onDeleteSession,
            onDeleteAll = { viewModel.deleteAllLogs() },
            onExport = onExportHistory,
            onRetry = { id ->
                val session = sessions.find { it.id == id }
                if (session?.status == RecordingStatus.COMPLETED) viewModel.resendNotification(id)
                else viewModel.retryUpload(id)
            },
            onPlay = { path -> selectedVideoPath = path },
            onRefresh = { viewModel.refresh() },
            onBack = { showFullHistory = false }
        )
    }

    if (showSavedDialog) { AlertDialog(onDismissRequest = { showSavedDialog = false }, title = { Text("Settings Saved") }, text = { Text("Your configuration has been updated and synced.") }, confirmButton = { TextButton(onClick = { showSavedDialog = false }) { Text("OK") } }) }
    if (showCredentialDialog && selectedCameraIp != null) { CameraCredentialDialog(ip = selectedCameraIp!!, onConfirm = { username, password -> val encodedUser = java.net.URLEncoder.encode(username, "UTF-8"); val encodedPass = java.net.URLEncoder.encode(password, "UTF-8"); localRtsp = "rtsp://$encodedUser:$encodedPass@$selectedCameraIp:554/stream1"; localRtspSub = "rtsp://$encodedUser:$encodedPass@$selectedCameraIp:554/stream2"; showCredentialDialog = false }, onDismiss = { showCredentialDialog = false }) }
    
    if (showLicenseRenewalDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseRenewalDialog = false },
            title = { Text("Renew or Change License") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter your new SeenMyPickle License Key below:", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = renewalKey,
                        onValueChange = { input -> 
                            val filtered = input.uppercase().filter { it.isLetterOrDigit() || it == '-' }
                            renewalKey = filtered
                        },
                        label = { Text("License Key") },
                        placeholder = { Text("XXXX-XXXX-XXXX-XXXX") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (viewModel.activateLicense(renewalKey)) {
                            showLicenseRenewalDialog = false
                        }
                    },
                    enabled = renewalKey.length >= 8
                ) { Text("ACTIVATE") }
            },
            dismissButton = {
                TextButton(onClick = { showLicenseRenewalDialog = false }) { Text("CANCEL") }
            }
        )
    }
    
    if (selectedVideoPath != null) {
        LocalVideoPlayerDialog(
            videoPath = selectedVideoPath!!,
            onDismiss = { selectedVideoPath = null }
        )
    }
}

@Composable
private fun SettingsSection(
    uiState: com.pbcam.app.ui.viewmodel.DashboardUiState,
    viewModel: DashboardViewModel,
    localRtsp: String,
    localRtspSub: String,
    onRtspChange: (String) -> Unit,
    onRtspSubChange: (String) -> Unit,
    localCourt: String,
    onCourtChange: (String) -> Unit,
    localSource: com.pbcam.app.data.CameraSource,
    onSourceChange: (com.pbcam.app.data.CameraSource) -> Unit,
    localMinutes: Int,
    onMinutesChange: (Int) -> Unit,
    localRecTimeout: Int,
    onRecTimeoutChange: (Int) -> Unit,
    localIdleTimeout: Int,
    onIdleTimeoutChange: (Int) -> Unit,
    localRetention: Int,
    onRetentionChange: (Int) -> Unit,
    newPasscode: String,
    onPasscodeChange: (String) -> Unit,
    onUpdatePasscode: (String) -> Unit,
    isAuthenticated: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onLicenseRenewal: () -> Unit,
    onWatermarkPositionChange: (WatermarkPosition) -> Unit,
    onWatermarkUpload: (android.net.Uri) -> Unit,
    onWatermarkClear: () -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let { onWatermarkUpload(it) }
    }

    // LOCAL INPUT BUFFERING: Code Bible Rule 3.2
    var localRecText by remember(localRecTimeout) { mutableStateOf(localRecTimeout.toString()) }
    var localIdleText by remember(localIdleTimeout) { mutableStateOf(localIdleTimeout.toString()) }
    var localRetentionText by remember(localRetention) { mutableStateOf(localRetention.toString()) }
    var localDurationText by remember(localMinutes) { mutableStateOf(localMinutes.toString()) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // --- DEVICE SETTINGS ---
        AdminSection(title = "Device Settings", icon = Icons.Default.Smartphone) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("App Theme", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                com.pbcam.app.data.AppTheme.entries.forEach { mode ->
                    FilterChip(
                        selected = uiState.themeMode == mode, 
                        onClick = { viewModel.updateThemeMode(mode) }, 
                        label = { Text(mode.name) }
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Preview Timeouts (Minutes)", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = localRecText,
                        onValueChange = { input -> 
                            val clean = input.filter { it.isDigit() }.take(2)
                            localRecText = clean
                            clean.toIntOrNull()?.let { onRecTimeoutChange(it.coerceIn(1, 60)) }
                        },
                        label = { Text("During Match") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = localIdleText,
                        onValueChange = { input -> 
                            val clean = input.filter { it.isDigit() }.take(2)
                            localIdleText = clean
                            clean.toIntOrNull()?.let { onIdleTimeoutChange(it.coerceIn(1, 60)) }
                        },
                        label = { Text("During Idle") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                }
            }
            
            OutlinedTextField(
                value = newPasscode, 
                onValueChange = onPasscodeChange, 
                label = { Text("Change Admin Passcode") }, 
                modifier = Modifier.fillMaxWidth(), 
                visualTransformation = PasswordVisualTransformation(), 
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                trailingIcon = {
                    TextButton(onClick = { if (newPasscode.length >= 4) onUpdatePasscode(newPasscode) }, enabled = newPasscode.length >= 4) {
                        Text("UPDATE")
                    }
                }
            )

            Button(
                onClick = { 
                    viewModel.runStorageMaintenance()
                    android.widget.Toast.makeText(context, "Storage cleanup started...", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
            ) {
                Icon(Icons.Default.DeleteSweep, null)
                Spacer(Modifier.width(8.dp))
                Text("CLEANUP LOCAL STORAGE")
            }
        }

        // --- WATERMARK CUSTOMIZATION ---
        AdminSection(title = "Watermark Customization", icon = Icons.Default.BrandingWatermark) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 1. Logo Selection
                Text("Custom Logo", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.customWatermarkPath != null) {
                            val bitmap = BitmapFactory.decodeFile(uiState.customWatermarkPath)
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Custom Watermark",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            } else {
                                Icon(Icons.Default.Image, null, tint = Color.White.copy(alpha = 0.3f))
                            }
                        } else {
                            Icon(Icons.Default.LogoDev, null, tint = Color.White.copy(alpha = 0.3f))
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { logoPicker.launch("image/*") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("UPLOAD LOGO")
                        }
                        if (uiState.customWatermarkPath != null) {
                            TextButton(
                                onClick = onWatermarkClear,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Reset to Default", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                // 2. Position Selection
                Text("Logo Position", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WatermarkPositionChip(
                            position = WatermarkPosition.TOP_LEFT,
                            selected = uiState.watermarkPosition == WatermarkPosition.TOP_LEFT,
                            onSelect = onWatermarkPositionChange,
                            modifier = Modifier.weight(1f)
                        )
                        WatermarkPositionChip(
                            position = WatermarkPosition.TOP_RIGHT,
                            selected = uiState.watermarkPosition == WatermarkPosition.TOP_RIGHT,
                            onSelect = onWatermarkPositionChange,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WatermarkPositionChip(
                            position = WatermarkPosition.BOTTOM_LEFT,
                            selected = uiState.watermarkPosition == WatermarkPosition.BOTTOM_LEFT,
                            onSelect = onWatermarkPositionChange,
                            modifier = Modifier.weight(1f)
                        )
                        WatermarkPositionChip(
                            position = WatermarkPosition.BOTTOM_RIGHT,
                            selected = uiState.watermarkPosition == WatermarkPosition.BOTTOM_RIGHT,
                            onSelect = onWatermarkPositionChange,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // --- CAMERA & BACKGROUND ---
        AdminSection(title = "Camera & Background", icon = Icons.Default.CameraAlt, initialExpanded = true) {
            Text("Source Type", style = MaterialTheme.typography.labelLarge)
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(localSource.name)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.fillMaxWidth(0.9f) ) {
                    com.pbcam.app.data.CameraSource.entries.forEach { source ->
                        DropdownMenuItem(text = { Text(source.name) }, onClick = { onSourceChange(source); expanded = false })
                    }
                }
            }

            if (localSource == com.pbcam.app.data.CameraSource.RTSP) {
                OutlinedTextField(value = localRtsp, onValueChange = onRtspChange, label = { Text("Main Stream URL (Recording)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = localRtspSub, onValueChange = onRtspSubChange, label = { Text("Sub Stream URL (Preview)") }, modifier = Modifier.fillMaxWidth())
                
                Button(
                    onClick = { viewModel.scanForCameras() }, 
                    enabled = !uiState.isScanning, 
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(if (uiState.isScanning) Icons.Default.Sync else Icons.Default.Search, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (uiState.isScanning) "Searching..." else "Auto-Detect Cameras")
                }
                
                if (uiState.discoveredCameras.isNotEmpty()) {
                    Text("Discovered Cameras (Tap to populate both streams):", style = MaterialTheme.typography.labelMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.discoveredCameras.forEach { cam ->
                            Card(
                                onClick = { 
                                    // These will trigger the credential dialog and then auto-fill
                                    // If already configured, just update the IP part
                                    onRtspChange("rtsp://${cam.ip}:554/stream1") 
                                    onRtspSubChange("rtsp://${cam.ip}:554/stream2")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Camera, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(cam.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    Text(cam.ip, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(value = localCourt, onValueChange = onCourtChange, label = { Text("Court Identifier") }, modifier = Modifier.fillMaxWidth())

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Max Recording Duration", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Slider(
                        value = localMinutes.toFloat(),
                        onValueChange = { onMinutesChange(it.toInt()) },
                        valueRange = 10f..360f, // 10 mins to 6 hours
                        steps = 34, // 10 min increments roughly
                        modifier = Modifier.weight(1f)
                    )
                    
                    OutlinedTextField(
                        value = localDurationText,
                        onValueChange = { input ->
                            val clean = input.filter { it.isDigit() }.take(3)
                            localDurationText = clean
                            clean.toIntOrNull()?.let { onMinutesChange(it.coerceIn(1, 480)) }
                        },
                        label = { Text("Mins") },
                        modifier = Modifier.width(85.dp),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    )
                }
                
                val hrs = localMinutes / 60
                val mins = localMinutes % 60
                Text(
                    text = if (hrs > 0) "$hrs Hours $mins Mins" else "$mins Minutes",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // --- APPLICATION UPDATES ---
        AdminSection(title = "Application Updates", icon = Icons.Default.SystemUpdate) {
            if (uiState.updateAvailable != null && !uiState.isCheckingUpdates) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Update Available", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text("Version v${uiState.updateAvailable}", style = MaterialTheme.typography.labelSmall)
                        }
                                
                        if (uiState.isDownloadingUpdate) {
                            CircularProgressIndicator(
                                progress = { uiState.updateDownloadProgress },
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                        } else {
                            Button(
                                onClick = { viewModel.startUpdateDownload() },
                                modifier = Modifier.height(48.dp),
                                shape = MaterialTheme.shapes.medium,
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("INSTALL", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    if (uiState.isDownloadingUpdate) {
                        LinearProgressIndicator(
                            progress = { uiState.updateDownloadProgress },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (uiState.isCheckingUpdates) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Checking for updates...", style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.manualCheckForUpdates() },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.Sync, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Check for Updates", fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    uiState.updateCheckStatus?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (status.contains("failed", true) || status.contains("error", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // --- LICENSE ---
        AdminSection(title = "License Management", icon = Icons.Default.VerifiedUser) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f))) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Device ID", style = MaterialTheme.typography.labelSmall)
                        Text(uiState.deviceId, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Status", style = MaterialTheme.typography.bodyMedium)
                        Text(if (uiState.isLicensed) "AUTHORIZED" else "UNLICENSED", color = if (uiState.isLicensed) Color(0xFF2E7D32) else Color.Red, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onLicenseRenewal, 
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Change License Key")
                    }
                }
            }
        }

        // --- CLOUD & STORAGE ---
        AdminSection(title = "Cloud & Storage", icon = Icons.Default.CloudQueue) {
            if (isAuthenticated) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AccountCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Text(uiState.authenticatedEmail ?: "Connected", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        
                        if (uiState.cloudStorageLimit != null && uiState.cloudStorageUsage != null) {
                            val usage = uiState.cloudStorageUsage
                            val limit = uiState.cloudStorageLimit
                            
                            // If limit is -1, it means unlimited
                            if (limit > 0) {
                                val progress = usage.toFloat() / limit.toFloat()
                                val usageGB = "%.2f".format(usage / (1024.0 * 1024.0 * 1024.0))
                                val limitGB = "%.0f".format(limit / (1024.0 * 1024.0 * 1024.0))
                                
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Drive Storage", style = MaterialTheme.typography.labelSmall)
                                        Text("$usageGB / $limitGB GB", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.extraSmall),
                                        color = if (progress > 0.9f) Color.Red else MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            } else {
                                Text("Storage: Unlimited", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        TextButton(
                            onClick = onSignOut, 
                            modifier = Modifier.align(Alignment.End).height(48.dp)
                        ) {
                            Text("Disconnect Account", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Button(
                    onClick = onSignIn, 
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.CloudQueue, null)
                    Spacer(Modifier.width(12.dp))
                    Text("Connect Google Account", fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cloud Retention Policy", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Slider(
                        value = localRetention.toFloat(),
                        onValueChange = { onRetentionChange(it.toInt()) },
                        valueRange = 1f..30f,
                        steps = 29,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = localRetentionText,
                        onValueChange = { input ->
                            val clean = input.filter { it.isDigit() }.take(2)
                            localRetentionText = clean
                            clean.toIntOrNull()?.let { onRetentionChange(it.coerceIn(1, 99)) }
                        },
                        label = { Text("Days") },
                        modifier = Modifier.width(85.dp),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    )
                }
                Text(
                    text = "Automatically delete Google Drive footage after $localRetention days",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun HistorySection(
    sessions: List<com.pbcam.app.data.db.RecordingSession>,
    pipelineProgress: Map<Long, com.pbcam.app.ui.viewmodel.PipelineProgress> = emptyMap(),
    currentFilter: com.pbcam.app.data.CameraSource?,
    onFilterChange: (com.pbcam.app.data.CameraSource?) -> Unit,
    onExport: (String) -> Unit,
    onRetry: (Long) -> Unit,
    onPlay: (String) -> Unit,
    onDelete: (com.pbcam.app.data.db.RecordingSession) -> Unit,
    onDeleteAll: () -> Unit,
    onViewAll: () -> Unit,
    onViewRecordings: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Recent History", 
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onViewRecordings,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.PlayCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("RECORDINGS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onViewAll,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.secondary),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("HISTORY", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null, com.pbcam.app.data.CameraSource.RTSP, com.pbcam.app.data.CameraSource.INTERNAL).forEach { source ->
                    FilterChip(
                        selected = currentFilter == source,
                        onClick = { onFilterChange(source) },
                        label = { Text(source?.name ?: "ALL") }
                    )
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onDeleteAll) {
                    Icon(Icons.Default.DeleteSweep, "Delete All", tint = Color.Red.copy(alpha = 0.7f))
                }
                IconButton(onClick = { 
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                    onExport("SeenMyPickle_History_$timestamp.xlsx")
                }) {
                    Icon(Icons.Default.FileDownload, "Export")
                }
            }
        }

        val filteredSessions = if (currentFilter == null) sessions else sessions.filter { it.source == currentFilter }
        
        // LIMIT TO 10 for performance in AdminPanel
        val displaySessions = filteredSessions.take(10)

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column {
                SessionList(
                    sessions = displaySessions, 
                    pipelineProgress = pipelineProgress,
                    onRetry = onRetry,
                    onPlay = onPlay,
                    onDelete = onDelete,
                    onRevealRequest = { it() }, 
                    isAdmin = true, 
                    modifier = Modifier.weight(1f)
                )
                
                if (filteredSessions.isEmpty()) {
                     Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                         Text("No records found", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.5f))
                     }
                }
            }
        }
    }
}

@Composable
private fun WatermarkPositionChip(
    position: WatermarkPosition,
    selected: Boolean,
    onSelect: (WatermarkPosition) -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = { onSelect(position) },
        label = { 
            Text(
                text = position.name.replace("_", " "),
                style = MaterialTheme.typography.labelSmall
            ) 
        },
        modifier = modifier,
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
        } else null
    )
}

@Composable
fun AdminSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    initialExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initialExpanded) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), MaterialTheme.shapes.medium)
            .padding(16.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically, 
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(
                title, 
                style = MaterialTheme.typography.titleSmall, 
                fontWeight = FontWeight.Bold, 
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
        }
        
        if (expanded) {
            content()
        }
    }
}
