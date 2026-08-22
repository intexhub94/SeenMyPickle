package com.pbcam.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingSessionDao {
    @Insert
    suspend fun insert(session: RecordingSession): Long

    @Update
    suspend fun update(session: RecordingSession)

    @androidx.room.Delete
    suspend fun delete(session: RecordingSession)

    @Query("SELECT * FROM recording_sessions ORDER BY startTime DESC")
    fun observeAll(): Flow<List<RecordingSession>>

    @Query("SELECT * FROM recording_sessions WHERE id = :id")
    suspend fun getById(id: Long): RecordingSession?

    @Query("SELECT * FROM recording_sessions WHERE filename = :filename LIMIT 1")
    suspend fun getByFilename(filename: String): RecordingSession?

    @Query("SELECT * FROM recording_sessions WHERE status = :status ORDER BY startTime ASC")
    suspend fun getByStatus(status: RecordingStatus): List<RecordingSession>

    @androidx.room.Transaction
    @Query("SELECT * FROM recording_sessions WHERE status IN ('PENDING_UPLOAD', 'PROCESSING', 'UPLOADING')")
    suspend fun getPendingUploads(): List<RecordingSession>

    @Query("UPDATE recording_sessions SET status = :status, gDriveUrl = :gDriveUrl WHERE id = :id")
    suspend fun updateUploadResult(id: Long, status: RecordingStatus, gDriveUrl: String?)

    @Query("UPDATE recording_sessions SET status = 'FAILED', errorMessage = 'Recording interrupted (App closed/crashed)' WHERE status = 'RECORDING' OR status = 'UPLOADING'")
    suspend fun cleanInterruptedSessions()

    @Query("UPDATE recording_sessions SET status = :status, errorMessage = :error WHERE id = :id")
    suspend fun markError(id: Long, status: RecordingStatus, error: String?)

    @Query("DELETE FROM recording_sessions")
    suspend fun deleteAll()

    @Query("DELETE FROM recording_sessions WHERE status IN ('COMPLETED', 'FAILED')")
    suspend fun deleteCompletedAndFailed()

    @Query("DELETE FROM recording_sessions WHERE startTime < :threshold AND status IN ('COMPLETED', 'FAILED')")
    suspend fun deleteSessionsOlderThan(threshold: Long)

    @Query("UPDATE recording_sessions SET status = 'FAILED', errorMessage = 'Session timed out (stuck in pipeline)' WHERE startTime < :threshold AND status IN ('PENDING_UPLOAD', 'PROCESSING', 'UPLOADING', 'SENDING_EMAIL')")
    suspend fun failStuckSessions(threshold: Long)

    @Query("UPDATE recording_sessions SET progressValue = :progress, progressMessage = :message WHERE id = :sessionId")
    suspend fun updateProgress(sessionId: Long, progress: Float, message: String?)
}
