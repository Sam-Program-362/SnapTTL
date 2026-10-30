package dev.snapttl.ui.allScreenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import dev.snapttl.R
import dev.snapttl.data.repository.ScreenshotMedia
import dev.snapttl.domain.DeletionDuration
import dev.snapttl.domain.TimeUtils
import dev.snapttl.ui.LocalSnapTtlFactory
import dev.snapttl.ui.common.DurationPickerDialog

@Composable
fun AllScreenshotsScreen() {
    val viewModel: AllScreenshotsViewModel = viewModel(factory = LocalSnapTtlFactory.current)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDurationPicker by remember { mutableStateOf(false) }

    if (showDurationPicker) {
        DurationPickerDialog(
            title = stringResource(R.string.screenshots_pick_time),
            initialSeconds = state.customDurations.firstOrNull() ?: 900L,
            onDismiss = { showDurationPicker = false },
            onConfirm = { seconds ->
                showDurationPicker = false
                viewModel.scheduleForSelected(seconds)
            }
        )
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.screenshots_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = viewModel::refresh) {
                    Text(stringResource(R.string.screenshots_refresh))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.screenshots_select_hint),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            if (state.selected.isNotEmpty()) {
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.screenshots_selected, state.selected.size),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(onClick = { showDurationPicker = true }) {
                                Text(stringResource(R.string.screenshots_pick_time))
                            }
                            OutlinedButton(onClick = {
                                viewModel.scheduleForSelected(state.customDurations.firstOrNull() ?: 900L)
                            }) {
                                Text(
                                    stringResource(
                                        R.string.screenshots_delete_in,
                                        DeletionDuration.label(state.customDurations.firstOrNull() ?: 900L)
                                    )
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(onClick = viewModel::cancelTimersForSelected) {
                                Text(stringResource(R.string.screenshots_bulk_cancel))
                            }
                            Button(onClick = viewModel::deleteSelected) {
                                Text(stringResource(R.string.screenshots_bulk_delete))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (state.items.isEmpty()) {
                Text(stringResource(R.string.screenshots_empty), style = MaterialTheme.typography.bodyMedium)
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
                    item { Spacer(Modifier.height(24.dp)) }
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
            AsyncImage(
                model = media.uri,
                contentDescription = media.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(media.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(
                    "${TimeUtils.formatFileSize(media.sizeBytes)} · ${TimeUtils.formatDateTime(media.dateTakenMs)}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (scheduled) {
                    Text(
                        stringResource(R.string.screenshots_scheduled),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
        }
    }
}
