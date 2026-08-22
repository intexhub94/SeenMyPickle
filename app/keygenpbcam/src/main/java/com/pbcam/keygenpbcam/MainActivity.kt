package com.pbcam.keygenpbcam

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.pbcam.keygenpbcam.ui.theme.PBCamTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminAuthViewModel : ViewModel() {
    private val _isFirebaseAuthed = MutableStateFlow(AdminCloudManager.isAuthenticated())
    val isFirebaseAuthed = _isFirebaseAuthed.asStateFlow()

    fun updateAuthState() {
        _isFirebaseAuthed.value = AdminCloudManager.isAuthenticated()
    }
}

class MainActivity : FragmentActivity() {
    private var isAuthorized by mutableStateOf(false)
    private var hardwareError by mutableStateOf<String?>(null)
    private val authViewModel: AdminAuthViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!AdminSecurityUtils.verifyAdminDevice(this)) {
            hardwareError = "UNAUTHORIZED HARDWARE: This app is locked to the administrator's device ID."
        } else {
            showBiometricPrompt()
        }

        setContent {
            PBCamTheme {
                val isConnected by AdminCloudManager.observeConnectionState().collectAsStateWithLifecycle(false)
                val rawCloudData by AdminCloudManager.observeRawData().collectAsStateWithLifecycle("Awaiting first sync...")
                val isFirebaseAuthed by authViewModel.isFirebaseAuthed.collectAsStateWithLifecycle()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("SEENMYPICKLE ADMIN", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
                            actions = {
                                IconButton(onClick = { 
                                    AdminCloudManager.signOut()
                                    authViewModel.updateAuthState()
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Force Re-auth")
                                }
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp)
                                        .size(14.dp)
                                        .background(
                                            color = if (isConnected) Color(0xFF2E7D32) else Color.Red,
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                )
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        if (hardwareError != null) {
                            HardwareLockScreen(hardwareError!!)
                        } else if (!isAuthorized) {
                            BiometricLockScreen(onRetry = { showBiometricPrompt() })
                        } else if (!isFirebaseAuthed) {
                            FirebaseLoginScreen(onAuthSuccess = { authViewModel.updateAuthState() })
                        } else {
                            AdminDashboard(rawCloudData = rawCloudData)
                        }
                    }
                }
            }
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isAuthorized = true
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        Toast.makeText(applicationContext, "Auth Error: $errString", Toast.LENGTH_SHORT).show()
                    }
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Admin Authentication")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    private fun shareKey(key: String, deviceId: String) {
        val message = "🔑 SeenMyPickle License\n\nDevice ID: $deviceId\nKey: $key\n\nEnter in Step 0."
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "SeenMyPickle License Key")
            putExtra(Intent.EXTRA_TEXT, message)
        }
        startActivity(Intent.createChooser(intent, "Share License Key"))
    }

    @Composable
    fun FirebaseLoginScreen(onAuthSuccess: () -> Unit) {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            @Suppress("DEPRECATION")
            Text("Cloud Authentication", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Login to resolve Permission Denied", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            
            Spacer(Modifier.height(24.dp))
            
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Admin Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(Modifier.height(8.dp))
            
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            
            if (errorMessage != null) {
                Text(errorMessage!!, color = Color.Red, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
            }
            
            Spacer(Modifier.height(24.dp))
            
            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    AdminCloudManager.signIn(email, password) { success, error ->
                        isLoading = false
                        if (success) {
                            onAuthSuccess()
                        } else {
                            errorMessage = error
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !isLoading && email.isNotEmpty() && password.isNotEmpty()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text("LOGIN TO CLOUD")
                }
            }
        }
    }

    @Composable
    fun AdminDashboard(rawCloudData: String) {
        var deviceId by remember { mutableStateOf("") }
        var generatedKey by remember { mutableStateOf("") }
        var clientEmail by remember { mutableStateOf("") }
        var showNameDialog by remember { mutableStateOf(false) }
        
        // Validity Period State
        val validityOptions = listOf("7 Days", "30 Days", "1 Year", "Lifetime", "Manual")
        var selectedValidity by remember { mutableStateOf("Lifetime") }
        var manualDays by remember { mutableStateOf("365") }

        val licenses by AdminCloudManager.observeLicenses().collectAsStateWithLifecycle(emptyList())
        val clipboardManager = LocalClipboardManager.current
        var activeTab by remember { mutableIntStateOf(0) }

        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = activeTab) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("Generator") })
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("Users (${licenses.size})") })
                Tab(selected = activeTab == 2, onClick = { activeTab = 2 }, text = { Text("Cloud Terminal") })
            }

            when (activeTab) {
                0 -> GeneratorTab(
                    deviceId = deviceId, 
                    onDeviceIdChange = { deviceId = it; generatedKey = "" }, 
                    generatedKey = generatedKey, 
                    validityOptions = validityOptions,
                    selectedValidity = selectedValidity,
                    onValidityChange = { selectedValidity = it },
                    manualDays = manualDays,
                    onManualDaysChange = { manualDays = it },
                    onGenerate = { showNameDialog = true }, 
                    onShare = { k, d -> shareKey(k, d) }, 
                    clipboardManager = clipboardManager
                )
                1 -> LicenseList(licenses)
                2 -> CloudTerminalTab(rawCloudData)
            }
        }

        if (showNameDialog) {
            AlertDialog(
                onDismissRequest = { showNameDialog = false },
                title = { Text("License Assignment") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Assign Device ID: $deviceId", style = MaterialTheme.typography.labelMedium)
                        OutlinedTextField(
                            value = clientEmail, 
                            onValueChange = { clientEmail = it }, 
                            label = { Text("Client Email Address") }, 
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
                        )
                        if (clientEmail.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(clientEmail).matches()) {
                            Text("Invalid email format", color = Color.Red, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                confirmButton = { Button(
                    enabled = android.util.Patterns.EMAIL_ADDRESS.matcher(clientEmail).matches(),
                    onClick = {
                    val days = when (selectedValidity) {
                        "7 Days" -> 7L
                        "30 Days" -> 30L
                        "1 Year" -> 365L
                        "Manual" -> manualDays.toLongOrNull() ?: 365L
                        else -> -1L // Lifetime
                    }
                    
                    val expiryTime = if (days == -1L) Long.MAX_VALUE else {
                        System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L)
                    }

                    generatedKey = AdminSecurityUtils.generateSerialKey(deviceId)
                    AdminCloudManager.registerLicense(deviceId, clientEmail, generatedKey, expiryTime)
                    showNameDialog = false
                }) { Text("Register") } }
            )
        }
    }
}

@Composable
fun CloudTerminalTab(rawData: String) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).background(Color.Black, RoundedCornerShape(8.dp)).padding(16.dp)) {
        @Suppress("DEPRECATION")
        Text("DATABASE RAW DUMP", color = Color.Green, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(8.dp))
        @Suppress("DEPRECATION")
        Text(text = rawData, color = Color.Green, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}

@Composable
fun GeneratorTab(
    deviceId: String, 
    onDeviceIdChange: (String) -> Unit, 
    generatedKey: String, 
    validityOptions: List<String>,
    selectedValidity: String,
    onValidityChange: (String) -> Unit,
    manualDays: String,
    onManualDaysChange: (String) -> Unit,
    onGenerate: () -> Unit, 
    onShare: (String, String) -> Unit, 
    clipboardManager: androidx.compose.ui.platform.ClipboardManager
) {
    var expanded by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Default.VpnKey, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        
        OutlinedTextField(
            value = deviceId, 
            onValueChange = { onDeviceIdChange(it.uppercase()) }, 
            label = { Text("Device ID") }, 
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { showScanner = true }) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Device QR")
                }
            }
        )

        // Validity Selection
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Validity: $selectedValidity")
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.fillMaxWidth(0.9f)) {
                validityOptions.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { 
                        onValidityChange(option)
                        expanded = false 
                    })
                }
            }
        }

        if (selectedValidity == "Manual") {
            OutlinedTextField(
                value = manualDays, 
                onValueChange = { if (it.all { char -> char.isDigit() }) onManualDaysChange(it) }, 
                label = { Text("Enter Days") }, 
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            )
        }

        Button(onClick = onGenerate, enabled = deviceId.length >= 4, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("GENERATE & SYNC") }

        if (generatedKey.isNotEmpty()) {
            GeneratedKeyCard(generatedKey, deviceId, onShare, clipboardManager)
        }
    }

    if (showScanner) {
        AdminQrScannerDialog(
            onResult = { scannedId ->
                onDeviceIdChange(scannedId.uppercase())
                showScanner = false
            },
            onDismiss = { showScanner = false }
        )
    }
}

@Composable
fun AdminQrScannerDialog(onResult: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan Device ID QR") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val executor = ContextCompat.getMainExecutor(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = androidx.camera.core.Preview.Builder().build().apply {
                                setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val scanner = BarcodeScanning.getClient(
                                BarcodeScannerOptions.Builder()
                                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                    .build()
                            )

                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build().apply {
                                    setAnalyzer(executor) { imageProxy ->
                                        @OptIn(androidx.camera.core.ExperimentalGetImage::class)
                                        val mediaImage = imageProxy.image
                                        if (mediaImage != null) {
                                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                            scanner.process(image)
                                                .addOnSuccessListener { barcodes ->
                                                    for (barcode in barcodes) {
                                                        barcode.rawValue?.let { onResult(it) }
                                                    }
                                                }
                                                .addOnCompleteListener { imageProxy.close() }
                                        } else {
                                            imageProxy.close()
                                        }
                                    }
                                }

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                            } catch (e: Exception) {
                                android.util.Log.e("AdminQrScanner", "Binding failed", e)
                            }
                        }, executor)
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        confirmButton = { },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        }
    )
}

@Composable
fun GeneratedKeyCard(key: String, deviceId: String, onShare: (String, String) -> Unit, clipboard: androidx.compose.ui.platform.ClipboardManager) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("KEY: $key", fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { clipboard.setText(AnnotatedString(key)) }) { Text("Copy") }
                @Suppress("DEPRECATION")
                Button(onClick = { onShare(key, deviceId) }) { Text("Share") }
            }
        }
    }
}

@Composable
fun LicenseList(licenses: List<LicenseRecord>) {
    var licenseToDelete by remember { mutableStateOf<LicenseRecord?>(null) }

    if (licenses.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No Users Found in Cloud", color = Color.Gray) }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(licenses) { record ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(record.name.ifEmpty { "No Email Assigned" }, fontWeight = FontWeight.Bold)
                            Text("ID: ${record.deviceId}", style = MaterialTheme.typography.labelSmall)
                            
                            val lastSeen = formatLastSeen(record.lastCheckIn)
                            val isRecent = lastSeen == "Just now"
                            Text(
                                text = "Last Seen: $lastSeen", 
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isRecent) Color(0xFF2E7D32) else Color.Gray
                            )

                            @Suppress("DEPRECATION")
                            Text("Status: ${record.status.uppercase()}", color = if (record.status == "revoked") Color.Red else Color(0xFF2E7D32))
                        }
                        
                        Row {
                            IconButton(onClick = { if (record.status == "revoked") AdminCloudManager.reactivateLicense(record.deviceId) else AdminCloudManager.revokeLicense(record.deviceId) }) {
                                Icon(if (record.status == "revoked") Icons.Default.Refresh else Icons.Default.Block, null, tint = if (record.status == "revoked") Color.Green else Color.Red)
                            }
                            IconButton(onClick = { licenseToDelete = record }) {
                                Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (licenseToDelete != null) {
        val deleteId = licenseToDelete!!.deviceId
        val deleteName = licenseToDelete!!.name
        AlertDialog(
            onDismissRequest = { licenseToDelete = null },
            title = { Text("Delete License?") },
            text = { Text("Are you sure you want to permanently delete the license for ${if (deleteName.isNotEmpty()) deleteName else deleteId}? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        AdminCloudManager.deleteLicense(deleteId)
                        licenseToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("DELETE") }
            },
            dismissButton = {
                TextButton(onClick = { licenseToDelete = null }) { Text("CANCEL") }
            }
        )
    }
}

private fun formatLastSeen(lastCheckIn: Long?): String {
    if (lastCheckIn == null) return "Never"
    val diff = System.currentTimeMillis() - lastCheckIn
    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        else -> "${diff / 86400000}d ago"
    }
}

@Composable fun HardwareLockScreen(m: String) { Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = Color.Red); Text(m, textAlign = TextAlign.Center) } }
@Composable fun BiometricLockScreen(onRetry: () -> Unit) { Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text("Scan Fingerprint"); Button(onClick = { onRetry() }) { Text("Unlock") } } }
