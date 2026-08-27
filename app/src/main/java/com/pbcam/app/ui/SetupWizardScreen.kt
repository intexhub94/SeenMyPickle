package com.pbcam.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.pbcam.app.data.CameraSource
import com.pbcam.app.data.DiscoveredCamera
import com.pbcam.app.ui.viewmodel.DashboardViewModel

@androidx.camera.core.ExperimentalGetImage
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    viewModel: DashboardViewModel,
    isAuthenticated: Boolean,
    onSignIn: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var currentStep by remember { mutableIntStateOf(0) }
    var hasAgreedToDisclaimer by remember { mutableStateOf(false) }
    
    val showDisclaimer = uiState.isLicensed && !hasAgreedToDisclaimer
    
    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { },
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Important: Requirements & Privacy", style = MaterialTheme.typography.titleLarge)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Please read the following requirements carefully before proceeding with the setup of SeenMyPickle Smart Court:",
                        fontWeight = FontWeight.Bold
                    )
                    
                    DisclaimerPoint(
                        title = "Google Account & Storage",
                        desc = "A Google Account with an active Google Drive subscription is required to store and deliver match footage."
                    )
                    
                    DisclaimerPoint(
                        title = "Data Privacy",
                        desc = "Player email addresses are stored for delivery purposes. This data is visible ONLY to the licensed court owners."
                    )
                    
                    DisclaimerPoint(
                        title = "CCTV & Hardware",
                        desc = "A professional RTSP-capable camera is required (Dahua, Hikvision, etc.)."
                    )
                    
                    DisclaimerPoint(
                        title = "Camera Security",
                        desc = "For secure monitoring, your CCTV camera MUST be configured with a valid username and password."
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { 
                        hasAgreedToDisclaimer = true
                        if (currentStep == 0) currentStep = 1
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("I AGREE & CONTINUE") }
            }
        )
    }

    LaunchedEffect(uiState.isLicensed) {
        if (uiState.isLicensed && currentStep == 0) {
            // Logic handled by disclaimer bridge
        }
    }

    val totalSteps = 5
    var passcode by remember { mutableStateOf("") }
    var confirmPasscode by remember { mutableStateOf("") }
    var rtspUrl by remember { mutableStateOf("rtsp://") }
    var rtspSubUrl by remember { mutableStateOf("rtsp://") }
    var courtTag by remember { mutableStateOf("Court 1") }
    var cameraSource by remember { mutableStateOf(CameraSource.RTSP) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("SeenMyPickle Setup Wizard - Step $currentStep of $totalSteps") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AnimatedContent(targetState = currentStep, label = "StepAnimation") { step ->
                    when (step) {
                        0 -> ActivationStep(
                            deviceId = uiState.deviceId,
                            onActivate = viewModel::activateLicense,
                            isLicensed = uiState.isLicensed
                        )
                        1 -> PasscodeStep(passcode, confirmPasscode, { passcode = it }, { confirmPasscode = it })
                        2 -> SourceStep(cameraSource) { cameraSource = it }
                        3 -> {
                            if (cameraSource == CameraSource.RTSP) {
                                RtspStep(
                                    url = rtspUrl,
                                    subUrl = rtspSubUrl,
                                    tag = courtTag,
                                    onUrlChange = { rtspUrl = it },
                                    onSubUrlChange = { rtspSubUrl = it },
                                    onTagChange = { courtTag = it },
                                    isScanning = uiState.isScanning,
                                    discoveredCameras = uiState.discoveredCameras,
                                    scanMessage = uiState.scanMessage,
                                    onScan = viewModel::scanForCameras,
                                    onProbe = viewModel::probeManualIp
                                )
                            } else {
                                InternalTagStep(courtTag) { courtTag = it }
                            }
                        }
                        4 -> AuthStep(isAuthenticated, onSignIn)
                        5 -> SummaryStep(rtspUrl, rtspSubUrl, courtTag, isAuthenticated, cameraSource)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentStep > 0) {
                    OutlinedButton(onClick = { currentStep-- }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Back")
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                if (currentStep < totalSteps) {
                    Button(
                        onClick = { currentStep++ },
                        enabled = when (currentStep) {
                            0 -> uiState.isLicensed
                            1 -> passcode.length >= 4 && passcode == confirmPasscode
                            2 -> true
                            3 -> if (cameraSource == CameraSource.RTSP) rtspUrl.startsWith("rtsp://") && rtspUrl.length > 7 && rtspSubUrl.startsWith("rtsp://") else courtTag.isNotBlank()
                            4 -> true
                            else -> true
                        }
                    ) {
                        Text("Next")
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                    }
                } else {
                    Button(
                        onClick = { 
                            viewModel.completeSetup(passcode, rtspUrl, rtspSubUrl, courtTag, cameraSource) 
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        enabled = true
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Finish Setup")
                    }
                }
            }
        }
    }
}

@Composable
fun SourceStep(selected: CameraSource, onSelect: (CameraSource) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Select Camera Source", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Choose how SeenMyPickle will record footage.", style = MaterialTheme.typography.bodyMedium)

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceOption(
                title = "Internal Camera",
                desc = "Use this phone's built-in camera lens.",
                selected = selected == CameraSource.INTERNAL,
                onClick = { onSelect(CameraSource.INTERNAL) }
            )
            SourceOption(
                title = "RTSP IP Camera",
                desc = "Connect to a remote network security camera.",
                selected = selected == CameraSource.RTSP,
                onClick = { onSelect(CameraSource.RTSP) }
            )
        }
    }
}

@Composable
fun SourceOption(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth(),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun InternalTagStep(tag: String, onTagChange: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Name Your Court", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = tag,
            onValueChange = onTagChange,
            label = { Text("Court Name/Tag") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@Composable
fun PasscodeStep(passcode: String, confirm: String, onPasscodeChange: (String) -> Unit, onConfirmChange: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Set Admin Passcode", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        
        OutlinedTextField(
            value = passcode,
            onValueChange = { if (it.length <= 4 && it.all { it.isDigit() }) onPasscodeChange(it) },
            label = { Text("Enter 4-digit Passcode") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        OutlinedTextField(
            value = confirm,
            onValueChange = { if (it.length <= 4 && it.all { it.isDigit() }) onConfirmChange(it) },
            label = { Text("Confirm Passcode") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = confirm.isNotEmpty() && confirm != passcode
        )
    }
}

@Composable
fun RtspStep(
    url: String, 
    subUrl: String,
    tag: String, 
    onUrlChange: (String) -> Unit, 
    onSubUrlChange: (String) -> Unit,
    onTagChange: (String) -> Unit,
    isScanning: Boolean,
    discoveredCameras: List<DiscoveredCamera>,
    scanMessage: String?,
    onScan: () -> Unit,
    onProbe: (String) -> Unit
) {
    var selectedCameraIp by remember { mutableStateOf<String?>(null) }
    var showCredentialDialog by remember { mutableStateOf(false) }
    var showCameraInfo by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Configure Camera", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showCameraInfo = true }) {
                Icon(Icons.Default.Help, contentDescription = "Camera Info", tint = MaterialTheme.colorScheme.primary)
            }
        }
        
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text("Main Stream URL (Recording)") },
            placeholder = { Text("rtsp://admin:pass@IP/stream1") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = subUrl,
            onValueChange = onSubUrlChange,
            label = { Text("Sub Stream URL (Preview)") },
            placeholder = { Text("rtsp://admin:pass@IP/stream2") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = manualIp,
                onValueChange = { manualIp = it },
                label = { Text("Manual IP Probe") },
                placeholder = { Text("192.168.1.50") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(
                onClick = { onProbe(manualIp) },
                enabled = !isScanning && manualIp.isNotBlank(),
                modifier = Modifier.height(56.dp)
            ) {
                Text("Probe")
            }
        }

        Button(onClick = onScan, enabled = !isScanning, modifier = Modifier.fillMaxWidth()) {
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Scanning Network...")
            } else {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Auto-Detect Cameras")
            }
        }

        scanMessage?.let { msg ->
            Text(text = msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        if (discoveredCameras.isNotEmpty()) {
            Text("Discovered Devices (Tap to link):", style = MaterialTheme.typography.labelMedium)
            Card(modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp)) {
                LazyColumn {
                    items(discoveredCameras) { camera ->
                        ListItem(
                            headlineContent = { Text(camera.ip) },
                            supportingContent = { Text("ONVIF Device Found") },
                            modifier = Modifier.clickable { 
                                selectedCameraIp = camera.ip
                                showCredentialDialog = true
                            }
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = tag,
            onValueChange = onTagChange,
            label = { Text("Court Name/Tag") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }

    if (showCameraInfo) {
        CameraInfoDialog(onDismiss = { showCameraInfo = false })
    }

    if (showCredentialDialog && selectedCameraIp != null) {
        CameraCredentialDialog(
            ip = selectedCameraIp!!,
            onConfirm = { username, password ->
                val encodedUser = java.net.URLEncoder.encode(username, "UTF-8")
                val encodedPass = java.net.URLEncoder.encode(password, "UTF-8")
                onUrlChange("rtsp://$encodedUser:$encodedPass@$selectedCameraIp:554/stream1")
                onSubUrlChange("rtsp://$encodedUser:$encodedPass@$selectedCameraIp:554/stream2")
                showCredentialDialog = false
            },
            onDismiss = { showCredentialDialog = false }
        )
    }
}

@Composable
fun AuthStep(isAuthenticated: Boolean, onSignIn: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Google Integration", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (isAuthenticated) {
            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
            Text("Account Linked Successfully!", color = MaterialTheme.colorScheme.primary)
        } else {
            Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                Text("Sign in with Google")
            }
        }
    }
}

@Composable
fun SummaryStep(url: String, subUrl: String, tag: String, isAuthenticated: Boolean, source: CameraSource) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ready to Go!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryItem("Passcode", "****")
                SummaryItem("Camera Source", source.name)
                SummaryItem("Court", tag)
                if (source == CameraSource.RTSP) {
                    SummaryItem("Main Stream", url)
                    SummaryItem("Sub Stream", subUrl)
                }
                SummaryItem("Google Auth", if (isAuthenticated) "Linked" else "Not Linked")
            }
        }
    }
}

@Composable
fun SummaryItem(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = FontWeight.Bold)
        Text(value)
    }
}

@androidx.camera.core.ExperimentalGetImage
@Composable
fun ActivationStep(deviceId: String, onActivate: (String) -> Boolean, isLicensed: Boolean) {
    var key by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var showIdQr by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Product Activation", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Your Device ID", style = MaterialTheme.typography.labelMedium)
                    Text(deviceId, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { showIdQr = true }) {
                        Icon(Icons.Default.QrCode, contentDescription = "Show QR", tint = MaterialTheme.colorScheme.primary)
                    }
                    Button(onClick = { clipboardManager.setText(AnnotatedString(deviceId)) }) {
                        Text("Copy")
                    }
                }
            }
        }

        if (isLicensed) {
            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
            Text("Activated Successfully!", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        } else {
            OutlinedTextField(
                value = key,
                onValueChange = { input -> 
                    val clean = input.uppercase().filter { it.isLetterOrDigit() }.take(16)
                    key = clean
                    isError = false
                    if (clean.length == 16) {
                        if (!onActivate(clean)) isError = true
                    }
                },
                label = { Text("Enter 16-character Serial Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = isError,
                visualTransformation = LicenseKeyTransformation(),
                supportingText = { if (isError) Text("Invalid key", color = MaterialTheme.colorScheme.error) },
                trailingIcon = {
                    IconButton(onClick = { showScanner = true }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR Code")
                    }
                }
            )

            if (key.isEmpty()) {
                Text(
                    "Tip: You can scan a QR code provided by the administrator for instant activation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showScanner) {
        QrScannerDialog(
            onResult = { scannedKey ->
                val clean = scannedKey.uppercase().filter { it.isLetterOrDigit() }.take(16)
                if (clean.length == 16) {
                    key = clean
                    onActivate(clean)
                }
                showScanner = false
            },
            onDismiss = { showScanner = false }
        )
    }

    if (showIdQr) {
        DeviceIdQrDialog(deviceId = deviceId, onDismiss = { showIdQr = false })
    }
}

@androidx.camera.core.ExperimentalGetImage
@Composable
fun QrScannerDialog(onResult: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan License QR Code") },
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
                            val preview = Preview.Builder().build().apply {
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
                                android.util.Log.e("QrScanner", "Binding failed", e)
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
fun DeviceIdQrDialog(deviceId: String, onDismiss: () -> Unit) {
    val qrBitmap = remember(deviceId) {
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(deviceId, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (e: Exception) {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your Device ID QR") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Show this code to the administrator to instantly generate your license.", textAlign = TextAlign.Center)
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Device ID QR",
                        modifier = Modifier.size(200.dp).clip(RoundedCornerShape(8.dp))
                    )
                }
                Text(deviceId, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE") }
        }
    )
}

@Composable
private fun DisclaimerPoint(title: String, desc: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CameraInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("RTSP Feed Patterns") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CameraBrandSection("Dahua", listOf(
                    "Main Stream: rtsp://admin:admin123@192.168.1.100:554/cam/realmonitor?channel=1&subtype=0",
                    "Sub Stream: rtsp://admin:admin123@192.168.1.100:554/cam/realmonitor?channel=1&subtype=1"
                ))
                CameraBrandSection("Hikvision", listOf(
                    "Main (CH 1): rtsp://admin:pass@IP:554/Streaming/channels/101",
                    "Sub (CH 1): rtsp://admin:pass@IP:554/Streaming/channels/102",
                    "Main (CH 5): rtsp://admin:pass@IP:554/Streaming/channels/501"
                ))
                CameraBrandSection("Tapo", listOf(
                    "High Quality (Main): rtsp://username:password@IP:554/stream1",
                    "Standard Quality (Sub): rtsp://username:password@IP:554/stream2"
                ))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } }
    )
}

@Composable
private fun CameraBrandSection(brand: String, patterns: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(brand, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        patterns.forEach { pattern ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = pattern, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }
    }
}

class LicenseKeyTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val rawText = text.text
        val trimmed = if (rawText.length >= 16) rawText.substring(0, 16) else rawText
        var out = ""
        for (i in trimmed.indices) {
            out += trimmed[i]
            if (i % 4 == 3 && i != 15) out += "-"
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 4) return offset
                if (offset <= 8) return offset + 1
                if (offset <= 12) return offset + 2
                if (offset <= 16) return offset + 3
                return 19
            }
            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 4) return offset
                if (offset <= 9) return offset - 1
                if (offset <= 14) return offset - 2
                if (offset <= 19) return offset - 3
                return 16
            }
        }
        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}
