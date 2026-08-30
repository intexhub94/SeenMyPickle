package com.pbcam.app.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

import androidx.work.OutOfQuotaPolicy

object WorkerScheduler {
    private const val UPLOAD_WORK_PREFIX = "upload_session_"
    private const val MAINTENANCE_WORK = "maintenance_cleanup"

    fun enqueueUpload(context: Context, sessionId: Long) {
        // STEP 1: Process Video (NO CONSTRAINTS - Always run immediately offline)
        val convertRequest = OneTimeWorkRequestBuilder<ConvertWorker>()
            .addTag(CONVERT_TAG)
            .setInputData(workDataOf(ConvertWorker.KEY_SESSION_ID to sessionId))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        // We no longer chain them here with .then() to avoid network constraints 
        // blocking the local conversion. ConvertWorker will trigger enqueueOnlyUpload on success.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "convert_$sessionId",
                ExistingWorkPolicy.REPLACE,
                convertRequest
            )
    }

    fun enqueueOnlyUpload(context: Context, sessionId: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .addTag(UPLOAD_TAG)
            .setInputData(workDataOf(UploadWorker.KEY_SESSION_ID to sessionId))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "$UPLOAD_WORK_PREFIX$sessionId",
                ExistingWorkPolicy.REPLACE,
                uploadRequest
            )

        val serverUploadRequest = OneTimeWorkRequestBuilder<ServerUploadWorker>()
            .addTag(PC_UPLOAD_TAG)
            .setInputData(workDataOf(ServerUploadWorker.KEY_SESSION_ID to sessionId))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "pc_upload_session_$sessionId",
                ExistingWorkPolicy.REPLACE,
                serverUploadRequest
            )
    }

    fun enqueueResend(context: Context, sessionId: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val resendRequest = OneTimeWorkRequestBuilder<ResendWorker>()
            .setInputData(workDataOf(ResendWorker.KEY_SESSION_ID to sessionId))
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(resendRequest)
    }

    const val CONVERT_TAG = "convert_task"
    const val UPLOAD_TAG = "upload_task"
    const val PC_UPLOAD_TAG = "pc_upload_task"

    fun scheduleMaintenance(context: Context) {
        val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MAINTENANCE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun runMaintenanceNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<MaintenanceWorker>()
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
