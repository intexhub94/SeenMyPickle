package com.pbcam.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object RecordingStateManager {
    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState = _recordingState.asStateFlow()

    fun updateState(state: RecordingState) {
        if (_recordingState.value != state) {
            android.util.Log.d("RecordingStateManager", "State Transition: ${_recordingState.value} -> $state")
            _recordingState.value = state
        }
    }
}
