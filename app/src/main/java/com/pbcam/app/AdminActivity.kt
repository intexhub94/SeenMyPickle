package com.pbcam.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pbcam.app.auth.GoogleAuthManager
import com.pbcam.app.ui.theme.PBCamTheme
import com.pbcam.app.ui.viewmodel.DashboardViewModel

class AdminActivity : ComponentActivity() {
    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var authManager: GoogleAuthManager

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        android.util.Log.d("GoogleAuth", "Result received in AdminActivity: resultCode=${result.resultCode}")
        authManager.handleSignInResult(result.data)
        viewModel.refresh()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        authManager = GoogleAuthManager(this)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            var localRtsp by remember(uiState.rtspUrl) { mutableStateOf(uiState.rtspUrl) }
            var localSubRtsp by remember(uiState.rtspSubUrl) { mutableStateOf(uiState.rtspSubUrl) }
            var localCourt by remember(uiState.courtTag) { mutableStateOf(uiState.courtTag) }

            PBCamTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Admin Settings") }
                        )
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .padding(16.dp)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("RTSP Configuration", style = MaterialTheme.typography.titleLarge)
                        
                        OutlinedTextField(
                            value = localRtsp,
                            onValueChange = { localRtsp = it },
                            label = { Text("Camera RTSP URL") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        OutlinedTextField(
                            value = localCourt,
                            onValueChange = { localCourt = it },
                            label = { Text("Court Identifier") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Button(
                            onClick = {
                                viewModel.updateRtspUrl(localRtsp)
                                viewModel.updateCourtTag(localCourt)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Configuration")
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Text("Google Authentication", style = MaterialTheme.typography.titleLarge)
                        
                        if (uiState.isAuthenticated) {
                            Text("Status: Authenticated", color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text("Status: Not Authenticated", color = MaterialTheme.colorScheme.error)
                            Text("Authentication is required for Google Drive uploads.", style = MaterialTheme.typography.bodySmall)
                        }
                        
                        Button(onClick = {
                            if (uiState.isAuthenticated) {
                                authManager.signOut(this@AdminActivity) { viewModel.refresh() }
                            } else {
                                signInLauncher.launch(authManager.getSignInIntent())
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (uiState.isAuthenticated) "Sign Out" else "Sign In with Google")
                        }
                        
                        Text(
                            "Note: RTSP changes are applied to the next recording session.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        
                        Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Close Admin")
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}
