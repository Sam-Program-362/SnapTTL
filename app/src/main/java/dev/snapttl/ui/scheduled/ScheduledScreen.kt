package dev.snapttl.ui.scheduled

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.snapttl.data.expiry.ExpiryQueueItem
import dev.snapttl.domain.TimeUtils
import dev.snapttl.ui.LocalSnapTtlFactory

@Composable
fun ScheduledScreen() {
    val viewModel: ScheduledViewModel = viewModel(factory = LocalSnapTtlFactory.current)
    val items by viewModel.items.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Scheduled for deletion", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (items.isEmpty()) {
                Text("Nothing is scheduled.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.id }) { item ->
                        ScheduledCard(item = item, onDeleteNow = { viewModel.deleteNow(item) },
                            onCancel = { viewModel.cancel(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledCard(item: ExpiryQueueItem, onDeleteNow: () -> Unit, onCancel: () -> Unit) {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            now.value = System.currentTimeMillis()
        }
    }
    val remaining = (item.deleteTimeEpochMs - now.value).coerceAtLeast(0)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Delete in ${TimeUtils.formatCountdown(remaining)}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text("#${item.screenshotId}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel) { Text("Cancel timer") }
                Button(onClick = onDeleteNow) { Text("Delete now") }
            }
        }
    }
}
