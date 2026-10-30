package dev.snapttl.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
 * In-app dialog for picking the default deletion duration (up to a week).
 * Used by Home, Settings and bulk actions.
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
