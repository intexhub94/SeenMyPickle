package com.pbcam.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.pbcam.app.PBCamApplication
import com.pbcam.app.R
import com.pbcam.app.data.CameraSource
import com.pbcam.app.data.CameraStateManager
import com.pbcam.app.data.RecordingState
import com.pbcam.app.data.RecordingStateManager
import com.pbcam.app.data.SecurityUtils
import com.pbcam.app.data.WatermarkPosition
import com.pbcam.app.data.db.RecordingSession
import com.pbcam.app.data.db.RecordingStatus
import com.pbcam.app.worker.WorkerScheduler
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * RecordingService: Hardened hardware-accelerated monitoring and recording service.
 * Strictly follows SeeMyPickle Code Bible for enterprise-grade stability.
 */
class RecordingService : LifecycleService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentSessionId: Long? = null
    private var currentPartIndex = 1
    private var sessionEmail: String? = null
    
    private var activeRecording: Recording? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var previewUseCase: Preview? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var isBinding = false
    private var currentRotation: Int = Surface.ROTATION_0
    
    private var rtspSession: com.arthenica.ffmpegkit.FFmpegSession? = null
    
    private var internalFinalizeDeferred: CompletableDeferred<Unit>? = null
    private var loopDeferred = CompletableDeferred<Unit>()
    private val cameraProviderDeferred = CompletableDeferred<ProcessCameraProvider>()
    
    private var orientationEventListener: OrientationEventListener? = null

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("RecordingService", "Service Created")
        acquireWakeLock()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Monitoring Service Active"))
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            cameraProviderDeferred.complete(cameraProvider!!)
        }, ContextCompat.getMainExecutor(this))

        orientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val rotation = when (orientation) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                if (rotation != currentRotation) {
                    val oldRotation = currentRotation
                    currentRotation = rotation
                    android.util.Log.d("RecordingService", "Rotation Change: $oldRotation -> $rotation")
                    scope.launch(Dispatchers.Main) {
                        videoCapture?.targetRotation = rotation
                        previewUseCase?.targetRotation = rotation
                    }
                }
            }
        }
        orientationEventListener?.enable()
        
        // --- REACTIVE CAMERA BINDING (Code Bible Rule: 3.5 Hardware Bridge) ---
        scope.launch {
            CameraStateManager.surfaceProvider.collect { provider ->
                withContext(Dispatchers.Main) {
                    val currentSource = (application as PBCamApplication).settingsStore.cameraSource
                    android.util.Log.d("RecordingService", "Surface update. Source: $currentSource, Provider: ${provider != null}")
                    
                    if (currentSource != CameraSource.RTSP) {
                        if (provider != null && activeRecording != null) {
                            android.util.Log.d("RecordingService", "Applying surface to active recording preview")
                            previewUseCase?.setSurfaceProvider(provider)
                        } else if (provider == null && activeRecording == null) {
                            android.util.Log.d("RecordingService", "Idle and Surface null: Releasing hardware")
                            cameraProvider?.unbindAll()
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                sessionEmail = intent.getStringExtra("email")
                if (RecordingStateManager.recordingState.value == RecordingState.IDLE) {
                    startRecordingLoop()
                }
            }
            ACTION_STOP -> {
                scope.launch {
                    stopRecordingSync()
                    // HARDENING: Wait for the recording loop to finish saving and enqueuing worker
                    try {
                        withTimeout(10000) { // 10s safety timeout
                            loopDeferred.await()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("RecordingService", "Handoff wait timed out or failed")
                    }
                    
                    val source = (application as PBCamApplication).settingsStore.cameraSource
                    if (source == CameraSource.RTSP) {
                        stopSelf()
                    } else {
                        updatePreviewNotification()
                    }
                }
            }
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_EXIT_APP -> {
                android.util.Log.w("RecordingService", "CRITICAL: Shutdown triggered from notification.")
                android.os.Process.killProcess(android.os.Process.myPid())
            }
            else -> updatePreviewNotification()
        }
        return START_STICKY
    }

    private fun updatePreviewNotification() {
        val source = (application as PBCamApplication).settingsStore.cameraSource
        val label = if (source == CameraSource.RTSP) "Recording service active" else "Camera monitoring active"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(label))
    }

    private fun startRecordingLoop() {
        loopDeferred = CompletableDeferred()
        scope.launch {
            RecordingStateManager.updateState(RecordingState.RECORDING)
            val source = (application as PBCamApplication).settingsStore.cameraSource
            
            if (source == CameraSource.RTSP) {
                recordRtspLoop()
            } else {
                recordInternalLoop()
            }
            loopDeferred.complete(Unit)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun recordInternalLoop() {
        val repository = (application as PBCamApplication).recordingRepository
        val settings = (application as PBCamApplication).settingsStore
        
        val sessionTimestamp = System.currentTimeMillis()
        val descriptiveFileName = SecurityUtils.generateSessionFileName(sessionEmail ?: settings.alertEmail, sessionTimestamp)
        
        // 1. Setup Session
        val sessionId = repository.insertSession(
            RecordingSession(
                filename = File(getRecordingsDir(this), descriptiveFileName).absolutePath,
                startTime = sessionTimestamp,
                status = RecordingStatus.RECORDING,
                targetEmail = sessionEmail ?: settings.alertEmail,
                courtTag = settings.courtTag,
                source = CameraSource.INTERNAL
            )
        )
        currentSessionId = sessionId

        // 2. Ensure Camera Bound (Hardware Bridge)
        withContext(Dispatchers.Main) {
            bindCameraUseCases(CameraStateManager.surfaceProvider.value)
        }

        currentPartIndex = 1
        val sessionStartTime = System.currentTimeMillis()
        val totalLimitMs = TimeUnit.MINUTES.toMillis(settings.maxRecordingMinutes.toLong())

        // 3. Recording Parts Loop (Code Bible Rule: 3.5 Data Integrity)
        while (RecordingStateManager.recordingState.value != RecordingState.IDLE) {
            val totalElapsed = System.currentTimeMillis() - sessionStartTime
            if (totalElapsed >= totalLimitMs) {
                android.util.Log.d("RecordingService", "Total Internal session limit reached ($totalElapsed/$totalLimitMs). Finalizing.")
                RecordingStateManager.updateState(RecordingState.IDLE)
                break
            }

            if (RecordingStateManager.recordingState.value == RecordingState.PAUSED) {
                // RELEASE HARDWARE ON PAUSE (Memory/Battery Optimization)
                withContext(Dispatchers.Main) {
                    cameraProvider?.unbindAll()
                    previewUseCase = null
                    videoCapture = null
                }
                delay(1000)
                continue
            }

            // Re-bind hardware if returning from PAUSE or initial start
            if (previewUseCase == null) {
                withContext(Dispatchers.Main) {
                    bindCameraUseCases(CameraStateManager.surfaceProvider.value)
                }
            }

            val partFile = File(getRecordingsDir(this), "${descriptiveFileName.replace(".mp4", "")}_part${currentPartIndex}.mp4")
            
            val outputOptions = FileOutputOptions.Builder(partFile).build()
            internalFinalizeDeferred = CompletableDeferred()
            
            activeRecording = videoCapture!!.output
                .prepareRecording(this, outputOptions)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(this)) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        internalFinalizeDeferred?.complete(Unit)
                    }
                }
            
            try {
                // Segment logic: Stay in this loop until Match ends OR session limit reached
                val remainingSessionMs = totalLimitMs - (System.currentTimeMillis() - sessionStartTime)
                // Also cap segment at 10 mins (standard micro-segmenting)
                val segmentLimitMs = TimeUnit.MINUTES.toMillis(10).coerceAtMost(remainingSessionMs)
                
                withTimeout(segmentLimitMs.coerceAtLeast(1000L)) {
                    while (RecordingStateManager.recordingState.value == RecordingState.RECORDING) {
                        delay(2000)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                if (System.currentTimeMillis() - sessionStartTime >= totalLimitMs) {
                    android.util.Log.d("RecordingService", "Internal Match limit reached. Stopping.")
                    RecordingStateManager.updateState(RecordingState.IDLE)
                } else {
                    android.util.Log.d("RecordingService", "Internal Segment rotation.")
                }
            }
            
            activeRecording?.stop()
            internalFinalizeDeferred?.await()
            currentPartIndex++
        }

        val finalSession = repository.getSession(sessionId)
        if (finalSession != null) {
            val recordingsDir = getRecordingsDir(this)
            val baseName = descriptiveFileName.replace(".mp4", "")
            val parts = recordingsDir.listFiles { dir: File, name: String -> 
                name.startsWith(baseName) && name.contains("_part") && (name.endsWith(".mp4") || name.endsWith(".ts")) && 
                File(dir, name).length() > 1024
            }
            
            if (parts.isNullOrEmpty()) {
                android.util.Log.e("RecordingService", "No valid Internal parts found for $baseName")
                repository.markFailed(sessionId, "No video data captured from internal camera.")
            } else {
                android.util.Log.d("RecordingService", "Pipeline Handoff: Enqueuing worker for $baseName")
                repository.updateSession(finalSession.copy(status = RecordingStatus.PENDING_UPLOAD))
                WorkerScheduler.enqueueUpload(this, sessionId)
            }
        }
        currentSessionId = null
        sessionEmail = null
        RecordingStateManager.updateState(RecordingState.IDLE)
    }

    private suspend fun recordRtspLoop() {
        val repository = (application as PBCamApplication).recordingRepository
        val settings = (application as PBCamApplication).settingsStore
        val rtspUrl = settings.rtspUrl
        
        val sessionTimestamp = System.currentTimeMillis()
        val descriptiveFileName = SecurityUtils.generateSessionFileName(sessionEmail ?: settings.alertEmail, sessionTimestamp)

        val sessionId = repository.insertSession(
            RecordingSession(
                filename = File(getRecordingsDir(this), descriptiveFileName).absolutePath,
                startTime = sessionTimestamp,
                status = RecordingStatus.RECORDING,
                targetEmail = sessionEmail ?: settings.alertEmail,
                courtTag = settings.courtTag,
                source = CameraSource.RTSP
            )
        )
        currentSessionId = sessionId
        
        currentPartIndex = 1
        val sessionStartTime = System.currentTimeMillis()
        val totalLimitMs = TimeUnit.MINUTES.toMillis(settings.maxRecordingMinutes.toLong())
        
        var lastExecutedSession: com.arthenica.ffmpegkit.FFmpegSession? = null
        var fatalErrorDetected = false
        
        while (RecordingStateManager.recordingState.value != RecordingState.IDLE && !fatalErrorDetected) {
            val totalElapsed = System.currentTimeMillis() - sessionStartTime
            if (totalElapsed >= totalLimitMs) {
                android.util.Log.d("RecordingService", "Total RTSP session limit reached. Finalizing.")
                RecordingStateManager.updateState(RecordingState.IDLE)
                break
            }

            if (RecordingStateManager.recordingState.value == RecordingState.PAUSED) {
                rtspSession?.cancel()
                rtspSession = null
                delay(1000)
                continue
            }

            val partFile = File(getRecordingsDir(this), "${descriptiveFileName.replace(".mp4", "")}_part${currentPartIndex}.ts")
            
            // HARDENED RESILIENT COMMAND (Code Bible 3.5 Hardening)
            // Restored buffers and reorder queue to absorb Wi-Fi jitter.
            val userAgent = "SeenMyPickle/1.0"
            val cmd = "-rtsp_transport tcp -reorder_queue_size 1024 -buffer_size 52428800 " +
                      "-user_agent \"$userAgent\" -timeout 15000000 " +
                      "-i \"$rtspUrl\" -map 0:v -map 0:a? -c copy -y \"${partFile.absolutePath}\""
            
            android.util.Log.d("RecordingService", "Executing simplified RTSP command: ${SecurityUtils.sanitizeLogs(cmd)}")

            val startTime = System.currentTimeMillis()
            val session = com.arthenica.ffmpegkit.FFmpegKit.executeAsync(cmd) { s ->
                val logs = SecurityUtils.sanitizeLogs(s.allLogsAsString)
                if (!com.arthenica.ffmpegkit.ReturnCode.isSuccess(s.returnCode) && !com.arthenica.ffmpegkit.ReturnCode.isCancel(s.returnCode)) {
                    android.util.Log.e("RecordingService", "FFmpeg RTSP ERROR: ${logs.takeLast(1000)}")
                }
            }
            rtspSession = session
            lastExecutedSession = session
            
            val rotationMs = TimeUnit.MINUTES.toMillis(10)
            val remainingSessionMs = totalLimitMs - (System.currentTimeMillis() - sessionStartTime)
            val segmentLimitMs = rotationMs.coerceAtMost(remainingSessionMs)

            try {
                withTimeout(segmentLimitMs.coerceAtLeast(1000L)) {
                    while (RecordingStateManager.recordingState.value == RecordingState.RECORDING) {
                        if (rtspSession?.state == com.arthenica.ffmpegkit.SessionState.COMPLETED) {
                            val duration = System.currentTimeMillis() - startTime
                            // FATAL ERROR GUARD: If it fails in less than 2 seconds, don't loop
                            if (duration < 2000) {
                                android.util.Log.e("RecordingService", "RTSP connection failed immediately. Stopping match.")
                                fatalErrorDetected = true
                            }
                            break
                        }
                        delay(2000)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                if (System.currentTimeMillis() - sessionStartTime >= totalLimitMs) {
                    android.util.Log.d("RecordingService", "RTSP Match limit reached. Stopping.")
                    RecordingStateManager.updateState(RecordingState.IDLE)
                } else {
                    android.util.Log.d("RecordingService", "RTSP Segment rotation.")
                }
            }
            
            rtspSession?.cancel()
            rtspSession = null
            currentPartIndex++
        }

        delay(1000) // Small buffer to let last session state finalize
        
        val finalSession = repository.getSession(sessionId)
        if (finalSession != null) {
            val recordingsDir = getRecordingsDir(this)
            val baseName = descriptiveFileName.replace(".mp4", "")
            val parts = recordingsDir.listFiles { dir: File, name: String -> 
                name.startsWith(baseName) && (name.endsWith(".ts") || name.endsWith(".mp4")) && File(dir, name).length() > 1024
            }
            
            if (parts.isNullOrEmpty()) {
                // Ensure we have the latest session state
                val finalLogs = SecurityUtils.sanitizeLogs(lastExecutedSession?.allLogsAsString ?: "No logs captured.")
                val exitCode = lastExecutedSession?.returnCode?.value ?: -1
                
                // DEEP DIAGNOSTICS: If log is short, take it all. If long, take the end.
                val logSnippet = if (finalLogs.length < 1500) finalLogs else finalLogs.takeLast(1000)
                val cleanSnippet = logSnippet.replace("\n", " | ")
                
                android.util.Log.e("RecordingService", "No valid RTSP data received. Exit: $exitCode. Logs: $cleanSnippet")
                repository.markFailed(sessionId, "Connection failed. Code: $exitCode. Technical: $cleanSnippet")
            } else {
                parts.forEach { part ->
                    android.util.Log.d("RecordingService", "Audit: Finalized part ${part.name} (Size: ${part.length()} bytes)")
                }
                android.util.Log.d("RecordingService", "Pipeline Handoff: Enqueuing worker for $baseName")
                repository.updateSession(finalSession.copy(status = RecordingStatus.PENDING_UPLOAD))
                WorkerScheduler.enqueueUpload(this, sessionId)
            }
        }
        currentSessionId = null
        sessionEmail = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun bindCameraUseCases(surfaceProvider: Preview.SurfaceProvider?) {
        if (isBinding) return
        isBinding = true

        try {
            val provider = cameraProvider ?: cameraProviderDeferred.await()
            withContext(Dispatchers.Main) {
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.higherQualityOrLowerThan(Quality.FHD)))
                    .setTargetVideoEncodingBitRate(6 * 1024 * 1024)
                    .build()

                videoCapture = VideoCapture.Builder(recorder)
                    .setTargetRotation(currentRotation)
                    .build()

                previewUseCase = Preview.Builder()
                    .setTargetRotation(currentRotation)
                    .build()

                if (surfaceProvider != null) {
                    previewUseCase?.setSurfaceProvider(surfaceProvider)
                }

                provider.unbindAll()
                provider.bindToLifecycle(
                    this@RecordingService,
                    getCameraSelector(),
                    previewUseCase!!,
                    videoCapture!!
                )
                android.util.Log.d("RecordingService", "Hardware bound successfully")
            }
        } catch (e: Exception) {
            android.util.Log.e("RecordingService", "Binding failed", e)
        } finally {
            isBinding = false
        }
    }

    private fun getCameraSelector(): CameraSelector {
        val source = (application as PBCamApplication).settingsStore.cameraSource
        return if (source == CameraSource.USB) {
            try {
                CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_EXTERNAL)
                    .build()
            } catch (e: Exception) {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    private fun pauseRecording() {
        android.util.Log.d("RecordingService", "Pausing recording")
        val source = (application as PBCamApplication).settingsStore.cameraSource
        if (source != CameraSource.RTSP) {
            activeRecording?.pause()
            scope.launch(Dispatchers.Main) {
                cameraProvider?.unbindAll()
            }
        } else {
            rtspSession?.cancel()
        }
        RecordingStateManager.updateState(RecordingState.PAUSED)
    }

    private fun resumeRecording() {
        android.util.Log.d("RecordingService", "Resuming recording")
        val source = (application as PBCamApplication).settingsStore.cameraSource
        if (source != CameraSource.RTSP) {
            activeRecording?.resume()
            scope.launch(Dispatchers.Main) {
                bindCameraUseCases(CameraStateManager.surfaceProvider.value)
            }
        }
        RecordingStateManager.updateState(RecordingState.RECORDING)
    }

    private suspend fun stopRecordingSync() {
        RecordingStateManager.updateState(RecordingState.IDLE)
        activeRecording?.stop()
        rtspSession?.cancel()
        
        withContext(Dispatchers.Main) {
            cameraProvider?.unbindAll()
            videoCapture = null
            previewUseCase = null
        }
        
        internalFinalizeDeferred?.await()
        activeRecording = null
        rtspSession = null
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        orientationEventListener?.disable()
        scope.cancel()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SeenMyPickle:Recording")
        wakeLock?.acquire()
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Recording Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val exitIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_EXIT_APP
        }
        val pendingExit = android.app.PendingIntent.getService(
            this, 0, exitIntent, 
            android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SeenMyPickle")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "EXIT APP", pendingExit)
            .build()
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_EXIT_APP = "ACTION_EXIT_APP"
        const val CHANNEL_ID = "recording_channel"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, RecordingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startRecording(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun pause(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }

        fun getRecordingsDir(context: Context): File {
            val dir = File(context.filesDir, "recordings")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }
    }
}
