package com.pbcam.app.data

import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object CameraStateManager {
    private val _surfaceProvider = MutableStateFlow<Preview.SurfaceProvider?>(null)
    val surfaceProvider: StateFlow<Preview.SurfaceProvider?> = _surfaceProvider

    // Track if the service is currently holding the camera for recording
    private val _isServiceHoldingCamera = MutableStateFlow(false)
    val isServiceHoldingCamera: StateFlow<Boolean> = _isServiceHoldingCamera

    fun setSurfaceProvider(provider: Preview.SurfaceProvider?) {
        _surfaceProvider.value = provider
    }

    fun setServiceHoldingCamera(holding: Boolean) {
        _isServiceHoldingCamera.value = holding
    }
}
