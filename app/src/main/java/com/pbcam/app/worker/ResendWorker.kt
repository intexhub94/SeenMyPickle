package com.pbcam.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pbcam.app.PBCamApplication
import com.pbcam.app.auth.GoogleAuthManager
import com.pbcam.app.cloud.GmailNotifier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ResendWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong(KEY_SESSION_ID, -1L)
        if (sessionId < 0) return Result.failure()

        val app = applicationContext as PBCamApplication
        val repository = app.recordingRepository
        val authManager = GoogleAuthManager(applicationContext)

        val session = repository.getSession(sessionId) ?: return Result.failure()
        val shareUrl = session.gDriveUrl ?: return Result.failure()
        val alertEmail = session.targetEmail ?: return Result.failure()

        val accessToken = authManager.getAccessToken() ?: return Result.retry()

        val dateStr = SimpleDateFormat("MMMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(session.startTime))
        val sessionTag = session.courtTag ?: "Unknown Court"
        val finalBody = GmailNotifier.buildReadyBody(sessionTag, dateStr, shareUrl)

        return try {
            val result = GmailNotifier.send(applicationContext, accessToken, alertEmail, "RE-SEND: SeenMyPickle Alert for $sessionTag", finalBody)
            if (result.success) {
                repository.updateSession(session.copy(notificationStatus = "READY_SENT"))
                Result.success()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            android.util.Log.e("ResendWorker", "Failed to resend email", e)
            Result.failure()
        }
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"
    }
}
