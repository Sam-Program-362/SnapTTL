package dev.snapttl.ui.allScreenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.snapttl.data.repository.ScreenshotMedia
import dev.snapttl.domain.TimeUtils
import dev.snapttl.ui.LocalSnapTtlFactory

@Composable
fun AllScreenshotsScreen() {
    val viewModel: AllScreenshotsViewModel = viewModel(factory = LocalSnapTtlFactory.current)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("All screenshots", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = viewModel::refresh) { Text("Refresh") }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Long-press items to select several, then set timers, cancel timers or delete.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            if (state.selected.isNotEmpty()) {
                Card {
                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${state.selected.size} selected", modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { viewModel.scheduleForSelected(15) }) { Text("15m") }
                        OutlinedButton(onClick = { viewModel.scheduleForSelected(45) }) { Text("45m") }
                        OutlinedButton(onClick = viewModel::cancelTimersForSelected) { Text("Cancel timers") }
                        Button(onClick = viewModel::deleteSelected) { Text("Delete") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (state.items.isEmpty()) {
                Text("No screenshots found on this device.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.items, key = { it.id }) { media ->
                        ScreenshotRow(
                            media = media,
                            scheduled = media.id in state.scheduledIds,
                            selected = media.id in state.selected,
                            onToggle = { viewModel.toggleSelected(media.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenshotRow(
    media: ScreenshotMedia,
    scheduled: Boolean,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(media.displayName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${TimeUtils.formatFileSize(media.sizeBytes)} · ${TimeUtils.formatDateTime(media.dateTakenMs)}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (scheduled) Text("Scheduled for deletion", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
