package com.pbcam.tv.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class TvReplaySession(
    val id: Long = -1L,
    val email: String = "",
    val startTime: Long = 0L,
    val duration: Long = 0L,
    val localUrl: String = "",
    val gDriveUrl: String = "",
    val status: String = "COMPLETED"
)

data class TvUiState(
    val pairedDeviceId: String = "",
    val status: String = "IDLE", // IDLE, RECORDING, PAUSED
    val rtspUrl: String = "",
    val rtspSubUrl: String = "",
    val lastRecordingUrl: String = "",
    val localReplayUrl: String = "",
    val duration: Long = 0,
    val players: List<String> = emptyList(),
    val isPaired: Boolean = false,
    val debugInfo: String = "Initializing...",
    val firebaseConnected: Boolean = false,
    val connectionError: String = "",
    val courtTag: String = "",
    val isTabletOnline: Boolean = false,
    val isSplashScreenActive: Boolean = true,
    val isSettingsOpen: Boolean = false,
    val retryCountdown: Int = 10,
    val lastReplaySessionId: Long = -1,
    val isAutoReplayActive: Boolean = false,
    val lastUpdateTimestamp: Long = 0,
    val isReplayLoading: Boolean = false,
    val showReplayCompletePrompt: Boolean = false,
    val replayPromptCountdown: Int = 20,
    val isInitialSyncPending: Boolean = true,
    val localIp: String = "",
    val syncChannel: String = "Cloud",
    val showReplayAvailableBanner: Boolean = false,
    val replayBannerCountdown: Int = 30,
    val pendingReplaySessionId: Long = -1L,
    val recentSessions: List<TvReplaySession> = emptyList(),
    val isReplayListOpen: Boolean = false,
    val activeReplaySession: TvReplaySession? = null,
    val useMainStream: Boolean = false,
    val useHdrMode: Boolean = false,
    val isHdrSupported: Boolean = false
)

class TvDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val DB_URL = "https://seemypickle-default-rtdb.asia-southeast1.firebasedatabase.app"
    private val prefs = application.getSharedPreferences("tv_prefs", Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(TvUiState())
    val uiState: StateFlow<TvUiState> = _uiState.asStateFlow()

    private var statusListener: ValueEventListener? = null
    private var currentObservedDeviceId: String? = null
    private var retryJob: Job? = null
    private var watchdogJob: Job? = null

    init {
        checkFirebaseConnection()
        checkHdrCapabilities(application)
        
        // --- STARTUP SPLASH DELAY & INITIAL SYNC WAIT ---
        viewModelScope.launch {
            delay(1500)
            var waitCount = 0
            while (_uiState.value.isPaired && _uiState.value.isInitialSyncPending && _uiState.value.firebaseConnected && waitCount < 10) {
                delay(200)
                waitCount++
            }
            _uiState.update { it.copy(isSplashScreenActive = false) }
        }

        // --- PAIRING PERSISTENCE & STREAM PREFERENCE ---
        val savedId = prefs.getString("paired_device_id", "") ?: ""
        val savedUseMainStream = prefs.getBoolean("use_main_stream", false)
        _uiState.update { it.copy(useMainStream = savedUseMainStream) }

        if (savedId != "") {
            pairDevice(savedId)
        } else {
            updateDebug("Waiting for pairing...")
        }
    }

    private fun checkHdrCapabilities(context: Context) {
        val isSupported = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
                val defaultDisplay = displayManager?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
                val hdrTypes = defaultDisplay?.hdrCapabilities?.supportedHdrTypes ?: intArrayOf()
                hdrTypes.isNotEmpty()
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("TvDashboardViewModel", "Failed to query HDR capabilities", e)
            false
        }

        val savedUseHdr = prefs.getBoolean("use_hdr_mode", false)
        _uiState.update { 
            it.copy(
                isHdrSupported = isSupported,
                useHdrMode = savedUseHdr && isSupported
            ) 
        }
    }

    private fun getDb() = FirebaseDatabase.getInstance(DB_URL)

    fun updateDebug(msg: String) {
        _uiState.update { it.copy(debugInfo = msg) }
    }

    private fun checkFirebaseConnection() {
        getDb().getReference(".info/connected").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                _uiState.update { it.copy(firebaseConnected = connected) }
            }
            override fun onCancelled(error: DatabaseError) {
                _uiState.update { it.copy(connectionError = error.message) }
            }
        })
    }

    fun pairDevice(rawDeviceId: String) {
        val clean = rawDeviceId.trim().uppercase().replace(" ", "")
        val formattedId = if (clean.length == 8 && !clean.contains("-")) {
            clean.take(4) + "-" + clean.drop(4)
        } else {
            clean
        }
        if (formattedId.isBlank()) return

        _uiState.update { it.copy(pairedDeviceId = formattedId, isPaired = true) }
        prefs?.edit()?.putString("paired_device_id", formattedId)?.apply()
        startObservingStatus(formattedId)
        startWatchdog()
        startLocalLanProber()
    }

    fun unpairDevice() {
        prefs?.edit()?.clear()?.apply()
        statusListener?.let { 
            getDb().getReference("live_status/${_uiState.value.pairedDeviceId}").removeEventListener(it)
        }
        watchdogJob?.cancel()
        lanProbeJob?.cancel()
        _uiState.update { TvUiState(isSplashScreenActive = false) }
    }

    fun toggleSettings(open: Boolean) {
        _uiState.update { it.copy(isSettingsOpen = open) }
    }

    fun toggleStreamQuality() {
        val newValue = !_uiState.value.useMainStream
        prefs.edit().putBoolean("use_main_stream", newValue).apply()
        _uiState.update { it.copy(useMainStream = newValue) }
    }

    fun toggleHdrMode() {
        val newValue = !_uiState.value.useHdrMode
        prefs.edit().putBoolean("use_hdr_mode", newValue).apply()
        _uiState.update { it.copy(useHdrMode = newValue) }
    }

    fun toggleReplayList(open: Boolean) {
        _uiState.update { it.copy(isReplayListOpen = open) }
    }

    fun playSelectedSession(session: TvReplaySession) {
        replayBannerJob?.cancel()
        _uiState.update { 
            it.copy(
                isReplayListOpen = false,
                showReplayAvailableBanner = false,
                isReplayLoading = true,
                showReplayCompletePrompt = false,
                isAutoReplayActive = true,
                activeReplaySession = session,
                localReplayUrl = session.localUrl.ifBlank { it.localReplayUrl },
                lastRecordingUrl = session.gDriveUrl.ifBlank { it.lastRecordingUrl }
            ) 
        }
        viewModelScope.launch {
            delay(2000)
            _uiState.update { it.copy(isReplayLoading = false) }
        }
    }

    private var replayCountdownJob: Job? = null

    fun onReplayEnded() {
        if (_uiState.value.isAutoReplayActive) {
            _uiState.update { it.copy(showReplayCompletePrompt = true, replayPromptCountdown = 20) }
            startReplayPromptCountdown()
        }
    }

    private fun startReplayPromptCountdown() {
        replayCountdownJob?.cancel()
        replayCountdownJob = viewModelScope.launch {
            while (_uiState.value.replayPromptCountdown > 0 && _uiState.value.showReplayCompletePrompt) {
                delay(1000)
                _uiState.update { it.copy(replayPromptCountdown = it.replayPromptCountdown - 1) }
            }
            if (_uiState.value.showReplayCompletePrompt) {
                dismissReplay()
            }
        }
    }

    fun restartReplay() {
        replayCountdownJob?.cancel()
        _uiState.update { 
            it.copy(
                showReplayCompletePrompt = false, 
                isReplayLoading = true,
                isAutoReplayActive = true 
            ) 
        }
        viewModelScope.launch {
            delay(2000) // Replay loading animation duration
            _uiState.update { it.copy(isReplayLoading = false) }
        }
    }

    fun dismissReplay() {
        replayCountdownJob?.cancel()
        _uiState.update { it.copy(isAutoReplayActive = false, showReplayCompletePrompt = false) }
    }

    private var replayBannerJob: Job? = null

    fun acceptReplay() {
        replayBannerJob?.cancel()
        _uiState.update { 
            it.copy(
                showReplayAvailableBanner = false,
                isReplayLoading = true,
                showReplayCompletePrompt = false,
                isAutoReplayActive = true
            ) 
        }
        viewModelScope.launch {
            delay(2000) // Replay loading transition duration
            _uiState.update { it.copy(isReplayLoading = false) }
        }
    }

    fun dismissReplayBanner() {
        replayBannerJob?.cancel()
        _uiState.update { it.copy(showReplayAvailableBanner = false) }
    }

    private fun startReplayBannerCountdown() {
        replayBannerJob?.cancel()
        replayBannerJob = viewModelScope.launch {
            while (_uiState.value.replayBannerCountdown > 0 && _uiState.value.showReplayAvailableBanner) {
                delay(1000)
                _uiState.update { it.copy(replayBannerCountdown = it.replayBannerCountdown - 1) }
            }
            if (_uiState.value.showReplayAvailableBanner) {
                dismissReplayBanner()
            }
        }
    }

    fun triggerRetry() {
        retryJob?.cancel()
        _uiState.update { it.copy(retryCountdown = 10) }
        startObservingStatus(_uiState.value.pairedDeviceId)
    }

    private fun startObservingStatus(deviceId: String) {
        val cleanId = deviceId.trim().uppercase()
        if (cleanId.isBlank()) return
        
        statusListener?.let { listener ->
            currentObservedDeviceId?.let { oldId ->
                getDb().getReference("live_status/$oldId").removeEventListener(listener)
            }
        }
        currentObservedDeviceId = cleanId

        statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val localReceiveTime = System.currentTimeMillis()
                if (snapshot.exists()) {
                    val status = snapshot.child("status").getValue(String::class.java) ?: "IDLE"
                    val rtspUrl = snapshot.child("rtspUrl").getValue(String::class.java) ?: ""
                    val rtspSubUrl = snapshot.child("rtspSubUrl").getValue(String::class.java) ?: ""
                    val lastRecordingUrl = snapshot.child("lastRecordingUrl").getValue(String::class.java) ?: ""
                    val localReplayUrl = snapshot.child("localReplayUrl").getValue(String::class.java) ?: ""
                    val duration = snapshot.child("duration").getValue(Long::class.java) ?: 0L
                    val players = snapshot.child("players").children.mapNotNull { it.getValue(String::class.java) }
                    val courtTag = snapshot.child("courtTag").getValue(String::class.java) ?: ""
                    val localIp = snapshot.child("localIp").getValue(String::class.java) ?: ""
                    
                    val rawIsOnline = snapshot.child("isOnline").getValue(Boolean::class.java)
                    val hasDataNode = snapshot.hasChild("status") || snapshot.hasChild("rtspUrl") || snapshot.hasChild("courtTag")
                    val isOnline = rawIsOnline ?: hasDataNode

                    val remoteReplayId: Long = snapshot.child("lastReplaySessionId").getValue(Long::class.java) ?: -1L
                    val lastSyncTime = SimpleDateFormat("HH:mm:ss", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("Asia/Manila")
                    }.format(Date(localReceiveTime))

                    val isFirstSync = _uiState.value.lastReplaySessionId < 0L || _uiState.value.isInitialSyncPending
                    val shouldNotifyReplay = !isFirstSync && remoteReplayId > 0L && remoteReplayId != _uiState.value.lastReplaySessionId && status == "IDLE"

                    if (shouldNotifyReplay) {
                        _uiState.update { 
                            it.copy(
                                showReplayAvailableBanner = true,
                                replayBannerCountdown = 30,
                                pendingReplaySessionId = remoteReplayId
                            ) 
                        }
                        startReplayBannerCountdown()
                    }

                    updateDebug("Update received at $lastSyncTime")

                    val recentSessions = snapshot.child("recent_sessions").children.mapNotNull { s ->
                        val id = s.child("id").getValue(Long::class.java) ?: -1L
                        val email = s.child("email").getValue(String::class.java) ?: ""
                        val startTime = s.child("startTime").getValue(Long::class.java) ?: 0L
                        val dur = s.child("duration").getValue(Long::class.java) ?: 0L
                        val lUrl = s.child("localUrl").getValue(String::class.java) ?: ""
                        val gUrl = s.child("gDriveUrl").getValue(String::class.java) ?: ""
                        val sessionStatus = s.child("status").getValue(String::class.java) ?: "COMPLETED"
                        
                        if (id > 0L) {
                            TvReplaySession(
                                id = id,
                                email = email,
                                startTime = startTime,
                                duration = dur,
                                localUrl = lUrl,
                                gDriveUrl = gUrl,
                                status = sessionStatus
                            )
                        } else null
                    }

                    _uiState.update {
                        it.copy(
                            status = status,
                            rtspUrl = rtspUrl,
                            rtspSubUrl = rtspSubUrl,
                            lastRecordingUrl = lastRecordingUrl,
                            localReplayUrl = localReplayUrl,
                            duration = duration,
                            players = players,
                            courtTag = courtTag,
                            localIp = localIp,
                            isTabletOnline = isOnline,
                            lastReplaySessionId = remoteReplayId,
                            recentSessions = if (recentSessions.isNotEmpty()) recentSessions else it.recentSessions,
                            isAutoReplayActive = if (status == "RECORDING") false else it.isAutoReplayActive,
                            showReplayCompletePrompt = if (status == "RECORDING") false else it.showReplayCompletePrompt,
                            lastUpdateTimestamp = localReceiveTime,
                            isInitialSyncPending = false,
                            syncChannel = "Cloud",
                            debugInfo = "Sync OK: $lastSyncTime"
                        )
                    }

                    if (isOnline) {
                        retryJob?.cancel()
                        _uiState.update { it.copy(retryCountdown = 10) }
                    }
                } else {
                    updateDebug("Node Missing: $cleanId (Check Pairing ID)")
                    _uiState.update { it.copy(isTabletOnline = false, isInitialSyncPending = false) }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                updateDebug("Cancelled: ${error.message}")
                _uiState.update { it.copy(isInitialSyncPending = false) }
            }
        }

        getDb().getReference("live_status/$cleanId").addValueEventListener(statusListener!!)
    }

    private var lanProbeJob: Job? = null

    private fun startLocalLanProber() {
        lanProbeJob?.cancel()
        lanProbeJob = viewModelScope.launch {
            while (true) {
                delay(3000)
                val targetIp = _uiState.value.localIp
                if (targetIp.isNotBlank()) {
                    val status = com.pbcam.tv.network.TvNetworkManager.probeLocalTablet(targetIp)
                    if (status != null && status.isOnline) {
                        val receiveTime = System.currentTimeMillis()
                        val lastSyncTime = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(receiveTime))
                        _uiState.update {
                            it.copy(
                                status = status.status,
                                rtspUrl = status.rtspUrl.ifBlank { it.rtspUrl },
                                rtspSubUrl = status.rtspSubUrl.ifBlank { it.rtspSubUrl },
                                courtTag = status.courtTag.ifBlank { it.courtTag },
                                isTabletOnline = true,
                                lastUpdateTimestamp = receiveTime,
                                syncChannel = "Local LAN ($targetIp)",
                                debugInfo = "LAN Sync OK: $lastSyncTime"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = viewModelScope.launch {
            while (true) {
                delay(5000) // Check every 5 seconds
                val lastUpdate = _uiState.value.lastUpdateTimestamp
                if (lastUpdate > 0L) {
                    val staleTime = System.currentTimeMillis() - lastUpdate
                    if (staleTime > 25000) { // 25 second stable threshold
                        if (_uiState.value.isTabletOnline) {
                            android.util.Log.w("TvWatchdog", "Tablet heartbeat stale ($staleTime ms). Forcing offline.")
                            _uiState.update { it.copy(isTabletOnline = false) }
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        statusListener?.let {
            getDb().getReference("live_status/${_uiState.value.pairedDeviceId}").removeEventListener(it)
        }
        retryJob?.cancel()
        watchdogJob?.cancel()
        lanProbeJob?.cancel()
    }
}
