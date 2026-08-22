package com.pbcam.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

import com.pbcam.app.data.CameraSource

@Entity(
    tableName = "recording_sessions",
    indices = [Index("startTime"), Index("status")]
)
data class RecordingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filename: String,
    val startTime: Long,
    val endTime: Long? = null,
    val status: RecordingStatus,
    val gDriveUrl: String? = null,
    val targetEmail: String? = null,
    val courtTag: String? = null,
    val errorMessage: String? = null,
    val notificationStatus: String = "IDLE", // IDLE, PROCESSING_SENT, READY_SENT, FAILED
    val source: CameraSource = CameraSource.RTSP,
    val progressValue: Float = 0f,
    val progressMessage: String? = null
)
