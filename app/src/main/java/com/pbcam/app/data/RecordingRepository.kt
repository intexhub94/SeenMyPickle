package com.pbcam.app.data

import com.pbcam.app.data.db.PBCamDatabase
import com.pbcam.app.data.db.RecordingSession
import com.pbcam.app.data.db.RecordingSessionDao
import com.pbcam.app.data.db.RecordingStatus
import kotlinx.coroutines.flow.Flow

class RecordingRepository(database: PBCamDatabase) {
    private val dao: RecordingSessionDao = database.recordingSessionDao()

    fun observeSessions(): Flow<List<RecordingSession>> = dao.observeAll()

    suspend fun insertSession(session: RecordingSession): Long = dao.insert(session)

    suspend fun updateSession(session: RecordingSession) = dao.update(session)
    
    suspend fun deleteSession(session: RecordingSession) = dao.delete(session)

    suspend fun getSession(id: Long): RecordingSession? = dao.getById(id)

    suspend fun getSessionByFilename(filename: String): RecordingSession? = dao.getByFilename(filename)

    suspend fun getPendingUploads(): List<RecordingSession> = dao.getPendingUploads()

    suspend fun markPendingUpload(sessionId: Long) {
        dao.updateUploadResult(sessionId, RecordingStatus.PENDING_UPLOAD, null)
    }

    suspend fun markProcessing(sessionId: Long) {
        dao.updateUploadResult(sessionId, RecordingStatus.PROCESSING, null)
    }

    suspend fun markUploading(sessionId: Long) {
        dao.updateUploadResult(sessionId, RecordingStatus.UPLOADING, null)
    }

    suspend fun markSending(sessionId: Long) {
        dao.updateUploadResult(sessionId, RecordingStatus.SENDING_EMAIL, null)
    }

    suspend fun markCompleted(sessionId: Long, url: String) {
        dao.updateUploadResult(sessionId, RecordingStatus.COMPLETED, url)
    }

    suspend fun markFailed(sessionId: Long, error: String? = null) {
        dao.markError(sessionId, RecordingStatus.FAILED, error ?: "Internal pipeline failure (check system logs)")
    }

    suspend fun cleanInterruptedSessions() {
        dao.cleanInterruptedSessions()
    }

    suspend fun clearAllSessions() {
        dao.deleteAll()
    }

    suspend fun clearOldLogs() {
        dao.deleteCompletedAndFailed()
    }

    suspend fun purgeOldSessions(days: Int) {
        val threshold = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        dao.deleteSessionsOlderThan(threshold)
    }

    suspend fun failStuckSessions() {
        // Mark sessions stuck for more than 12 hours as failed
        val threshold = System.currentTimeMillis() - (12 * 60 * 60 * 1000L)
        dao.failStuckSessions(threshold)
    }

    suspend fun updateProgress(sessionId: Long, progress: Float, message: String?) {
        dao.updateProgress(sessionId, progress, message)
    }
}
