package com.pbcam.app

import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.pbcam.app.auth.GoogleAuthManager
import com.pbcam.app.data.SecurityUtils
import com.pbcam.app.service.RecordingService
import com.pbcam.app.ui.DashboardScreen
import com.pbcam.app.ui.SetupWizardScreen
import com.pbcam.app.ui.theme.PBCamTheme
import com.pbcam.app.ui.viewmodel.DashboardViewModel
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: DashboardViewModel by viewModels()
    private val authManager by lazy { GoogleAuthManager(applicationContext) }

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            authManager.handleSignInResult(result.data)
            viewModel.refresh()
        }
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        uri?.let { viewModel.exportHistoryToUri(it) }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.refresh()
            // Start the recording service automatically once permissions are granted
            RecordingService.start(this)
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        try {
            // --- BATTERY OPTIMIZATION REQUEST ---
            requestIgnoreBatteryOptimizations()

            // 1. Pre-flight permission check
            requestCorePermissions()
            
            // 3. UI Construction
            setContent {
                val uiState by viewModel.uiState.collectAsState()
                val configuration = LocalConfiguration.current
                val isTablet = configuration.smallestScreenWidthDp >= 600

                // DYNAMIC ORIENTATION: Code Bible Rule 3.4 Adjustment
                LaunchedEffect(uiState.isSetupComplete) {
                    requestedOrientation = if (uiState.isSetupComplete || isTablet) {
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }

                PBCamTheme(uiState.themeMode) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        // --- GLOBAL SLEEP PREVENTION (User Request) ---
                        // Keep screen ON as long as the app is in the foreground
                        SideEffect {
                            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            android.util.Log.d("PBCamSleep", "Enforcing Global FLAG_KEEP_SCREEN_ON")
                        }

                        if (!uiState.isSetupComplete) {
                            SetupWizardScreen(
                                viewModel = viewModel,
                                isAuthenticated = uiState.isAuthenticated,
                                onSignIn = { signInLauncher.launch(authManager.getSignInIntent()) }
                            )
                        } else {
                            DashboardScreen(
                                viewModel = viewModel,
                                onSignIn = { signInLauncher.launch(authManager.getSignInIntent()) },
                                onSignOut = {
                                    authManager.signOut(this) { viewModel.refresh() }
                                },
                                onStartRecording = {
                                    val combinedList = uiState.selectedEmails.toMutableList()
                                    if (uiState.isEmailValid && !combinedList.contains(uiState.alertEmail)) {
                                        if (combinedList.size < 5) {
                                            combinedList.add(uiState.alertEmail)
                                        }
                                    }
                                    val emails = combinedList.joinToString(",")

                                    val intent = Intent(this, RecordingService::class.java).apply {
                                        action = RecordingService.ACTION_START
                                        putExtra("email", emails)
                                    }
                                    startService(intent)
                                    viewModel.startPreview() // Force preview on match start
                                },
                                onStopRecording = {
                                    RecordingService.stop(this)
                                },
                                onExportHistory = exportLauncher::launch
                            )
                        }
                    }
                }
            }
            
            // 4. Background non-critical logging
            lifecycleScope.launch(Dispatchers.IO) {
                logAppSignature()
            }

        } catch (e: Exception) {
            android.util.Log.e("SeeMyPickleFatal", "FATAL STARTUP ERROR: ${e.message}", e)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        hideNavigationBarOnly()
        viewModel.refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavigationBarOnly()
    }

    private fun hideNavigationBarOnly() {
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Specifically hide ONLY navigation bars, keep status bars visible
        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
    }

    private fun requestCorePermissions() {
        val permissions = mutableListOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun requestIgnoreBatteryOptimizations() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:$packageName".toUri()
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("PBCamBattery", "Failed to request battery optimization bypass: ${e.message}")
        }
    }

    private fun logAppSignature() {
        try {
            val deviceId = SecurityUtils.getDeviceId(this)
            android.util.Log.d("PBCamHardware", "DEVICE ID: $deviceId")
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            }
            
            android.util.Log.d("PBCamSecurity", "Build integrity check passed")
        } catch (e: Exception) {
            android.util.Log.e("PBCamSecurity", "Signature check failed: ${e.message}")
        }
    }
}
