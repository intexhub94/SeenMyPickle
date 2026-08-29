package com.pbcam.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pbcam.app.PBCamApplication
import com.pbcam.app.auth.GoogleAuthManager
import com.pbcam.app.cloud.DriveUploader
import com.pbcam.app.data.db.RecordingStatus
import com.pbcam.app.service.RecordingService
import java.util.concurrent.TimeUnit

class MaintenanceWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PBCamApplication
        val repository = app.recordingRepository
        val retentionDays = app.settingsStore.retentionDays
        
        // 1. Purge Old Session Logs (Sync with Drive retention)
        repository.purgeOldSessions(retentionDays)

        // 2. Clean local video files (2-Hour Retention Policy for court rentals)
        val localStorageRetentionHours = app.settingsStore.localStorageRetentionHours
        val localCutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(localStorageRetentionHours.toLong())
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        val safetyCutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14)

        val recordingsDir = RecordingService.getRecordingsDir(applicationContext)
        if (recordingsDir.exists()) {
            recordingsDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val isOld = file.lastModified() < cutoff
                    val isExtraOld = file.lastModified() < safetyCutoff
                    val session = repository.getSessionByFilename(file.absolutePath)
                    val isUploaded = session?.status == RecordingStatus.COMPLETED
                    val isOldLocal = file.lastModified() < localCutoff || (session != null && (System.currentTimeMillis() - session.startTime) > TimeUnit.HOURS.toMillis(localStorageRetentionHours.toLong()))

                    // Logic: Delete if older than 2 hours and uploaded, OR if extra old (safety net)
                    if (isExtraOld || (isOldLocal && isUploaded) || (isOld && isUploaded)) {
                        file.delete()
                    }
                }
            }
        }

        // 2. Clean Drive files (Retention Policy)
        val authManager = GoogleAuthManager(applicationContext)
        authManager.getAccessToken()?.let { token ->
            try {
                DriveUploader.cleanupOldFiles(token, retentionDays)
            } catch (e: Exception) {
                android.util.Log.e("MaintenanceWorker", "Drive cleanup failed", e)
            }
        }

        return Result.success()
    }

    companion object {
        // RETENTION_MS removed in favor of dynamic settings
    }
}
