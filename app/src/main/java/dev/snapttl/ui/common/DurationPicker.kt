package dev.snapttl.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.snapttl.R
import dev.snapttl.domain.DeletionDuration

/**
 * A compact −/value/+ stepper. The overlay and the in-app pickers are built
 * from these so duration entry feels the same everywhere.
 */
@Composable
fun Stepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { if (value > range.first) onValueChange(value - 1) },
                enabled = value > range.first
            ) {
                Icon(Icons.Default.Remove, contentDescription = "- $label")
            }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(
                onClick = { if (value < range.last) onValueChange(value + 1) },
                enabled = value < range.last
            ) {
                Icon(Icons.Default.Add, contentDescription = "+ $label")
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * In-app manager for the user's own quick-delete options — the one-tap buttons
 * that appear on the floating overlay. Tap a chip to edit it, × to remove it
 * (at least one must stay), "Add duration" for a new one. The first option is
 * what silent auto-delete and bulk delete use.
 */
@Composable
fun QuickDurationsCard(
    durations: List<Long>,
    onUpsert: (oldSeconds: Long?, newSeconds: Long) -> Unit,
    onRemove: (seconds: Long) -> Unit
) {
    var editing by remember { mutableStateOf<Long?>(null) } // null = adding
    var dialogOpen by remember { mutableStateOf(false) }

    if (dialogOpen) {
        DurationPickerDialog(
            title = if (editing == null) {
                stringResource(R.string.duration_add)
            } else {
                stringResource(R.string.duration_edit)
            },
            initialSeconds = editing ?: durations.firstOrNull() ?: DeletionDuration.DEFAULT_CHIPS.first(),
            onDismiss = { dialogOpen = false },
            onConfirm = { seconds ->
                dialogOpen = false
                onUpsert(editing, seconds)
            }
        )
    }

    Column {
        Text(stringResource(R.string.durations_card_title), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.durations_card_desc), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        durations.chunked(3).forEach { rowDurations ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowDurations.forEach { seconds ->
                    InputChip(
                        selected = false,
                        onClick = {
                            editing = seconds
                            dialogOpen = true
                        },
                        label = { Text(DeletionDuration.detailedLabel(seconds)) },
                        trailingIcon = if (durations.size > 1) {
                            {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.duration_remove),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onRemove(seconds) }
                                )
                            }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 0.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        if (durations.size < DeletionDuration.MAX_CHIPS) {
            OutlinedButton(onClick = {
                editing = null
                dialogOpen = true
            }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.duration_add))
            }
        }
    }
}

/**
 * In-app dialog for picking a duration (up to a week). Used for adding or
 * editing a quick option and for ad-hoc choices in bulk actions.
 */
@Composable
fun DurationPickerDialog(
    title: String,
    initialSeconds: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var hours by remember { mutableIntStateOf((initialSeconds / DeletionDuration.SECONDS_PER_HOUR).toInt()) }
    var minutes by remember {
        mutableIntStateOf(((initialSeconds % DeletionDuration.SECONDS_PER_HOUR) / DeletionDuration.SECONDS_PER_MINUTE).toInt())
    }
    var seconds by remember {
        mutableIntStateOf((initialSeconds % DeletionDuration.SECONDS_PER_MINUTE).toInt())
    }

    val totalSeconds = (hours * 3_600 + minutes * 60 + seconds).toLong()
    val valid = DeletionDuration.isValidSeconds(totalSeconds)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Stepper(
                        label = stringResource(R.string.duration_hours),
                        value = hours,
                        onValueChange = { hours = it },
                        range = 0..(DeletionDuration.MAX_SECONDS / DeletionDuration.SECONDS_PER_HOUR).toInt()
                    )
                    Spacer(Modifier.width(4.dp))
                    Stepper(
                        label = stringResource(R.string.duration_minutes),
                        value = minutes,
                        onValueChange = { minutes = it },
                        range = 0..59
                    )
                    Spacer(Modifier.width(4.dp))
                    Stepper(
                        label = stringResource(R.string.duration_seconds),
                        value = seconds,
                        onValueChange = { seconds = it },
                        range = 0..59
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.duration_preview, DeletionDuration.detailedLabel(totalSeconds)),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(totalSeconds) },
                enabled = valid
            ) { Text(stringResource(R.string.duration_set)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.duration_cancel)) }
        }
    )
}
