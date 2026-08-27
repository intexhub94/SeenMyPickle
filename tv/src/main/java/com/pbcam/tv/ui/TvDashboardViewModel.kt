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
    val replayPromptCountdown: Int = 20
)

class TvDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val DB_URL = "https://seemypickle-default-rtdb.asia-southeast1.firebasedatabase.app"
    private val prefs = application.getSharedPreferences("tv_prefs", Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(TvUiState())
    val uiState: StateFlow<TvUiState> = _uiState.asStateFlow()

    private var statusListener: ValueEventListener? = null
    private var retryJob: Job? = null
    private var watchdogJob: Job? = null

    init {
        checkFirebaseConnection()
        
        // --- STARTUP SPLASH DELAY ---
        viewModelScope.launch {
            delay(2000)
            _uiState.update { it.copy(isSplashScreenActive = false) }
        }

        // --- PAIRING PERSISTENCE ---
        val savedId = prefs.getString("paired_device_id", "") ?: ""
        if (savedId != "") {
            pairDevice(savedId)
        } else {
            updateDebug("Waiting for pairing...")
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

    fun pairDevice(deviceId: String) {
        _uiState.update { it.copy(pairedDeviceId = deviceId, isPaired = true) }
        prefs?.edit()?.putString("paired_device_id", deviceId)?.apply()
        startObservingStatus(deviceId)
        startWatchdog()
    }

    fun unpairDevice() {
        prefs?.edit()?.remove("paired_device_id")?.apply()
        statusListener?.let { 
            getDb().getReference("live_status/${_uiState.value.pairedDeviceId}").removeEventListener(it)
        }
        watchdogJob?.cancel()
        _uiState.update { TvUiState(isSplashScreenActive = false) }
    }

    fun toggleSettings(open: Boolean) {
        _uiState.update { it.copy(isSettingsOpen = open) }
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

    fun triggerRetry() {
        retryJob?.cancel()
        _uiState.update { it.copy(retryCountdown = 10) }
        startObservingStatus(_uiState.value.pairedDeviceId)
    }

    private fun startObservingStatus(deviceId: String) {
        if (deviceId == "") return
        
        statusListener?.let { 
            getDb().getReference("live_status/${_uiState.value.pairedDeviceId}").removeEventListener(it)
        }

        statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val status = snapshot.child("status").getValue(String::class.java) ?: "IDLE"
                    val rtspUrl = snapshot.child("rtspUrl").getValue(String::class.java) ?: ""
                    val rtspSubUrl = snapshot.child("rtspSubUrl").getValue(String::class.java) ?: ""
                    val lastRecordingUrl = snapshot.child("lastRecordingUrl").getValue(String::class.java) ?: ""
                    val localReplayUrl = snapshot.child("localReplayUrl").getValue(String::class.java) ?: ""
                    val duration = snapshot.child("duration").getValue(Long::class.java) ?: 0L
                    val players = snapshot.child("players").children.mapNotNull { it.getValue(String::class.java) }
                    val courtTag = snapshot.child("courtTag").getValue(String::class.java) ?: ""
                    val isOnline = snapshot.child("isOnline").getValue(Boolean::class.java) ?: false
                    val remoteReplayId: Long = snapshot.child("lastReplaySessionId").getValue(Long::class.java) ?: -1L
                    val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                    val lastSyncTime = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

                    val shouldTriggerReplay = remoteReplayId > 0L && remoteReplayId != _uiState.value.lastReplaySessionId && status == "IDLE"

                    if (shouldTriggerReplay) {
                        viewModelScope.launch {
                            _uiState.update { it.copy(isReplayLoading = true, showReplayCompletePrompt = false) }
                            delay(2000)
                            _uiState.update { it.copy(isReplayLoading = false) }
                        }
                    }

                    updateDebug("Update received at $lastSyncTime")

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
                            isTabletOnline = isOnline,
                            lastReplaySessionId = remoteReplayId,
                            isAutoReplayActive = if (status == "RECORDING") false else if (shouldTriggerReplay) true else it.isAutoReplayActive,
                            showReplayCompletePrompt = if (status == "RECORDING") false else it.showReplayCompletePrompt,
                            lastUpdateTimestamp = timestamp,
                            debugInfo = "Sync OK: $lastSyncTime"
                        )
                    }

                    if (isOnline) {
                        retryJob?.cancel()
                        _uiState.update { it.copy(retryCountdown = 10) }
                    } else {
                        startRetryCountdown()
                    }
                } else {
                    updateDebug("Node Missing: $deviceId (Check Pairing ID)")
                    _uiState.update { it.copy(isTabletOnline = false) }
                    startRetryCountdown()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                updateDebug("Cancelled: ${error.message}")
            }
        }

        getDb().getReference("live_status/$deviceId").addValueEventListener(statusListener!!)
    }

    private fun startRetryCountdown() {
        if (retryJob?.isActive == true) return
        retryJob = viewModelScope.launch {
            while (_uiState.value.retryCountdown > 0 && !_uiState.value.isTabletOnline) {
                delay(1000)
                _uiState.update { it.copy(retryCountdown = it.retryCountdown - 1) }
            }
            if (!_uiState.value.isTabletOnline) {
                triggerRetry()
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
                    if (staleTime > 30000) { // 30 second timeout
                        if (_uiState.value.isTabletOnline) {
                            android.util.Log.w("TvWatchdog", "Tablet heartbeat stale ($staleTime ms). Forcing offline.")
                            _uiState.update { it.copy(isTabletOnline = false) }
                            startRetryCountdown()
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
    }
}
