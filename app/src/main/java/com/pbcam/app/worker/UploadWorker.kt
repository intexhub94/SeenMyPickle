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

        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            android.util.Log.e("UploadWorker", "Failed to set foreground state", e)
        }

        val app = applicationContext as PBCamApplication
        val repository = app.recordingRepository
        val settings = app.settingsStore
        val authManager = GoogleAuthManager(applicationContext)

        val session = repository.getSession(sessionId) ?: run {
            android.util.Log.e("UploadWorker", "Session $sessionId not found in DB")
            return Result.failure()
        }
        
        if (session.status == RecordingStatus.COMPLETED && session.gDriveUrl != null) {
            return Result.success()
        }

        val mp4File = File(session.filename)
        if (!mp4File.exists() || mp4File.length() < 1024) {
            android.util.Log.e("UploadWorker", "Finalized file missing or invalid for session $sessionId")
            repository.markFailed(sessionId, "Finalized video file missing (Processing error)")
            return Result.failure()
        }

        val accessToken = authManager.getAccessToken() ?: run {
            android.util.Log.w("UploadWorker", "No access token available")
            return Result.retry()
        }

        val sessionTag = session.courtTag ?: settings.courtTag
        val alertEmail = session.targetEmail ?: settings.alertEmail
        val retentionDays = settings.retentionDays

        repository.markUploading(sessionId)
        val initialMsg = "Starting upload..."
        repository.updateProgress(sessionId, 0.05f, initialMsg)
        setProgress(workDataOf(KEY_SESSION_ID to sessionId, PROGRESS_VAL to 0.05f, PROGRESS_MSG to initialMsg))
        setForeground(createForegroundInfo("Uploading footage...", 5))

        val shareUrl = DriveUploader.upload(accessToken, mp4File, sessionTag) { progress ->
            val percent = (progress * 100).toInt()
            val msg = "Uploading: $percent%"
            
            // ATOMIC PROGRESS UPDATE: Use runBlocking to ensure database integrity 
            // during network-heavy upload.
            kotlinx.coroutines.runBlocking {
                repository.updateProgress(sessionId, progress, msg)
                setProgress(workDataOf(KEY_SESSION_ID to sessionId, PROGRESS_VAL to progress, PROGRESS_MSG to msg))
            }
            
            if (percent % 2 == 0) {
                setForegroundAsync(createForegroundInfo(msg, percent))
            }
        } ?: run {
            val errorMsg = "Drive upload failed for session $sessionId"
            android.util.Log.e("UploadWorker", errorMsg)
            // DO NOT mark as failed if we are retrying - keeps the UI progress active
            return Result.retry()
        }

        val emailMsg = "Sending final email..."
        // ATOMIC PROGRESS UPDATE: Mark as sending email in DB
        kotlinx.coroutines.runBlocking {
            repository.markSending(sessionId)
            repository.updateProgress(sessionId, 0.95f, emailMsg)
            setProgress(workDataOf(KEY_SESSION_ID to sessionId, PROGRESS_VAL to 0.95f, PROGRESS_MSG to emailMsg))
        }
        setForegroundAsync(createForegroundInfo("Sending email notification...", 95))
        
        // --- FRESH TOKEN PROTOCOL: Refresh token before email send to prevent expiration during long uploads ---
        val emailToken = authManager.getAccessToken() ?: accessToken

        if (!alertEmail.isNullOrBlank()) {
            val dateStr = SimpleDateFormat("MMMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(session.startTime))
            val finalBody = GmailNotifier.buildReadyBody(sessionTag, dateStr, shareUrl, retentionDays)
            
            val emailList = alertEmail.split(",").map { it.trim() }.filter { it.isNotBlank() }
            android.util.Log.d("UploadWorker", "Attempting email delivery to ${emailList.size} recipients: $alertEmail")
            
            var allSuccess = true
            
            for (recipient in emailList) {
                try {
                    android.util.Log.d("UploadWorker", "Sending email to: $recipient")
                    if (!GmailNotifier.send(applicationContext, emailToken, recipient, "SeenMyPickle Alert: New Footage for $sessionTag", finalBody)) {
                        android.util.Log.e("UploadWorker", "Gmail API delivery failure for $recipient")
                        allSuccess = false
                    } else {
                        android.util.Log.i("UploadWorker", "Successfully sent email to $recipient")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("UploadWorker", "System error sending email to $recipient: ${e.message}", e)
                    allSuccess = false
                }
            }

            if (allSuccess) {
                repository.updateSession(session.copy(notificationStatus = "READY_SENT", gDriveUrl = shareUrl, status = RecordingStatus.COMPLETED))
                repository.markCompleted(sessionId, shareUrl)
                updateCloudStatusWithReplay(session.filename, shareUrl)
            } else {
                val retryMsg = "One or more emails failed to send. Retrying..."
                android.util.Log.w("UploadWorker", retryMsg)
                repository.updateProgress(sessionId, 0.90f, retryMsg)
                setProgress(workDataOf(KEY_SESSION_ID to sessionId, PROGRESS_VAL to 0.90f, PROGRESS_MSG to retryMsg))
                return Result.retry()
            }
        } else {
            repository.markCompleted(sessionId, shareUrl)
            updateCloudStatusWithReplay(session.filename, shareUrl)
        }

        repository.updateProgress(sessionId, 1.0f, "Complete")
        setProgress(workDataOf(KEY_SESSION_ID to sessionId, PROGRESS_VAL to 1.0f, PROGRESS_MSG to "Complete"))
        
        // Cleanup TS parts only AFTER successful upload validation
        val cleanupDir = mp4File.parentFile
        if (cleanupDir != null && mp4File.exists() && mp4File.length() > 1024) {
            val baseName = mp4File.name.replace(".mp4", "")
            cleanupDir.listFiles { _, name ->
                name.startsWith(baseName) && (name.endsWith(".ts") || name.endsWith(".part"))
            }?.forEach { it.delete() }
        }
        
        return Result.success()
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
