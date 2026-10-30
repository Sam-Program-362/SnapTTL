package dev.snapttl.ui.overlay

import android.content.Intent
import android.provider.MediaStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.snapttl.R
import dev.snapttl.SnapTtlApp
import dev.snapttl.data.repository.ScreenshotMedia
import dev.snapttl.domain.DeletionDuration
import dev.snapttl.domain.TimeUtils
import dev.snapttl.service.CaptureActions
import dev.snapttl.ui.common.Stepper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Overlay interaction mode: quick timer vs explicit date/time. */
private enum class OverlayMode { TIMER, DATETIME }

/**
 * Floating delete-timer card shown over other apps. The user picks any
 * duration (up to a week) with the stepper; longer retention is reachable
 * through the quieter "pick a date & time" affordance. Once armed, the card
 * shows a live countdown.
 */
@Composable
fun CaptureOverlay(
    screenshotId: Long,
    displayName: String,
    defaultSeconds: Long,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val services = (context.applicationContext as SnapTtlApp).services
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(OverlayMode.TIMER) }

    // Quick-timer stepper state, seeded from the user's default.
    var hours by remember { mutableStateOf((defaultSeconds / DeletionDuration.SECONDS_PER_HOUR).toInt()) }
    var minutes by remember {
        mutableStateOf(((defaultSeconds % DeletionDuration.SECONDS_PER_HOUR) / DeletionDuration.SECONDS_PER_MINUTE).toInt())
    }
    var seconds by remember {
        mutableStateOf((defaultSeconds % DeletionDuration.SECONDS_PER_MINUTE).toInt())
    }

    // Date/time stepper state (days from now + wall-clock time of day).
    var targetDays by remember { mutableStateOf(7) }
    var targetHour by remember { mutableStateOf(0) }
    var targetMinute by remember { mutableStateOf(0) }

    var media by remember { mutableStateOf<ScreenshotMedia?>(null) }
    var remainingMs by remember { mutableLongStateOf(0L) }
    var totalMs by remember { mutableLongStateOf(0L) }
    var hasTimer by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(screenshotId) {
        media = services.screenshotRepo.findScreenshot(screenshotId)
    }

    // Countdown poll once a timer is armed; auto-close when it fires.
    LaunchedEffect(screenshotId) {
        while (true) {
            val item = services.expiryRepo.findByScreenshot(screenshotId)
            if (item != null) {
                remainingMs = (item.deleteTimeEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
                totalMs = (item.deleteTimeEpochMs - item.createdAtEpochMillis).coerceAtLeast(1L)
                hasTimer = true
                if (remainingMs <= 0L) onDismiss()
            }
            delay(1000)
        }
    }

    // Untouched cards should not linger over the screen forever.
    LaunchedEffect(mode, hasTimer) {
        if (!hasTimer) {
            delay(60_000)
            onDismiss()
        }
    }

    val timerSeconds = hours * 3_600 + minutes * 60 + seconds
    val timerValid = DeletionDuration.isValidSeconds(timerSeconds.toLong())

    val targetEpochMs = System.currentTimeMillis() +
        ((targetDays * 24L + targetHour) * 60L + targetMinute) * 60_000L
    val targetValid = DeletionDuration.isValidTargetEpochMs(targetEpochMs, System.currentTimeMillis())

    val previewUri = media?.uri ?: ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .widthIn(max = 480.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayName.ifBlank { stringResource(R.string.overlay_new_screenshot) },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { share(context, screenshotId) }) {
                    Icon(Icons.Default.Share, contentDescription = stringResource(R.string.screenshots_bulk_share))
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.overlay_close))
                }
            }
            Spacer(Modifier.height(8.dp))

            if (previewUri.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(previewUri).build(),
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(140.dp)
                )
                Spacer(Modifier.height(8.dp))
            }

            if (hasTimer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            R.string.overlay_delete_in,
                            TimeUtils.formatCountdown(remainingMs)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    CircularProgressIndicator(
                        progress = { (1f - remainingMs.toFloat() / totalMs).coerceIn(0f, 1f) },
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else if (mode == OverlayMode.TIMER) {
                Text(
                    text = stringResource(R.string.overlay_choose),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Stepper(
                        label = stringResource(R.string.duration_hours),
                        value = hours,
                        onValueChange = { hours = it },
                        range = 0..(DeletionDuration.MAX_SECONDS / DeletionDuration.SECONDS_PER_HOUR).toInt()
                    )
                    Stepper(
                        label = stringResource(R.string.duration_minutes),
                        value = minutes,
                        onValueChange = { minutes = it },
                        range = 0..59
                    )
                    Stepper(
                        label = stringResource(R.string.duration_seconds),
                        value = seconds,
                        onValueChange = { seconds = it },
                        range = 0..59
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.duration_preview, DeletionDuration.detailedLabel(timerSeconds.toLong())),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            services.expiryRepo.scheduleIn(screenshotId = screenshotId, durationSeconds = timerSeconds.toLong())
                        }
                    },
                    enabled = timerValid,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.duration_schedule))
                }
                TextButton(
                    onClick = { mode = OverlayMode.DATETIME },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.duration_pick_datetime), style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Text(
                    text = stringResource(R.string.duration_pick_datetime_title),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Stepper(
                        label = stringResource(R.string.duration_days),
                        value = targetDays,
                        onValueChange = { targetDays = it },
                        range = 0..DeletionDuration.MAX_TARGET_AHEAD_DAYS
                    )
                    Stepper(
                        label = stringResource(R.string.duration_hours),
                        value = targetHour,
                        onValueChange = { targetHour = it },
                        range = 0..23
                    )
                    Stepper(
                        label = stringResource(R.string.duration_minutes),
                        value = targetMinute,
                        onValueChange = { targetMinute = it },
                        range = 0..59
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.duration_target_preview, TimeUtils.formatDateTime(targetEpochMs)),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            services.expiryRepo.scheduleAt(screenshotId = screenshotId, targetEpochMs = targetEpochMs)
                        }
                    },
                    enabled = targetValid,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Event, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.duration_schedule))
                }
                TextButton(
                    onClick = { mode = OverlayMode.TIMER },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.duration_back_to_timer), style = MaterialTheme.typography.labelSmall)
                }
            }

            if (!hasTimer) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            deleting = true
                            scope.launch {
                                CaptureActions.deleteNow(services, screenshotId)
                                deleting = false
                                onDismiss()
                            }
                        },
                        enabled = !deleting,
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.overlay_delete_now)) }
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.overlay_keep))
                    }
                }
            }
        }
    }
}

private fun share(context: android.content.Context, screenshotId: Long) {
    val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        .buildUpon()
        .appendPath(screenshotId.toString())
        .build()
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, uri)
    }
    val chooser = Intent.createChooser(send, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(chooser) }
}
