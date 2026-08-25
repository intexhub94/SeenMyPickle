package com.pbcam.app.ui

import androidx.compose.foundation.background
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
                        if (session.status == RecordingStatus.COMPLETED) {
                            IconButton(
                                onClick = { if (session.filename.isNotBlank()) onPlay(session.filename) },
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

@Composable
fun LocalVideoPlayerDialog(videoPath: String, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = androidx.compose.runtime.remember { androidx.media3.exoplayer.ExoPlayer.Builder(context).build() }

    androidx.compose.runtime.LaunchedEffect(videoPath) {
        val file = java.io.File(videoPath)
        if (file.exists()) {
            player.setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(file)))
            player.prepare()
            player.play()
        }
    }

    AlertDialog(
        onDismissRequest = { player.release(); onDismiss() },
        title = { Text("Playback Review") },
        text = {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16/9f).background(Color.Black)) {
                androidx.compose.ui.viewinterop.AndroidView(factory = { ctx -> androidx.media3.ui.PlayerView(ctx).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxSize())
            }
        },
        confirmButton = { Button(onClick = { player.release(); onDismiss() }) { Text("CLOSE") } }
    )
}
