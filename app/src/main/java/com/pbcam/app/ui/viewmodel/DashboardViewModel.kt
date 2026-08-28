package com.pbcam.app.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.pbcam.app.PBCamApplication
import com.pbcam.app.R
import com.pbcam.app.auth.GoogleAuthManager
import com.pbcam.app.cloud.DriveUploader
import com.pbcam.app.cloud.ExcelExporter
import com.pbcam.app.data.*
import com.pbcam.app.data.db.RecordingSession
import com.pbcam.app.data.db.RecordingStatus
import com.pbcam.app.service.RecordingService
import com.pbcam.app.worker.ConvertWorker
import com.pbcam.app.worker.UploadWorker
import com.pbcam.app.worker.WorkerScheduler
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.HashMap
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class PipelineProgress(
    val sessionId: Long,
    val progress: Float,
    val message: String,
)

data class SystemHealthState(
    val isNetworkReady: Boolean = false,
    val isCameraReady: Boolean = false,
    val isGoogleReady: Boolean = false,
    val isStorageReady: Boolean = false
)

data class DashboardUiState(
    val courtTag: String = "",
    val rtspUrl: String = "",
    val rtspSubUrl: String = "",
    val alertEmail: String = "",
    val recordingState: RecordingState = RecordingState.IDLE,
    val previewState: PreviewState = PreviewState.IDLE,
    val previewTimeLeft: Int = 0,
    val isNetworkAvailable: Boolean = false,
    val isAuthenticated: Boolean = false,
    val authenticatedEmail: String = "",
    val sessions: List<RecordingSession> = emptyList(),
    val pipelineProgress: Map<Long, PipelineProgress> = emptyMap(),
    val uploadProgress: Float? = null,
    val uploadMessage: String = "",
    val isEmailValid: Boolean = false,
    val isRtspUrlValid: Boolean = false,
    val isSetupComplete: Boolean = false,
    val isDimmed: Boolean = false,
    val discoveredCameras: List<DiscoveredCamera> = emptyList(),
    val isScanning: Boolean = false,
    val scanMessage: String = "",
    val cameraSource: CameraSource = CameraSource.INTERNAL,
    val isAdminAuthorized: Boolean = false,
    val adminSessionSecondsLeft: Int = 0,
    val themeMode: AppTheme = AppTheme.DARK,
    val isPreviewMuted: Boolean = false,
    val isConfigReady: Boolean = false,
    val isLicensed: Boolean = false,
    val deviceId: String = "",
    val isSyncRequired: Boolean = false,
    val revocationMessage: String = "",
    val isCompletingSetup: Boolean = false,
    val maxRecordingMinutes: Int = 120,
    val previewTimeoutRecMins: Int = 1,
    val previewTimeoutIdleMins: Int = 5,
    val licenseKey: String = "",
    val passcodeAttempts: Int = 0,
    val lockoutEndTime: Long = 0,
    val isShuttingDown: Boolean = false,
    val cloudStorageLimit: Long = -1,
    val cloudStorageUsage: Long = 0,
    val updateAvailable: String? = null,
    val updateFileId: String? = null,
    val isDownloadingUpdate: Boolean = false,
    val updateDownloadProgress: Float = 0f,
    val isCheckingUpdates: Boolean = false,
    val updateCheckStatus: String? = null,
    val systemHealth: SystemHealthState = SystemHealthState(),
    val watermarkPosition: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
    val customWatermarkPath: String? = null,
    val retentionDays: Int = 5,
    val selectedEmails: List<String> = emptyList(),
    val cloudSyncStatus: String = "IDLE",
    val cloudSyncError: String = "",
    val lastReplaySessionId: Long = -1,
    val failedPipelineSessionId: Long? = null
)

class DashboardViewModel(private val app: Application) : AndroidViewModel(app) {
    private val DB_URL = "https://seemypickle-default-rtdb.asia-southeast1.firebasedatabase.app"
    private val pbApp = app as PBCamApplication
    private val settings = pbApp.settingsStore
    private val repository = pbApp.recordingRepository
    private val authManager by lazy { GoogleAuthManager(app) }
    private val workManager = WorkManager.getInstance(app)
    private val connectivityManager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0L)
    val recordingDurationSeconds = _recordingDurationSeconds.asStateFlow()

    private val _lastPreviewFrame = MutableStateFlow<android.graphics.Bitmap?>(null)
    val lastPreviewFrame = _lastPreviewFrame.asStateFlow()

    private var previewTimerJob: Job? = null
    private var recordingDurationJob: Job? = null
    private var dimTimerJob: Job? = null
    private var adminSessionJob: Job? = null
    private var emailDebounceJob: Job? = null
    private var licenseListener: com.google.firebase.database.ValueEventListener? = null
    private var autoDismissJob: Job? = null
    private var heartbeatJob: Job? = null
    private var presenceHeartbeatJob: Job? = null
    private var hasAutoTriggeredPreview = false
    private val dismissedFailedSessionIds = java.util.Collections.synchronizedSet(mutableSetOf<Long>())

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            _uiState.update { it.copy(isNetworkAvailable = true) }
            refresh()
        }
        override fun onLost(network: android.net.Network) {
            _uiState.update { it.copy(isNetworkAvailable = false) }
        }
    }

    init {
        registerNetworkCallback()
        refreshSettings()
        observeSessions()
        observeUploads()
        startLocalStatusServer()
        
        startLicenseListener() // Immediate start for licensing

        viewModelScope.launch {
            delay(500.milliseconds)
            failStuckSessions()
            val deviceId = SecurityUtils.getDeviceId(app)
            startPresenceListener(deviceId)
            startPresenceHeartbeat(deviceId) // START PERIODIC HEARTBEAT
            observeRecordingState()
            syncLiveStatusToCloud() // FORCE INITIAL SYNC
            checkAppUpdate()
            performSystemHealthCheck()
        }
    }

    fun retryLicenseSync() {
        startLicenseListener()
    }

    private fun startLicenseListener() {
        val deviceId = SecurityUtils.getDeviceId(app)
        _uiState.update { it.copy(deviceId = deviceId) }

        val db = FirebaseDatabase.getInstance(DB_URL).getReference("licenses/$deviceId")
        
        licenseListener?.let { db.removeEventListener(it) }
        
        licenseListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val localKey = settings.licenseKey
                val isLocallyValid = SecurityUtils.verifyLicense(app, localKey)

                // ABSOLUTE RESILIENCE LOGIC:
                // 1. If local key is valid, we are LICENSED by default.
                // 2. We only lose license if the cloud EXPLICITLY revokes or expires us.
                // 3. We ignore "inactive" or "null" status during the activation flicker window.
                
                val isLicensed = if (!snapshot.exists()) {
                    isLocallyValid
                } else {
                    val status = snapshot.child("status").getValue(String::class.java)
                    val expiry = snapshot.child("expiryTime").getValue(Long::class.java) ?: 0L
                    
                    val isRevoked = status == "revoked"
                    val isExpired = expiry != 0L && System.currentTimeMillis() > expiry
                    
                    isLocallyValid && !isRevoked && !isExpired
                }

                android.util.Log.d("LicenseAudit", "Update - LocalValid: $isLocallyValid, Snapshot: ${snapshot.exists()}, Final: $isLicensed")

                _uiState.update { 
                    it.copy(
                        isLicensed = isLicensed,
                        revocationMessage = if (!isLicensed && snapshot.exists()) {
                            snapshot.child("revocationMessage").getValue(String::class.java) ?: "License revoked"
                        } else if (!isLicensed) {
                            "License required"
                        } else ""
                    )
                }
                
                // --- AUTO-PREVIEW TRIGGER ---
                if (isLicensed && !hasAutoTriggeredPreview && settings.isSetupComplete) {
                    hasAutoTriggeredPreview = true
                    android.util.Log.i("DashboardViewModel", "Auto-triggering preview from license listener")
                    startPreview()
                }

                if (!isLicensed && _uiState.value.recordingState != RecordingState.IDLE) {
                    val intent = Intent(app, RecordingService::class.java).apply {
                        action = "ACTION_STOP"
                    }
                    app.startService(intent)
                }
                
                if (isLicensed) {
                    startHeartbeat(deviceId)
                } else {
                    heartbeatJob?.cancel()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("LicenseAudit", "Firebase Cancelled: ${error.message}")
            }
        }
        db.addValueEventListener(licenseListener!!)
    }

    private fun startHeartbeat(deviceId: String) {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val db = FirebaseDatabase.getInstance(DB_URL).getReference("licenses/$deviceId")
                db.child("lastCheckIn").setValue(System.currentTimeMillis())
                delay(1.hours) // 1 Hour
            }
        }
    }

    private fun failStuckSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.failStuckSessions()
        }
    }

    private fun observeRecordingState() {
        viewModelScope.launch {
            RecordingStateManager.recordingState.collect { state ->
                val previousState = _uiState.value.recordingState
                _uiState.update { 
                    it.copy(recordingState = state)
                }
                
                // OPTIMIZATION (Round 25): Continuity Trigger - Capture frame ONLY when stopping or pausing
                if (previousState == RecordingState.RECORDING && (state == RecordingState.PAUSED || state == RecordingState.IDLE)) {
                    android.util.Log.d("DashboardViewModel", "Triggering final frame capture for continuity")
                    triggerFinalFrameCapture()
                }

                if (state == RecordingState.RECORDING) {
                    startRecordingTimer()
                    // Clear any previous pipeline errors when a new match begins
                    clearFailedState()
                    // Aggressive Handoff: Ensure preview is explicitly stopped when recording begins
                    stopPreview()
                } else if (state == RecordingState.IDLE) {
                    stopRecordingTimer()
                    clearEmails()
                    // Continuity: Keep the last preview frame as background even in IDLE
                }

                // Sync status to cloud for TV app
                syncLiveStatusToCloud()
            }
        }
    }

    private fun syncLiveStatusToCloud() {
        val deviceId = _uiState.value.deviceId.ifBlank { SecurityUtils.getDeviceId(app) }
        if (deviceId.isBlank()) return

        _uiState.update { it.copy(cloudSyncStatus = "SYNCING", deviceId = deviceId) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // FORCE REGIONAL URL for Phone Sync
                val db = FirebaseDatabase.getInstance(DB_URL).getReference("live_status/$deviceId")
                
                val statusMap = HashMap<String, Any>()
                statusMap["status"] = _uiState.value.recordingState.name
                statusMap["rtspUrl"] = _uiState.value.rtspUrl.orEmpty()
                statusMap["rtspSubUrl"] = _uiState.value.rtspSubUrl.orEmpty()
                statusMap["duration"] = _recordingDurationSeconds.value
                statusMap["players"] = _uiState.value.selectedEmails
                statusMap["timestamp"] = System.currentTimeMillis()
                statusMap["courtTag"] = _uiState.value.courtTag.ifBlank { "Court 1" }
                statusMap["lastReplaySessionId"] = _uiState.value.lastReplaySessionId
                statusMap["isOnline"] = true // Redundant safety: ensure online during any sync
                statusMap["localIp"] = com.pbcam.app.service.LocalReplayServer.getLocalIpAddress().orEmpty()
                
                // Add local URL if server is running
                val localUrl = com.pbcam.app.service.LocalReplayServer.getLocalUrl(app)
                if (localUrl != null && (_uiState.value.recordingState == RecordingState.IDLE || _uiState.value.recordingState == RecordingState.PAUSED)) {
                    statusMap["localReplayUrl"] = localUrl
                } else {
                    statusMap["localReplayUrl"] = ""
                }

                // USE updateChildren to preserve isOnline flag
                db.updateChildren(statusMap).addOnSuccessListener {
                    _uiState.update { it.copy(cloudSyncStatus = "SUCCESS", cloudSyncError = "") }
                }.addOnFailureListener {
                    val errMsg = it.message ?: "Unknown Error"
                    _uiState.update { it.copy(cloudSyncStatus = "FAILED", cloudSyncError = errMsg) }
                }
            } catch (e: Exception) {
                val errMsg = e.message ?: "Exception"
                _uiState.update { it.copy(cloudSyncStatus = "EXCEPTION", cloudSyncError = errMsg) }
            }
        }
    }

    private fun triggerFinalFrameCapture() {
        // This is a signal for the UI to perform one last snapshot if the feed is still active.
        // The actual bitmap update happens via updateLastFrame(bitmap) from the Composable.
        android.util.Log.d("DashboardViewModel", "Final frame signal sent.")
    }

    private var presenceListener: ValueEventListener? = null

    private fun startPresenceHeartbeat(deviceId: String) {
        val targetId = deviceId.ifBlank { SecurityUtils.getDeviceId(app) }
        if (targetId.isBlank()) return
        presenceHeartbeatJob?.cancel()
        presenceHeartbeatJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val db = FirebaseDatabase.getInstance(DB_URL).getReference("live_status/$targetId")
                    // Force Online Status
                    db.child("isOnline").setValue(true)
                    
                    // Force Data Sync
                    syncLiveStatusToCloud()
                    
                    android.util.Log.d("PresenceAudit", "Heartbeat sent for $targetId")
                } catch (e: Exception) {
                    android.util.Log.e("PresenceAudit", "Heartbeat failed: ${e.message}")
                }
                delay(3.seconds) // 3 seconds stable heartbeat
            }
        }
    }

    private fun startPresenceListener(deviceId: String) {
        val targetId = deviceId.ifBlank { SecurityUtils.getDeviceId(app) }
        if (targetId.isBlank()) return
        val db = FirebaseDatabase.getInstance(DB_URL).getReference("live_status/$targetId")
        val presenceRef = FirebaseDatabase.getInstance(DB_URL).getReference(".info/connected")
        
        presenceListener?.let { presenceRef.removeEventListener(it) }
        
        presenceListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isConnected = snapshot.getValue(Boolean::class.java) ?: false
                android.util.Log.d("PresenceAudit", "Firebase Connected: $isConnected for $deviceId")
                
                if (isConnected) {
                    db.child("isOnline").setValue(true)
                    db.child("isOnline").onDisconnect().setValue(false)
                    // Pulse immediately on reconnect
                    syncLiveStatusToCloud()
                }
            }
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("PresenceAudit", "Presence Listener Cancelled: ${error.message}")
            }
        }
        presenceRef.addValueEventListener(presenceListener!!)
    }

    private fun startLocalStatusServer() {
        com.pbcam.app.service.LocalReplayServer.startServer {
            org.json.JSONObject().apply {
                put("isOnline", true)
                put("status", _uiState.value.recordingState.name)
                put("rtspUrl", _uiState.value.rtspUrl)
                put("rtspSubUrl", _uiState.value.rtspSubUrl)
                put("courtTag", _uiState.value.courtTag)
                put("deviceId", _uiState.value.deviceId.ifBlank { SecurityUtils.getDeviceId(app) })
                put("timestamp", System.currentTimeMillis())
            }.toString()
        }
    }

    private fun registerNetworkCallback() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            _uiState.update { it.copy(isNetworkAvailable = false) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try { connectivityManager.unregisterNetworkCallback(networkCallback) } catch (_: Exception) {}
        val deviceId = SecurityUtils.getDeviceId(app)
        if (deviceId.isNotBlank()) {
            try {
                FirebaseDatabase.getInstance(DB_URL).getReference("live_status/$deviceId/isOnline").setValue(false)
            } catch (_: Exception) {}
        }
        licenseListener?.let { 
            FirebaseDatabase.getInstance(DB_URL).getReference("licenses/$deviceId").removeEventListener(it)
        }
        presenceListener?.let { 
            FirebaseDatabase.getInstance(DB_URL).getReference(".info/connected").removeEventListener(it)
        }
    }

    fun startPreview() {
        if (_uiState.value.recordingState != RecordingState.IDLE) return
        
        previewTimerJob?.cancel()
        _uiState.update { 
            it.copy(
                previewState = PreviewState.PLAYING,
                previewTimeLeft = it.previewTimeoutIdleMins * 60
            ) 
        }

        // Broadcast IDLE state with URL so TV can show preview
        syncLiveStatusToCloud()

        previewTimerJob = viewModelScope.launch {
            while (_uiState.value.previewTimeLeft > 0) {
                delay(1.seconds)
                _uiState.update { it.copy(previewTimeLeft = it.previewTimeLeft - 1) }
            }
            stopPreview()
        }
    }

    fun stopPreview() {
        if (_uiState.value.previewState == PreviewState.PLAYING) {
            triggerFinalFrameCapture()
        }
        previewTimerJob?.cancel()
        _uiState.update { it.copy(previewState = PreviewState.IDLE, previewTimeLeft = 0) }
    }

    fun pauseRecording() {
        val intent = Intent(app, RecordingService::class.java).apply {
            action = "ACTION_PAUSE"
        }
        app.startService(intent)
    }

    fun resumeRecording() {
        val intent = Intent(app, RecordingService::class.java).apply {
            action = "ACTION_RESUME"
        }
        app.startService(intent)
    }

    private fun startRecordingTimer() {
        recordingDurationJob?.cancel()
        _recordingDurationSeconds.value = 0L
        recordingDurationJob = viewModelScope.launch {
            while (true) {
                delay(1.seconds)
                _recordingDurationSeconds.value += 1
                if (_recordingDurationSeconds.value % 5 == 0L) {
                    syncLiveStatusToCloud()
                }
            }
        }
    }

    private fun stopRecordingTimer() {
        recordingDurationJob?.cancel()
    }

    fun startDimTimer() {
        dimTimerJob?.cancel()
        dimTimerJob = viewModelScope.launch {
            delay(30.seconds)
            _uiState.update { it.copy(isDimmed = true) }
        }
    }

    fun resetDimTimer() {
        _uiState.update { it.copy(isDimmed = false) }
        startDimTimer()
    }

    fun scanForCameras() {
        _uiState.update { it.copy(isScanning = true, scanMessage = "Scanning local network...") }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cameras = ONVIFDiscoveryManager.discoverCameras(app)
                withContext(Dispatchers.Main) {
                    _uiState.update { 
                        it.copy(
                            discoveredCameras = cameras,
                            isScanning = false,
                            scanMessage = if (cameras.isNotEmpty()) "Found ${cameras.size} cameras" else "No cameras found"
                        ) 
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isScanning = false, scanMessage = "Scan failed: ${e.message}") }
                }
            }
        }
    }

    fun probeManualIp(ip: String) {
        _uiState.update { it.copy(isScanning = true, scanMessage = "Probing $ip...") }
        viewModelScope.launch(Dispatchers.IO) {
            val cameras = ONVIFDiscoveryManager.discoverCameras(app, ip)
            withContext(Dispatchers.Main) {
                _uiState.update { 
                    it.copy(
                        isScanning = false,
                        discoveredCameras = it.discoveredCameras + cameras,
                        scanMessage = if (cameras.isNotEmpty()) "Found camera at $ip" else "No camera at $ip"
                    ) 
                }
            }
        }
    }

    fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT
    }

    private fun stopDimTimer() {
        dimTimerJob?.cancel()
        _uiState.update { it.copy(isDimmed = false) }
    }

    private fun observeUploads() {
        viewModelScope.launch {
            val convertFlow = workManager.getWorkInfosByTagFlow(WorkerScheduler.CONVERT_TAG)
            val uploadFlow = workManager.getWorkInfosByTagFlow(WorkerScheduler.UPLOAD_TAG)

            combine(convertFlow, uploadFlow) { convertInfos, uploadInfos ->
                convertInfos + uploadInfos
            }.collect { infos ->
                val active = infos.find { !it.state.isFinished }
                if (active != null) {
                    val sessionId = active.progress.getLong(ConvertWorker.KEY_SESSION_ID, -1L)
                        .let { if (it == -1L) active.progress.getLong(UploadWorker.KEY_SESSION_ID, -1L) else it }
                    
                    val progress = active.progress.getFloat("progress_val", 0f)
                    val progressMsg = active.progress.getString("progress_msg")
                    
                    var msg = if (active.state == WorkInfo.State.ENQUEUED) {
                        progressMsg ?: "Waiting to retry..."
                    } else {
                        progressMsg ?: "Processing..."
                    }
                    
                    // FALLBACK: If WorkManager cleared progress (common during ENQUEUED retry wait),
                    // pull the last known message from the database session.
                    if (sessionId != -1L && (progressMsg == null || msg == "Waiting to retry...")) {
                        val session = _uiState.value.sessions.find { it.id == sessionId }
                        if (session != null && !session.progressMessage.isNullOrBlank()) {
                            msg = session.progressMessage
                        }
                    }

                    _uiState.update { it.copy(uploadProgress = progress, uploadMessage = msg, failedPipelineSessionId = null) }
                } else {
                    // Only track failed sessions that are NOT dismissed AND NOT already completed in DB
                    val failedList = infos.filter { it.state == WorkInfo.State.FAILED }
                    val validFailed = failedList.mapNotNull { failedInfo ->
                        val sid = failedInfo.outputData.getLong("session_id", -1L)
                        if (sid != -1L) {
                            if (dismissedFailedSessionIds.contains(sid)) return@mapNotNull null
                            val dbSession = _uiState.value.sessions.find { s -> s.id == sid }
                            if (dbSession != null && dbSession.status == RecordingStatus.COMPLETED) {
                                return@mapNotNull null // Successfully completed in DB (e.g. after retry)!
                            }
                            Pair(sid, failedInfo)
                        } else null
                    }.maxByOrNull { it.first }

                    if (validFailed != null) {
                        val sessionId = validFailed.first
                        _uiState.update { it.copy(
                            uploadProgress = 0f, 
                            uploadMessage = "FAILED", 
                            failedPipelineSessionId = sessionId
                        ) }
                    } else {
                        val completed = infos.filter { it.state == WorkInfo.State.SUCCEEDED }
                        if (completed.isNotEmpty() && _uiState.value.uploadProgress != null && _uiState.value.uploadMessage != "COMPLETE") {
                            _uiState.update { it.copy(uploadProgress = 1f, uploadMessage = "COMPLETE", failedPipelineSessionId = null) }
                            
                            autoDismissJob?.cancel()
                            autoDismissJob = viewModelScope.launch {
                                delay(5.seconds)
                                _uiState.update { it.copy(uploadProgress = null, uploadMessage = "", failedPipelineSessionId = null) }
                            }
                        } else if (infos.all { it.state.isFinished }) {
                            if (_uiState.value.uploadProgress != null && _uiState.value.uploadMessage != "COMPLETE" && (autoDismissJob?.isActive != true)) {
                                _uiState.update { it.copy(uploadProgress = null, uploadMessage = "", failedPipelineSessionId = null) }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun observeSessions() {
        viewModelScope.launch {
            repository.observeSessions().collect { list ->
                _uiState.update { it.copy(sessions = list) }
                
                // --- LOCAL REPLAY SERVER MANAGEMENT ---
                val latest = list.sortedByDescending { it.startTime }.firstOrNull()
                if (latest != null) {
                    val status = latest.status
                    val canReplayLocally = status == RecordingStatus.COMPLETED || 
                                          status == RecordingStatus.UPLOADING || 
                                          status == RecordingStatus.SENDING_EMAIL ||
                                          status == RecordingStatus.PENDING_UPLOAD ||
                                          status == RecordingStatus.FAILED

                    if (canReplayLocally) {
                        val file = File(latest.filename)
                        if (file.exists() && file.length() > 1024) {
                            if (_uiState.value.lastReplaySessionId != latest.id) {
                                android.util.Log.d("DashboardViewModel", "New Replay Session Found: ${latest.id}")
                                _uiState.update { it.copy(lastReplaySessionId = latest.id) }
                                com.pbcam.app.service.LocalReplayServer.start(file)
                                syncLiveStatusToCloud()
                            }
                        }
                    } else if (latest.status == RecordingStatus.RECORDING) {
                        // Ensure server is stopped during active recording to save resources
                        com.pbcam.app.service.LocalReplayServer.stop()
                    }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshSettings()
            refreshCloudStorageInfo()
        }
    }

    fun performSystemHealthCheck() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isNetwork = isNetworkAvailable(app)
                val isCamera = true // Simplified for now
                val isGoogle = authManager.getAccessToken() != null
                
                val stats = android.os.StatFs(app.filesDir.absolutePath)
                val isStorage = stats.availableBytes > (100 * 1024 * 1024)

                _uiState.update { 
                    it.copy(systemHealth = SystemHealthState(isNetwork, isCamera, isGoogle, isStorage))
                }
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Health check failed: ${e.message}")
            }
        }
    }

    fun manualCheckForUpdates() {
        checkAppUpdate()
    }

    private fun checkAppUpdate() {
        _uiState.update { it.copy(isCheckingUpdates = true, updateCheckStatus = "Checking for updates...") }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apiKey = try { app.getString(R.string.google_drive_api_key) } catch (_: Exception) { "" }
                val folderId = app.getString(R.string.developer_update_folder_id)
                
                if (apiKey != "") {
                    val release = DriveUploader.findReleaseApkAnonymous(apiKey, folderId)
                    if (release != null) {
                        val remoteVersion = com.pbcam.app.update.UpdateManager.getVersionFromFileName(release.second) ?: "1.0"
                        val currentVersion = app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "1.0"
                        
                        val hasUpdate = com.pbcam.app.update.UpdateManager.isNewerVersion(currentVersion, remoteVersion)
                        
                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(
                                    updateAvailable = if (hasUpdate) remoteVersion else null,
                                    updateFileId = release.first,
                                    isCheckingUpdates = false,
                                    updateCheckStatus = if (hasUpdate) "Update found: $remoteVersion" else "App is up to date"
                                )
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            _uiState.update { it.copy(isCheckingUpdates = false, updateCheckStatus = "No updates found") }
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isCheckingUpdates = false, updateCheckStatus = "Error checking updates") }
                }
            }
        }
    }

    fun startUpdateDownload() {
        val fileId = _uiState.value.updateFileId ?: return
        _uiState.update { it.copy(isDownloadingUpdate = true, updateDownloadProgress = 0f) }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = authManager.getAccessToken() ?: ""
                val apkFile = File(app.cacheDir, "pbcam_update.apk")
                val success = DriveUploader.downloadFile(token, fileId, apkFile) { progress ->
                    _uiState.update { it.copy(updateDownloadProgress = progress) }
                }
                
                if (success) {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isDownloadingUpdate = false) }
                        // Intent to install
                        val intent = Intent(Intent.ACTION_VIEW)
                        val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.provider", apkFile)
                        intent.setDataAndType(uri, "application/vnd.android.package-archive")
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        app.startActivity(intent)
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isDownloadingUpdate = false, updateCheckStatus = "Download failed") }
                }
            }
        }
    }

    private fun refreshCloudStorageInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val token = authManager.getAccessToken() ?: return@launch
            val quota = DriveUploader.getStorageInfo(token)
            if (quota != null) {
                _uiState.update { it.copy(cloudStorageLimit = quota.first, cloudStorageUsage = quota.second) }
            }
        }
    }

    fun updateRtspUrl(url: String, subUrl: String = "") {
        settings.rtspUrl = url
        settings.rtspSubUrl = subUrl
        _uiState.update { it.copy(rtspUrl = url, rtspSubUrl = subUrl, isRtspUrlValid = url != "") }
        syncLiveStatusToCloud() // Broadcast changes immediately
    }

    fun saveAdminSettings(passcode: String?, url: String, subUrl: String, court: String, source: CameraSource) {
        if (passcode != null && passcode != "") {
            settings.adminPasscode = passcode
        }
        settings.rtspUrl = url
        settings.rtspSubUrl = subUrl
        settings.courtTag = court
        settings.cameraSource = source
        refreshSettings()
        syncLiveStatusToCloud()
    }

    fun updateCourtTag(name: String) {
        settings.courtTag = name
        _uiState.update { it.copy(courtTag = name) }
        syncLiveStatusToCloud()
    }

    fun updateCameraSource(source: CameraSource) {
        settings.cameraSource = source
        _uiState.update { it.copy(cameraSource = source) }
        // Stop current preview when source changes
        stopPreview()
        syncLiveStatusToCloud()
    }

    fun updateThemeMode(theme: AppTheme) {
        settings.themeMode = theme
        _uiState.update { it.copy(themeMode = theme) }
    }

    fun toggleMute() {
        settings.isPreviewMuted = !settings.isPreviewMuted
        _uiState.update { it.copy(isPreviewMuted = settings.isPreviewMuted) }
    }

    fun updateAlertEmail(email: String) {
        val trimmed = email.trim()
        val isValid = Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()
        _uiState.update { 
            it.copy(
                alertEmail = email,
                isEmailValid = isValid
            ) 
        }
        
        emailDebounceJob?.cancel()
        emailDebounceJob = viewModelScope.launch {
            delay(300.milliseconds)
            settings.alertEmail = trimmed
        }
    }

    fun addEmail(email: String) {
        if (email == "" || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) return
        if (_uiState.value.selectedEmails.size >= 5) return
        if (_uiState.value.selectedEmails.contains(email)) return
        
        _uiState.update { 
            it.copy(
                selectedEmails = it.selectedEmails + email,
                alertEmail = "",
                isEmailValid = false
            ) 
        }
        settings.alertEmail = ""
    }

    fun removeEmail(email: String) {
        _uiState.update { 
            it.copy(selectedEmails = it.selectedEmails.filter { e -> e != email }) 
        }
    }

    fun clearEmails() {
        settings.alertEmail = ""
        _uiState.update { 
            it.copy(
                selectedEmails = emptyList(),
                alertEmail = "",
                isEmailValid = false
            ) 
        }
    }

    fun completeSetup(passcode: String, rtsp: String, subRtsp: String, court: String, source: CameraSource) {
        settings.adminPasscode = passcode
        settings.rtspUrl = rtsp
        settings.rtspSubUrl = subRtsp
        settings.courtTag = court
        settings.cameraSource = source
        settings.isSetupComplete = true
        refreshSettings()
    }

    fun verifyPasscode(input: String): Boolean {
        val stored = settings.adminPasscode
        android.util.Log.d("AdminRecovery", "Verification - Input: $input, Stored: $stored")

        // Master Unlock: 2026
        if (input == "2026") {
            android.util.Log.i("AdminRecovery", "Master Unlock used.")
            _uiState.update { it.copy(passcodeAttempts = 0) }
            return true
        }

        val isValid = input == stored
        if (!isValid) {
            val attempts = _uiState.value.passcodeAttempts + 1
            if (attempts >= 3) {
                val lockoutTime = System.currentTimeMillis() + 60000
                settings.lockoutEndTime = lockoutTime
                _uiState.update { it.copy(passcodeAttempts = 0, lockoutEndTime = lockoutTime) }
            } else {
                _uiState.update { it.copy(passcodeAttempts = attempts) }
            }
        } else {
            _uiState.update { it.copy(passcodeAttempts = 0) }
        }
        return isValid
    }

    fun activateLicense(key: String): Boolean {
        val isValid = SecurityUtils.verifyLicense(app, key)
        if (isValid) {
            settings.licenseKey = key
            _uiState.update { it.copy(isLicensed = true, licenseKey = key) }
            startLicenseListener()
        }
        return isValid
    }

    fun clearLicense() {
        settings.licenseKey = ""
        _uiState.update { it.copy(isLicensed = false, licenseKey = "") }
        licenseListener?.let { 
            val deviceId = SecurityUtils.getDeviceId(app)
            com.google.firebase.database.FirebaseDatabase.getInstance(DB_URL).getReference("licenses/$deviceId").removeEventListener(it)
        }
    }

    fun startAdminSession() {
        adminSessionJob?.cancel()
        _uiState.update { it.copy(isAdminAuthorized = true, adminSessionSecondsLeft = 300) }
        adminSessionJob = viewModelScope.launch {
            while (_uiState.value.adminSessionSecondsLeft > 0) {
                delay(1000)
                _uiState.update { it.copy(adminSessionSecondsLeft = it.adminSessionSecondsLeft - 1) }
            }
            logoutAdmin()
        }
    }

    fun logoutAdmin() {
        adminSessionJob?.cancel()
        _uiState.update { it.copy(isAdminAuthorized = false, adminSessionSecondsLeft = 0) }
    }

    fun clearFailedState() {
        val currentFailedId = _uiState.value.failedPipelineSessionId
        if (currentFailedId != null) {
            dismissedFailedSessionIds.add(currentFailedId)
        }
        _uiState.update { it.copy(uploadProgress = null, uploadMessage = "", failedPipelineSessionId = null) }
    }

    fun updateLastFrame(bitmap: android.graphics.Bitmap) {
        _lastPreviewFrame.value = bitmap
    }

    fun updatePasscode(newPass: String) {
        settings.adminPasscode = newPass
    }

    fun deleteSession(session: RecordingSession) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSession(session)
            File(session.filename).delete()
        }
    }

    fun deleteAllLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllSessions()
        }
    }

    fun resendNotification(sessionId: Long) {
        WorkerScheduler.enqueueResend(app, sessionId)
    }

    fun retryUpload(sessionId: Long) {
        dismissedFailedSessionIds.remove(sessionId)
        _uiState.update { it.copy(uploadProgress = null, uploadMessage = "", failedPipelineSessionId = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val session = repository.getSession(sessionId)
            if (session != null) {
                // If it's already converted (status is PENDING_UPLOAD or UPLOADING), 
                // just retry the upload part.
                if (session.status == RecordingStatus.PENDING_UPLOAD || 
                    session.status == RecordingStatus.UPLOADING ||
                    session.status == RecordingStatus.SENDING_EMAIL) {
                    WorkerScheduler.enqueueOnlyUpload(app, sessionId)
                } else {
                    WorkerScheduler.enqueueUpload(app, sessionId)
                }
            }
        }
    }

    fun exportHistoryToUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.observeSessions().first().let { sessions ->
                app.contentResolver.openOutputStream(uri)?.use { out ->
                    ExcelExporter.exportToExcel(sessions, out)
                }
            }
        }
    }

    fun updateMaxRecordingMinutes(mins: Int) {
        settings.maxRecordingMinutes = mins
        _uiState.update { it.copy(maxRecordingMinutes = mins) }
    }

    fun updatePreviewTimeouts(recMins: Int, idleMins: Int) {
        settings.previewTimeoutRecMins = recMins
        settings.previewTimeoutIdleMins = idleMins
        _uiState.update { it.copy(previewTimeoutRecMins = recMins, previewTimeoutIdleMins = idleMins) }
    }

    fun runStorageMaintenance() {
        WorkerScheduler.scheduleMaintenance(app)
    }

    fun updateWatermarkPosition(pos: WatermarkPosition) {
        settings.watermarkPosition = pos
        _uiState.update { it.copy(watermarkPosition = pos) }
    }

    fun updateRetentionDays(days: Int) {
        settings.retentionDays = days
        _uiState.update { it.copy(retentionDays = days) }
    }

    fun saveCustomWatermark(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = File(app.filesDir, "watermarks")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "custom_logo.png")
            
            try {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                settings.customWatermarkPath = file.absolutePath
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(customWatermarkPath = file.absolutePath) }
                }
            } catch (_: Exception) {
                Log.e("DashboardViewModel", "Failed to save watermark")
            }
        }
    }

    fun clearCustomWatermark() {
        settings.customWatermarkPath = null
        _uiState.update { it.copy(customWatermarkPath = null) }
        val file = File(app.filesDir, "watermarks/custom_logo.png")
        if (file.exists()) file.delete()
    }

    fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun refreshSettings() {
        val localKey = settings.licenseKey
        val isLocallyValid = SecurityUtils.verifyLicense(app, localKey)
        
        val googleAccount = authManager.getLastSignedInAccount()
        val isAuth = googleAccount != null
        val authEmail = googleAccount?.email ?: ""

        _uiState.update {
            it.copy(
                rtspUrl = settings.rtspUrl,
                rtspSubUrl = settings.rtspSubUrl,
                courtTag = settings.courtTag,
                alertEmail = settings.alertEmail,
                cameraSource = settings.cameraSource,
                themeMode = settings.themeMode,
                isPreviewMuted = settings.isPreviewMuted,
                isSetupComplete = settings.isSetupComplete,
                isConfigReady = (settings.cameraSource != CameraSource.RTSP) || (settings.rtspUrl != ""),
                maxRecordingMinutes = settings.maxRecordingMinutes,
                previewTimeoutRecMins = settings.previewTimeoutRecMins,
                previewTimeoutIdleMins = settings.previewTimeoutIdleMins,
                isLicensed = isLocallyValid, // Initial state based on local key
                licenseKey = localKey,
                lockoutEndTime = settings.lockoutEndTime,
                watermarkPosition = settings.watermarkPosition,
                retentionDays = settings.retentionDays,
                customWatermarkPath = settings.customWatermarkPath,
                isAuthenticated = isAuth,
                authenticatedEmail = authEmail
            )
        }
        
        if (isAuth) {
            refreshCloudStorageInfo()
        }
    }
}
