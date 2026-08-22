package com.pbcam.app.data

enum class RecordingState {
    IDLE,
    RECORDING,
    PAUSED,
    PAUSED_LOW_STORAGE,
    PAUSED_OVERHEATING
}

enum class PreviewState {
    IDLE,
    PLAYING
}
