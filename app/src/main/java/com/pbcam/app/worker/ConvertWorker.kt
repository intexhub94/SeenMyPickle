package com.pbcam.app.worker

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.pbcam.app.PBCamApplication
import com.pbcam.app.R
import com.pbcam.app.data.CameraSource
import com.pbcam.app.data.WatermarkPosition
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import java.io.File

class ConvertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong(KEY_SESSION_ID, -1L)
        if (sessionId < 0) return Result.failure()

        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            android.util.Log.e("ConvertWorker", "Failed to set foreground state", e)
        }

        val app = applicationContext as PBCamApplication
        val repository = app.recordingRepository
        
        // 1. THERMAL THROTTLING
        try {
            val batteryStatus = applicationContext.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val temp = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            if (temp > 450) { 
                android.util.Log.w("ConvertWorker", "Device temperature too high ($temp/10 °C). Delaying processing.")
                return Result.retry()
            }
        } catch (e: Exception) {
            android.util.Log.e("ConvertWorker", "Failed to check battery temperature", e)
        }

        val session = repository.getSession(sessionId) ?: run {
            android.util.Log.e("ConvertWorker", "Session $sessionId not found in DB")
            return Result.failure()
        }
        
        repository.markProcessing(sessionId)
        val initialMsg = "Step 0/3: Discovering parts..."
        repository.updateProgress(sessionId, 0.05f, initialMsg)
        setProgress(workDataOf(KEY_SESSION_ID to sessionId, PROGRESS_VAL to 0.05f, PROGRESS_MSG to initialMsg))
        
        val mp4File = File(session.filename)
        val recordingsDir = mp4File.parentFile ?: run {
            val errorMsg = "Could not determine recordings directory for session $sessionId"
            android.util.Log.e("ConvertWorker", errorMsg)
            repository.markFailed(sessionId, errorMsg)
            return Result.failure()
        }
        
        // --- DATA INTEGRITY: SEARCH FOR ALL PARTS ---
        val baseName = mp4File.name.replace(".mp4", "")
        android.util.Log.d("ConvertWorker", "Audit: Searching for parts for session $sessionId (baseName: $baseName)")
        
        var parts: List<File> = emptyList()
        var retryCount = 0
        val maxRetries = 5
        
        while (parts.isEmpty() && retryCount < maxRetries) {
            if (retryCount > 0) {
                android.util.Log.w("ConvertWorker", "Retry $retryCount: Waiting for segments to appear on disk...")
                delay(2000)
            }
            
            val allFiles = recordingsDir.listFiles() ?: emptyArray()
            parts = allFiles.filter { 
                it.name.startsWith(baseName, ignoreCase = true) && 
                (it.name.endsWith(".ts", ignoreCase = true) || it.name.endsWith(".mp4", ignoreCase = true)) && 
                it.absolutePath != mp4File.absolutePath 
            }.sortedBy { it.name.lowercase() } // Hardened: Sort by name to ensure temporal order
            
            retryCount++
        }

        if (parts.isEmpty()) {
            val errorMsg = "Critical: No parts found for $baseName after $maxRetries retries."
            android.util.Log.e("ConvertWorker", errorMsg)
            repository.markFailed(sessionId, "Storage Error: No footage segments found.")
            return Result.failure()
        }

        // --- IO HARDENING: Wait for file availability and size stability ---
        // OPTIMIZATION (Round 25): Atomic Handoff - Check stability across 3x1s intervals.
        for (part in parts) {
            var isReady = false
            var fileRetry = 0
            val maxFileRetries = 10 // Increased retries for shorter intervals
            
            while (!isReady && fileRetry < maxFileRetries) {
                if (checkFileStability(part)) {
                    isReady = true
                    android.util.Log.d("ConvertWorker", "Part ${part.name} is stable.")
                } else {
                    android.util.Log.w("ConvertWorker", "Waiting for part ${part.name} to stabilize... Retry $fileRetry")
                    delay(1000)
                    fileRetry++
                }
            }
            
            if (!isReady) {
                val errorMsg = "IO Error: Segment ${part.name} is busy or still growing."
                android.util.Log.e("ConvertWorker", errorMsg)
                repository.markFailed(sessionId, errorMsg)
                return Result.failure()
            }
        }

        // --- STEP 0.5: DYNAMIC FPS & CODEC DETECTION ---
        var detectedFps = 30
        var detectedCodec = "unknown"
        try {
            // HARDENING: Detect both FPS and Codec to avoid forcing wrong hardware decoders.
            val probeCmd = "-v error -select_streams v:0 -show_entries stream=r_frame_rate,codec_name -of default=noprint_wrappers=1:nokey=1 \"${parts.first().absolutePath}\""
            val probeResult = com.arthenica.ffmpegkit.FFprobeKit.execute(probeCmd)
            if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(probeResult.returnCode)) {
                val outputLines = probeResult.allLogsAsString.trim().split("\n")
                if (outputLines.size >= 2) {
                    val fpsRaw = outputLines[0].trim()
                    detectedCodec = outputLines[1].trim()
                    if (fpsRaw.contains("/")) {
                        val split = fpsRaw.split("/")
                        val num = split[0].toDoubleOrNull() ?: 30.0
                        val den = split[1].toDoubleOrNull() ?: 1.0
                        detectedFps = (num / den).toInt().coerceIn(15, 60)
                    } else {
                        detectedFps = fpsRaw.toIntOrNull()?.coerceIn(15, 60) ?: 30
                    }
                }
                android.util.Log.d("ConvertWorker", "Audit: Detected Source Codec: $detectedCodec, FPS: $detectedFps")
            }
        } catch (e: Exception) {
            android.util.Log.w("ConvertWorker", "FPS/Codec detection failed, defaulting to 30: ${e.message}")
        }

        val processingSource = File(applicationContext.cacheDir, "proc_${sessionId}_source.mp4")
        val watermarkFile = File(applicationContext.cacheDir, "proc_${sessionId}_wm.png")
        val watermarkedRecording = File(applicationContext.cacheDir, "proc_${sessionId}_final.mp4")
        val scaledLogo = File(applicationContext.cacheDir, "proc_${sessionId}_scaled.png")
        val concatList = File(applicationContext.cacheDir, "proc_${sessionId}_list.txt")

        try {
            // STEP 1/3: CONCAT PARTS
            val step1Msg = "Step 1/3: Combining parts..."
            repository.updateProgress(sessionId, 0.1f, step1Msg)
            setProgress(workDataOf(KEY_SESSION_ID to sessionId, PROGRESS_VAL to 0.1f, PROGRESS_MSG to step1Msg))
            
            concatList.writeText(parts.joinToString("\n") { "file '${it.absolutePath}'" })
            
            val concatCmd = "-f concat -safe 0 -analyzeduration 100M -probesize 100M " +
                            "-i \"${concatList.absolutePath}\" -fflags +igndts+genpts " +
                            "-c copy -avoid_negative_ts make_zero -y \"${processingSource.absolutePath}\""
            val concatResult = com.arthenica.ffmpegkit.FFmpegKit.execute(concatCmd)
            
            if (!com.arthenica.ffmpegkit.ReturnCode.isSuccess(concatResult.returnCode)) {
                repository.markFailed(sessionId, "Combining video parts failed (Code ${concatResult.returnCode})")
                return Result.failure()
            }
            
            if (!processingSource.exists() || processingSource.length() < 1024) {
                repository.markFailed(sessionId, "Concat produced empty file.")
                return Result.failure()
            }

            // STEP 2/3: HARDWARE ACCELERATED WATERMARK
            // STABILITY HARDENING: Processing all sources in the worker to prevent recording stutter.
            val step2Msg = "Step 2/3: Applying watermark..."
            repository.updateProgress(sessionId, 0.4f, step2Msg)
            setProgress(workDataOf(KEY_SESSION_ID to sessionId, PROGRESS_VAL to 0.4f, PROGRESS_MSG to step2Msg))
            
            // Extract Logo
            try {
                val customPath = app.settingsStore.customWatermarkPath
                if (customPath != null && File(customPath).exists()) {
                    File(customPath).copyTo(watermarkFile, overwrite = true)
                } else {
                    applicationContext.resources.openRawResource(R.raw.app_logo_email).use { input ->
                        watermarkFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("ConvertWorker", "Could not load logo: ${e.message}")
            }

            val pos = app.settingsStore.watermarkPosition
            val watermarkFilter = when (pos) {
                WatermarkPosition.TOP_LEFT -> "overlay=20:20:format=yuv420:shortest=1,fps=30,setpts=PTS-STARTPTS[v]"
                WatermarkPosition.TOP_RIGHT -> "overlay=main_w-overlay_w-20:20:format=yuv420:shortest=1,fps=30,setpts=PTS-STARTPTS[v]"
                WatermarkPosition.BOTTOM_LEFT -> "overlay=20:main_h-overlay_h-20:format=yuv420:shortest=1,fps=30,setpts=PTS-STARTPTS[v]"
                WatermarkPosition.BOTTOM_RIGHT -> "overlay=main_w-overlay_w-20:main_h-overlay_h-20:format=yuv420:shortest=1,fps=30,setpts=PTS-STARTPTS[v]"
                else -> "overlay=main_w-overlay_w-20:main_h-overlay_h-20:format=yuv420:shortest=1,fps=30,setpts=PTS-STARTPTS[v]"
            }
            
            val preScaleCmd = "-i \"${watermarkFile.absolutePath}\" -vf \"scale=iw*0.15:-1\" -y \"${scaledLogo.absolutePath}\""
            com.arthenica.ffmpegkit.FFmpegKit.execute(preScaleCmd)

            val cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

            // HARDENING (Round 27): Removed forced input hardware decoder. 
            // Letting FFmpeg auto-select decoder is safer for H.265 (HEVC) sources.
            val watermarkCmd = if (scaledLogo.exists() && scaledLogo.length() > 0) {
                "-thread_queue_size 16384 -fflags +igndts+genpts -analyzeduration 100M -probesize 100M -i \"${processingSource.absolutePath}\" " +
                "-thread_queue_size 16384 -i \"${scaledLogo.absolutePath}\" -filter_complex \"$watermarkFilter\" -map [v] -map 0:a? " +
                "-c:v h264_mediacodec -b:v 5M -maxrate 8M -bufsize 20M -profile:v main -level 4.1 -bf 0 -g 60 -tune zerolatency " +
                "-af \"aresample=async=1\" " +
                "-r 30 -vsync cfr -pix_fmt yuv420p -c:a aac -b:a 96k -ar 44100 -movflags +faststart -threads $cpuCores -y \"${watermarkedRecording.absolutePath}\""
            } else {
                android.util.Log.w("ConvertWorker", "Watermark file missing or empty. Skipping watermark.")
                "-thread_queue_size 16384 -fflags +igndts+genpts -analyzeduration 100M -probesize 100M -i \"${processingSource.absolutePath}\" " +
                "-vf \"fps=30,setpts=PTS-STARTPTS\" -map 0:v -map 0:a? -c:v h264_mediacodec -b:v 5M -maxrate 8M -bufsize 20M -profile:v main -level 4.1 -bf 0 -g 60 -tune zerolatency " +
                "-af \"aresample=async=1\" " +
                "-r 30 -vsync cfr -pix_fmt yuv420p -c:a aac -b:a 96k -ar 44100 -movflags +faststart -threads $cpuCores -y \"${watermarkedRecording.absolutePath}\""
            }

            val totalDurationMs = (session.endTime ?: System.currentTimeMillis()) - session.startTime
            val deferred = CompletableDeferred<com.arthenica.ffmpegkit.Session>()

            com.arthenica.ffmpegkit.FFmpegKit.executeAsync(watermarkCmd, { kitResult ->
                deferred.complete(kitResult)
            }, { /* Log callback */ }, { stats ->
                val timeInMs = stats.time
                if (totalDurationMs > 0) {
                    val convertProgress = (timeInMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                    val overallProgress = 0.4f + (convertProgress * 0.4f)
                    val percent = (overallProgress * 100).toInt()
                    val progressMsg = "Step 2/3: Watermarking... $percent%"
                    
                    kotlinx.coroutines.runBlocking {
                        repository.updateProgress(sessionId, overallProgress, progressMsg)
                        setProgress(workDataOf(KEY_SESSION_ID to sessionId, PROGRESS_VAL to overallProgress, PROGRESS_MSG to progressMsg))
                    }
                }
            })

            val kitResult = deferred.await()
            
            if (!com.arthenica.ffmpegkit.ReturnCode.isSuccess(kitResult.returnCode) || !watermarkedRecording.exists() || watermarkedRecording.length() < 1024) {
                val logs = kitResult.allLogsAsString.takeLast(500)
                android.util.Log.e("ConvertWorker", "Watermark failed (Code ${kitResult.returnCode}). Logs: $logs")
                
                android.util.Log.w("ConvertWorker", "RESCUE MODE: Re-muxing bitstream with FastStart...")
                val rescueCmd = "-i \"${processingSource.absolutePath}\" -c copy -movflags +faststart -y \"${watermarkedRecording.absolutePath}\""
                val rescueResult = com.arthenica.ffmpegkit.FFmpegKit.execute(rescueCmd)
                
                if (!com.arthenica.ffmpegkit.ReturnCode.isSuccess(rescueResult.returnCode)) {
                    android.util.Log.e("ConvertWorker", "RESCUE FAILED: Final fallback to raw copy.")
                    processingSource.copyTo(watermarkedRecording, overwrite = true)
                }
            }

            // STEP 3/3: FINALIZE
            val step3Msg = "Step 3/3: Finalizing MP4..."
            repository.updateProgress(sessionId, 0.8f, step3Msg)
            setProgress(workDataOf(KEY_SESSION_ID to sessionId, PROGRESS_VAL to 0.8f, PROGRESS_MSG to step3Msg))
            watermarkedRecording.copyTo(mp4File, overwrite = true)
            
            // HARDENING: Clean up segments ONLY on success and valid output size.
            if (mp4File.exists() && mp4File.length() > 1024 * 1024) {
                parts.forEach { it.delete() }
            } else {
                android.util.Log.w("ConvertWorker", "Suspiciously small output (${mp4File.length()} bytes). Preserving segments.")
            }
            
            WorkerScheduler.enqueueOnlyUpload(applicationContext, sessionId)
            return Result.success(workDataOf(KEY_SESSION_ID to sessionId))

        } catch (e: Exception) {
            android.util.Log.e("ConvertWorker", "Fatal worker error", e)
            repository.markFailed(sessionId, e.message ?: "Internal processing error")
            return Result.failure()
        } finally {
            processingSource.delete(); watermarkedRecording.delete(); watermarkFile.delete(); scaledLogo.delete(); concatList.delete()
        }
    }

    private suspend fun checkFileStability(file: File): Boolean {
        if (!file.exists() || !file.canRead()) return false
        var lastSize = file.length()
        if (lastSize < 1024) return false
        
        for (i in 1..3) {
            delay(1000)
            val currentSize = file.length()
            if (currentSize != lastSize || currentSize < 1024) return false
            lastSize = currentSize
        }
        return true
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo("Processing high-fidelity recording...")
    }

    private fun createForegroundInfo(message: String): ForegroundInfo {
        val channelId = "pbcam_worker"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, "Background Processing", android.app.NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val exitIntent = android.content.Intent(applicationContext, com.pbcam.app.service.RecordingService::class.java).apply {
            action = com.pbcam.app.service.RecordingService.ACTION_EXIT_APP
        }
        val pendingExit = android.app.PendingIntent.getService(
            applicationContext, 1, exitIntent, 
            android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("SeenMyPickle: Processing")
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "EXIT APP", pendingExit)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(2001, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            ForegroundInfo(2001, notification)
        }
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"
        const val PROGRESS_VAL = "progress_val"
        const val PROGRESS_MSG = "progress_msg"
    }
}
