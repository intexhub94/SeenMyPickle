package com.pbcam.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pbcam.app.PBCamApplication
import com.pbcam.app.R
import com.pbcam.app.auth.GoogleAuthManager
import com.pbcam.app.cloud.DriveUploader
import com.pbcam.app.cloud.GmailNotifier
import com.pbcam.app.data.db.RecordingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong(KEY_SESSION_ID, -1L)
        if (sessionId < 0) return Result.failure()

        val app = applicationContext as PBCamApplication
        val repository = app.recordingRepository
        val settings = app.settingsStore
        val authManager = GoogleAuthManager(applicationContext)

        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            android.util.Log.e("UploadWorker", "Failed to set foreground state", e)
        }

        try {
            val session = repository.getSession(sessionId) ?: run {
                android.util.Log.e("UploadWorker", "Session $sessionId not found in DB")
                return Result.failure()
            }
            
            if (session.status == RecordingStatus.COMPLETED && session.gDriveUrl != null) {
                return Result.success()
            }

            val mp4File = File(session.filename)
            if (!mp4File.exists() || mp4File.length() < 1024) {
                val errorMsg = "Finalized video file missing (Processing error)"
                android.util.Log.e("UploadWorker", "Session $sessionId error: $errorMsg")
                repository.markFailed(sessionId, errorMsg)
                return Result.failure()
            }

            repository.updateProgress(sessionId, 0.01f, "AUTHENTICATING...")
            setProgress(workDataOf(KEY_SESSION_ID to sessionId, PROGRESS_VAL to 0.01f, PROGRESS_MSG to "AUTHENTICATING..."))

            val accessToken = authManager.getFreshAccessToken() ?: run {
                val msg = "Auth Token Error: Google Sign-In required (Re-login in Settings)"
                android.util.Log.e("UploadWorker", "Failed to obtain access token for session $sessionId")
                repository.markFailed(sessionId, msg)
                return Result.failure(workDataOf(KEY_SESSION_ID to sessionId))
            }

            val sessionTag = session.courtTag ?: settings.courtTag
            val alertEmail = session.targetEmail ?: settings.alertEmail
            val retentionDays = settings.retentionDays

            repository.markUploading(sessionId)
            val initialMsg = "SEARCHING FOLDER..."
            repository.updateProgress(sessionId, 0.05f, initialMsg)
            setProgress(workDataOf(KEY_SESSION_ID to sessionId, PROGRESS_VAL to 0.05f, PROGRESS_MSG to initialMsg))
            setForeground(createForegroundInfo("Preparing Drive...", 5))

            val uploadResult = DriveUploader.upload(accessToken, mp4File, sessionTag) { progress ->
                val percent = (progress * 100).toInt()
                val msg = when {
                    percent < 2 -> "SEARCHING FOLDER..."
                    percent < 5 -> "HANDSHAKING..."
                    percent < 10 -> "STARTING DATA STREAM..."
                    else -> "UPLOADING: $percent%"
                }
                
                // ATOMIC PROGRESS UPDATE: Use runBlocking to ensure database integrity 
                // during network-heavy upload.
                kotlinx.coroutines.runBlocking {
                    repository.updateProgress(sessionId, progress, msg)
                    setProgress(workDataOf(KEY_SESSION_ID to sessionId, PROGRESS_VAL to progress, PROGRESS_MSG to msg))
                }
                
                if (percent % 2 == 0) {
                    setForegroundAsync(createForegroundInfo(msg, percent))
                }
            }

            val shareUrl = uploadResult.url ?: run {
                val errorMsg = uploadResult.error ?: "Unknown Drive upload error"
                android.util.Log.e("UploadWorker", "Session $sessionId failed: $errorMsg")
                
                // Smart Result: Retry on timeouts or generic errors, Fail on definite API errors
                return if (errorMsg.contains("403") || errorMsg.contains("401") || errorMsg.contains("404") || errorMsg.contains("400") || errorMsg.contains("Google API Error")) {
                    if (errorMsg.contains("401") || errorMsg.contains("403")) {
                        authManager.invalidateToken(accessToken)
                    }
                    repository.markFailed(sessionId, "Upload Permanent Error: $errorMsg")
                    Result.failure(workDataOf(KEY_SESSION_ID to sessionId))
                } else {
                    val retryMsg = "Network Error: ${errorMsg.take(50)} (Retrying...)"
                    repository.updateProgress(sessionId, 0f, retryMsg)
                    return Result.retry()
                }
            }

            val emailMsg = "Sending final email..."
            // ATOMIC PROGRESS UPDATE: Mark as sending email in DB
            kotlinx.coroutines.runBlocking {
                repository.markSending(sessionId)
                repository.updateProgress(sessionId, 0.95f, emailMsg)
                setProgress(workDataOf(KEY_SESSION_ID to sessionId, PROGRESS_VAL to 0.95f, PROGRESS_MSG to emailMsg))
            }
            setForegroundAsync(createForegroundInfo("Sending email notification...", 95))
            
            // --- FRESH TOKEN PROTOCOL ---
            val emailToken = authManager.getFreshAccessToken() ?: accessToken

            if (!alertEmail.isNullOrBlank()) {
                val dateStr = SimpleDateFormat("MMMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(session.startTime))
                val finalBody = GmailNotifier.buildReadyBody(sessionTag, dateStr, shareUrl, retentionDays)
                
                val emailList = alertEmail.split(",").map { it.trim() }.filter { it.isNotBlank() }
                android.util.Log.d("UploadWorker", "Attempting email delivery to ${emailList.size} recipients: $alertEmail")
                
                var successCount = 0
                var permanentErrorCount = 0
                var lastError: String? = null

                for (recipient in emailList) {
                    try {
                        val sendResult = GmailNotifier.send(applicationContext, emailToken, recipient, "SeenMyPickle Alert: New Footage for $sessionTag", finalBody)
                        if (sendResult.success) {
                            successCount++
                        } else {
                            lastError = sendResult.error
                            if (sendResult.isPermanent) {
                                permanentErrorCount++
                                android.util.Log.w("UploadWorker", "Permanent email error for recipient '$recipient': ${sendResult.error}")
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("UploadWorker", "Email system error for $recipient: ${e.message}")
                        lastError = e.message
                    }
                }

                if (successCount > 0 || permanentErrorCount == emailList.size) {
                    val statusMsg = if (successCount == emailList.size) "READY_SENT" else "READY_EMAIL_FAILED"
                    repository.updateSession(session.copy(
                        notificationStatus = statusMsg,
                        gDriveUrl = shareUrl,
                        status = RecordingStatus.COMPLETED
                    ))
                    repository.markCompleted(sessionId, shareUrl)
                    updateCloudStatusWithReplay(session.filename, shareUrl)
                    
                    if (successCount < emailList.size) {
                        android.util.Log.w("UploadWorker", "Completed session $sessionId with email issues ($successCount/${emailList.size} sent): $lastError")
                    }
                } else {
                    val retryMsg = "Email network error (Retrying...)"
                    repository.updateProgress(sessionId, 0.90f, retryMsg)
                    return Result.retry()
                }
            } else {
                repository.markCompleted(sessionId, shareUrl)
                updateCloudStatusWithReplay(session.filename, shareUrl)
            }

            repository.updateProgress(sessionId, 1.0f, "Complete")
            
            // Cleanup
            val cleanupDir = mp4File.parentFile
            if (cleanupDir != null && mp4File.exists() && mp4File.length() > 1024) {
                val baseName = mp4File.name.replace(".mp4", "")
                cleanupDir.listFiles { _, name ->
                    name.startsWith(baseName) && (name.endsWith(".ts") || name.endsWith(".part"))
                }?.forEach { it.delete() }
            }
            
            return Result.success()

        } catch (e: Exception) {
            val fatalMsg = "Fatal Crash: ${e.localizedMessage ?: "Unknown"}"
            android.util.Log.e("UploadWorker", "Fatal worker crash for session $sessionId", e)
            repository.updateProgress(sessionId, 0f, fatalMsg)
            return Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo("Initializing upload...", 0)
    }

    private fun updateCloudStatusWithReplay(filename: String, shareUrl: String) {
        val app = applicationContext as PBCamApplication
        val deviceId = com.pbcam.app.data.SecurityUtils.getDeviceId(applicationContext)
        if (deviceId.isBlank()) return

        try {
            val dbUrl = "https://seemypickle-default-rtdb.asia-southeast1.firebasedatabase.app/"
            val db = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl).getReference("live_status/$deviceId")
            db.child("status").setValue("IDLE")
            db.child("lastRecordingUrl").setValue(shareUrl)
            db.child("timestamp").setValue(System.currentTimeMillis())
        } catch (e: Exception) {
            android.util.Log.e("UploadWorker", "Failed to sync replay to cloud", e)
        }
    }

    private fun createForegroundInfo(contentText: String, progress: Int): ForegroundInfo {
        val channelId = "pbcam_worker"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Background Processing", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val exitIntent = android.content.Intent(applicationContext, com.pbcam.app.service.RecordingService::class.java).apply {
            action = com.pbcam.app.service.RecordingService.ACTION_EXIT_APP
        }
        val pendingExit = android.app.PendingIntent.getService(
            applicationContext, 2, exitIntent, 
            android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("SeenMyPickle: Uploading Footage")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false) // Filling progress bar
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "EXIT APP", pendingExit)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(2002, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            ForegroundInfo(2002, notification)
        }
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"
        const val PROGRESS_VAL = "progress_val"
        const val PROGRESS_MSG = "progress_msg"
    }
}
