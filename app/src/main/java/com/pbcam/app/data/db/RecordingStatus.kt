package com.pbcam.app.data.db

enum class RecordingStatus {
    RECORDING,
    PAUSED,
    PENDING_UPLOAD,
    PROCESSING, // Explicit state for video concatenation
    UPLOADING,
    SENDING_EMAIL,
    COMPLETED,
    FAILED
}
