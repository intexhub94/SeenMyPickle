package com.pbcam.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pbcam.app.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.delay

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Ensure system checks start immediately
        viewModel.performSystemHealthCheck()

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // 1. FULL BACKGROUND ART
                Image(
                    painter = painterResource(id = R.drawable.splash_full),
                    contentDescription = "SeenMyPickle Splash",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // 2. SYSTEM DIAGNOSTICS OVERLAY (Bottom)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp) // Lowered to avoid blocking the motto
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(24.dp),
                            spotColor = Color.Black,
                            ambientColor = Color.Black
                        )
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 32.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "SYSTEM READINESS CHECK",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HealthIndicator(Icons.Default.Wifi, uiState.systemHealth.isNetworkReady, "Network")
                        HealthIndicator(Icons.Default.CameraAlt, uiState.systemHealth.isCameraReady, "Camera")
                        HealthIndicator(Icons.Default.AccountCircle, uiState.systemHealth.isGoogleReady, "Google")
                        HealthIndicator(Icons.Default.SdCard, uiState.systemHealth.isStorageReady, "Storage")
                    }
                }
            }

            LaunchedEffect(uiState.systemHealth) {
                // Wait for all critical systems to be checked
                val health = uiState.systemHealth
                val allChecked = health.isNetworkReady != null && 
                                 health.isCameraReady != null && 
                                 health.isGoogleReady != null && 
                                 health.isStorageReady != null
                
                if (allChecked) {
                    delay(2500) // Minimum branding time for professional feel
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                }
            }
        }
    }
}

@Composable
fun HealthIndicator(icon: ImageVector, isReady: Boolean?, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = when (isReady) {
                    true -> Color(0xFF99FF00) // PickleGreen
                    false -> Color.Red
                    null -> Color.White.copy(alpha = 0.2f)
                },
                modifier = Modifier.size(28.dp)
            )
            if (isReady == false) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color.Red),
                    contentAlignment = Alignment.Center
                ) {
                    Text("!", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isReady == true) Color(0xFF99FF00) else Color.White.copy(alpha = 0.5f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
