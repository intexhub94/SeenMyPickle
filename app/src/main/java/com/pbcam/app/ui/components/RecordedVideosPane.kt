package com.pbcam.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pbcam.app.data.db.RecordingSession
import com.pbcam.app.data.db.RecordingStatus
import com.pbcam.app.ui.SessionList
import com.pbcam.app.ui.viewmodel.PipelineProgress

@Composable
fun RecordedVideosPane(
    sessions: List<RecordingSession>,
    pipelineProgress: Map<Long, PipelineProgress>,
    onDeleteSession: (RecordingSession) -> Unit,
    onRetry: (Long) -> Unit,
    onPlay: (String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            // --- HEADER ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Recorded Videos",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(24.dp))

            // --- LIST ---
            val completedSessions = sessions.filter { it.status == RecordingStatus.COMPLETED }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (completedSessions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No recordings found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    SessionList(
                        sessions = completedSessions,
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
}
