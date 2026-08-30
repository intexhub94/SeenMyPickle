package com.pbcam.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pbcam.app.data.SecurityUtils
import com.pbcam.app.data.db.RecordingSession
import com.pbcam.app.data.db.RecordingStatus
import com.pbcam.app.ui.viewmodel.PipelineProgress
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SessionList(
    sessions: List<RecordingSession>,
    pipelineProgress: Map<Long, PipelineProgress> = emptyMap(),
    onRetry: (Long) -> Unit,
    onPlay: (String) -> Unit,
    onDelete: (RecordingSession) -> Unit,
    onRevealRequest: (() -> Unit) -> Unit,
    isAdmin: Boolean = false,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(sessions.sortedByDescending { it.startTime }, key = { it.id }) { session ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                var isRevealed by remember { mutableStateOf(false) }
                val email = session.targetEmail ?: "No email"
                val displayedEmail = if (isRevealed) email else SecurityUtils.maskEmail(email)
                
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(session.startTime))
                        Text(dateStr, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = displayedEmail, 
                            style = MaterialTheme.typography.bodySmall, 
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { 
                                if (!isRevealed) {
                                    onRevealRequest { isRevealed = true }
                                }
                            }
                        )
                        if (isAdmin) {
                            Text("Court: ${session.courtTag}", style = MaterialTheme.typography.labelSmall)
                            
                            val progressInfo = pipelineProgress[session.id]
                            if (progressInfo != null) {
                                Spacer(Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = progressInfo.message,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    LinearProgressIndicator(
                                        progress = { progressInfo.progress },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    )
                                }
                            } else if (session.status == RecordingStatus.FAILED) {
                                Text(session.errorMessage ?: "Unknown error", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier.padding(start = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val localFileExists = session.filename.isNotBlank() && java.io.File(session.filename).exists() && java.io.File(session.filename).length() > 1024
                        if (localFileExists) {
                            IconButton(
                                onClick = { onPlay(session.filename) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.PlayCircle, "Play Local", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        
                        // Action: Retry (Failed) or Resend (Completed)
                        if (session.status == RecordingStatus.FAILED || (isAdmin && session.status == RecordingStatus.COMPLETED)) {
                            IconButton(
                                onClick = { onRetry(session.id) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                if (session.status == RecordingStatus.FAILED) {
                                    Icon(Icons.Default.Refresh, "Retry Pipeline", tint = Color.Red)
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.Send, "Resend Notification", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        
                        if (isAdmin) {
                            IconButton(
                                onClick = { onDelete(session) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun playVideoWithExternalPlayer(context: android.content.Context, videoPath: String) {
    val file = java.io.File(videoPath)
    if (!file.exists()) {
        android.widget.Toast.makeText(context, "Video file not found", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val uri: android.net.Uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = android.content.Intent.createChooser(intent, "Play Recording").apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        android.util.Log.e("SessionList", "Failed to launch external video player", e)
        android.widget.Toast.makeText(context, "No external video player application found", android.widget.Toast.LENGTH_SHORT).show()
    }
}
