package com.pbcam.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pbcam.app.data.CameraSource
import com.pbcam.app.data.db.RecordingSession
import com.pbcam.app.data.db.RecordingStatus
import com.pbcam.app.ui.SessionList
import com.pbcam.app.ui.viewmodel.DashboardViewModel
import com.pbcam.app.ui.viewmodel.PipelineProgress
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullHistoryPane(
    sessions: List<RecordingSession>,
    pipelineProgress: Map<Long, PipelineProgress>,
    onDeleteSession: (RecordingSession) -> Unit,
    onDeleteAll: () -> Unit,
    onExport: (String) -> Unit,
    onRetry: (Long) -> Unit,
    onPlay: (String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    var currentFilter by remember { mutableStateOf<CameraSource?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("Purge History") },
            text = { Text("Are you sure you want to permanently delete all completed and failed recording logs? Active recordings and uploads will be skipped.") },
            confirmButton = {
                Button(
                    onClick = { 
                        onDeleteAll()
                        showDeleteAllConfirm = false 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("DELETE ALL") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) { Text("CANCEL") }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            // --- HEADER ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "Full Activity History",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { showDeleteAllConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.1f), contentColor = Color.Red)
                    ) {
                        Icon(Icons.Default.DeleteForever, null)
                        Spacer(Modifier.width(8.dp))
                        Text("CLEAR HISTORY")
                    }
                    
                    IconButton(onClick = { 
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                        onExport("SeenMyPickle_FullHistory_$timestamp.xlsx")
                    }) {
                        Icon(Icons.Default.FileDownload, "Export")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // --- FILTER BAR ---
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null, CameraSource.RTSP, CameraSource.INTERNAL).forEach { source ->
                    FilterChip(
                        selected = currentFilter == source,
                        onClick = { currentFilter = source },
                        label = { Text(source?.name ?: "ALL SOURCES") }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- LIST ---
            val filteredSessions = if (currentFilter == null) sessions else sessions.filter { it.source == currentFilter }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                SessionList(
                    sessions = filteredSessions,
                    pipelineProgress = pipelineProgress,
                    onRetry = onRetry,
                    onPlay = onPlay,
                    onDelete = onDeleteSession,
                    onRevealRequest = { it() },
                    isAdmin = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
