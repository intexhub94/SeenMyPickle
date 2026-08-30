package com.pbcam.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pbcam.app.PBCamApplication
import com.pbcam.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class ServerUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sessionId = inputData.getLong(KEY_SESSION_ID, -1L)
        if (sessionId < 0) return@withContext Result.failure()

        val app = applicationContext as PBCamApplication
        val repository = app.recordingRepository
        val settings = app.settingsStore

        if (!settings.enablePcOffload) {
            Log.d(TAG, "PC Storage Offload is disabled in settings. Skipping ServerUploadWorker.")
            return@withContext Result.success()
        }

        val pcIp = settings.pcServerIp
        val pcPort = settings.pcServerPort
        if (pcIp.isBlank()) {
            Log.e(TAG, "PC Server IP address is empty. Skipping PC offload.")
            return@withContext Result.failure()
        }

        try {
            setForeground(createForegroundInfo("Preparing PC Upload...", 0))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set foreground state", e)
        }

        val session = repository.getSession(sessionId) ?: run {
            Log.e(TAG, "Session $sessionId not found in DB")
            return@withContext Result.failure()
        }

        val mp4File = File(session.filename)
        if (!mp4File.exists() || mp4File.length() < 1024) {
            Log.e(TAG, "Session $sessionId file missing or corrupted: ${session.filename}")
            return@withContext Result.failure()
        }

        val totalBytes = mp4File.length()
        val uploadUrlStr = "http://$pcIp:$pcPort/api/upload"

        try {
            repository.updateProgress(sessionId, 0.02f, "CONNECTING TO PC SERVER...")
            setProgress(workDataOf(KEY_SESSION_ID to sessionId, PROGRESS_VAL to 0.02f, PROGRESS_MSG to "CONNECTING TO PC SERVER..."))

            val url = URL(uploadUrlStr)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 30000
                requestMethod = "POST"
                doOutput = true
                setChunkedStreamingMode(64 * 1024) // 64KB chunks
                setRequestProperty("Content-Type", "application/octet-stream")
                setRequestProperty("Content-Length", totalBytes.toString())
                setRequestProperty("Filename", mp4File.name)
            }

            var bytesSent = 0L
            val buffer = ByteArray(64 * 1024)

            FileInputStream(mp4File).use { fileInputStream ->
                BufferedInputStream(fileInputStream).use { bufferedInput ->
                    connection.outputStream.use { outputStream ->
                        var bytesRead: Int
                        while (bufferedInput.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            bytesSent += bytesRead

                            val progress = (bytesSent.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            val percent = (progress * 100).toInt()
                            val msg = "UPLOADING TO PC: $percent%"

                            repository.updateProgress(sessionId, progress, msg)
                            setProgress(workDataOf(KEY_SESSION_ID to sessionId, PROGRESS_VAL to progress, PROGRESS_MSG to msg))

                            if (percent % 10 == 0) {
                                setForegroundAsync(createForegroundInfo(msg, percent))
                            }
                        }
                        outputStream.flush()
                    }
                }
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                Log.d(TAG, "Successfully uploaded ${mp4File.name} ($totalBytes bytes) to PC server at $pcIp:$pcPort")

                // PURGE LOCAL FILE SAFEGUARD
                if (mp4File.exists()) {
                    val deleted = mp4File.delete()
                    Log.d(TAG, "Local file ${mp4File.name} deleted post PC upload: $deleted")
                }

                // Clean up any temporary part files associated with this session
                try {
                    val parentDir = mp4File.parentFile
                    if (parentDir != null && parentDir.exists()) {
                        parentDir.listFiles()?.forEach { file ->
                            if (file.name.contains("proc_${sessionId}") || file.name.contains("_part") && file.name.contains(mp4File.name.take(10))) {
                                file.delete()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Part cleanup non-fatal warning: ${e.localizedMessage}")
                }

                repository.updateProgress(sessionId, 1.0f, "OFFLOADED TO PC SERVER")
                return@withContext Result.success()
            } else {
                val errorMsg = "PC Server returned HTTP $responseCode"
                Log.e(TAG, "Upload failed for session $sessionId: $errorMsg")
                repository.updateProgress(sessionId, 0f, "PC SERVER ERROR ($responseCode) - RETRYING")
                return@withContext Result.retry()
            }

        } catch (e: Exception) {
            val errorMsg = "PC Upload Error: ${e.localizedMessage ?: "Network error"}"
            Log.e(TAG, "Exception during PC upload for session $sessionId: $errorMsg", e)
            repository.updateProgress(sessionId, 0f, "PC OFFLOAD RETRYING...")
            return@withContext Result.retry()
        }
    }

    private fun createForegroundInfo(message: String, progressPercent: Int): ForegroundInfo {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "pc_server_upload_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "PC Storage Offload",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("SeenMyPickle - PC Storage Offload")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setProgress(100, progressPercent, progressPercent == 0)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "ServerUploadWorker"
        const val KEY_SESSION_ID = "session_id"
        const val PROGRESS_VAL = "progress_val"
        const val PROGRESS_MSG = "progress_msg"
        private const val NOTIFICATION_ID = 20261
    }
}
