package dev.snapttl.ui.overlay

import android.content.Intent
import android.provider.MediaStore
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.snapttl.R
import dev.snapttl.SnapTtlApp
import dev.snapttl.data.repository.ScreenshotMedia
import dev.snapttl.domain.DeletionDuration
import dev.snapttl.domain.TimeUtils
import dev.snapttl.service.CaptureActions
import dev.snapttl.ui.common.DurationSlider
import dev.snapttl.ui.common.WheelColumn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Overlay interaction mode: one-tap options, slider timer, or date/time. */
private enum class OverlayMode { OPTIONS, TIMER, DATETIME }

/**
 * Floating delete-timer card shown over other apps — the heart of SnapTTL's
 * muscle memory, modelled on the reference app's compact bottom bar:
 *
 * - a wide letterbox preview strip (identify the screenshot at a glance),
 *   with share and keep (✕) affordances as small badges on it — no filename
 *   text anywhere;
 * - the user's own one-tap durations as a single scrolling row of chips;
 * - a compact action row: delete-now, "Custom…" (slider), date & time;
 * - any choice arms the timer and closes the card **immediately** — there is
 *   no confirmation/countdown state to sit through;
 * - the card sits bottom-centre, above the nav bar, out of the way of the
 *   content the user just captured, and enters with a soft rise animation.
 */
@Composable
fun CaptureOverlay(
    screenshotId: Long,
    displayName: String,
    quickOptions: List<Long>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SnapTtlApp
    val services = app.services

    var mode by remember { mutableStateOf(OverlayMode.OPTIONS) }

    // Slider timer, seeded from the first quick option (snapped to the ladder).
    val seed = quickOptions.firstOrNull() ?: DeletionDuration.DEFAULT_CHIPS.first()
    var timerSeconds by remember {
        mutableStateOf(DeletionDuration.LADDER[DeletionDuration.nearestLadderIndex(seed)])
    }

    // Date/time wheels (offset from now).
    var targetDays by remember { mutableIntStateOf(1) }
    var targetHour by remember { mutableIntStateOf(0) }
    var targetMinute by remember { mutableIntStateOf(0) }

    var media by remember { mutableStateOf<ScreenshotMedia?>(null) }

    LaunchedEffect(screenshotId) {
        media = services.screenshotRepo.findScreenshot(screenshotId)
    }

    // Untouched cards should not linger over the screen forever; a mode
    // change (user exploring) restarts the grace period.
    LaunchedEffect(mode) {
        delay(45_000)
        onDismiss()
    }

    // Soft entry: rise + fade, once, when the window appears.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entry by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "entry"
    )

    fun arm(durationSeconds: Long) {
        app.appScope.launch {
            services.expiryRepo.scheduleIn(
                screenshotId = screenshotId,
                durationSeconds = durationSeconds
            )
        }
        onDismiss()
    }

    fun armAt(targetEpochMs: Long) {
        app.appScope.launch {
            services.expiryRepo.scheduleAt(
                screenshotId = screenshotId,
                targetEpochMs = targetEpochMs
            )
        }
        onDismiss()
    }

    fun deleteNow() {
        app.appScope.launch { CaptureActions.deleteNow(services, screenshotId) }
        onDismiss()
    }

    val targetEpochMs = System.currentTimeMillis() +
        ((targetDays * 24L + targetHour) * 60L + targetMinute) * 60_000L
    val targetValid = DeletionDuration.isValidTargetEpochMs(targetEpochMs, System.currentTimeMillis())

    Card(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
            .widthIn(max = 340.dp)
            .graphicsLayer {
                alpha = entry
                translationY = (1f - entry) * 48.dp.toPx()
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            PreviewStrip(
                model = media?.uri ?: "",
                contentDescription = displayName.ifBlank {
                    stringResource(R.string.overlay_new_screenshot)
                },
                onShare = { share(context, screenshotId) },
                onKeep = onDismiss
            )
            Spacer(Modifier.height(10.dp))

            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 10 }) togetherWith
                        fadeOut(tween(80))
                },
                label = "mode"
            ) { current ->
                when (current) {
                    OverlayMode.OPTIONS -> OptionsMode(
                        quickOptions = quickOptions,
                        onChoose = { arm(it) },
                        onCustom = { mode = OverlayMode.TIMER },
                        onDateTime = { mode = OverlayMode.DATETIME },
                        onDeleteNow = { deleteNow() }
                    )

                    OverlayMode.TIMER -> TimerMode(
                        seconds = timerSeconds,
                        onSecondsChange = { timerSeconds = it },
                        onArm = { arm(timerSeconds) },
                        onBack = { mode = OverlayMode.OPTIONS }
                    )

                    OverlayMode.DATETIME -> DateTimeMode(
                        days = targetDays,
                        hours = targetHour,
                        minutes = targetMinute,
                        onDaysChange = { targetDays = it },
                        onHoursChange = { targetHour = it },
                        onMinutesChange = { targetMinute = it },
                        targetEpochMs = targetEpochMs,
                        targetValid = targetValid,
                        onArm = { armAt(targetEpochMs) },
                        onBack = { mode = OverlayMode.OPTIONS }
                    )
                }
            }
        }
    }
}

/**
 * Wide letterbox preview of the screenshot: full card width, ~96 dp tall —
 * enough to identify the shot at a glance without eating the screen. Share
 * and keep (✕) ride on it as translucent badges.
 */
@Composable
private fun PreviewStrip(
    model: String,
    contentDescription: String,
    onShare: () -> Unit,
    onKeep: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (model.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(model)
                    .crossfade(150)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BadgeIconButton(
                icon = Icons.Default.Share,
                contentDescription = stringResource(R.string.screenshots_bulk_share),
                onClick = onShare
            )
            BadgeIconButton(
                icon = Icons.Default.Close,
                contentDescription = stringResource(R.string.overlay_keep),
                onClick = onKeep
            )
        }
    }
}

/** Small translucent circular badge button for the preview strip. */
@Composable
private fun BadgeIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** One-tap options: scrolling chip row + compact action row. */
@Composable
private fun OptionsMode(
    quickOptions: List<Long>,
    onChoose: (Long) -> Unit,
    onCustom: () -> Unit,
    onDateTime: () -> Unit,
    onDeleteNow: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickOptions.forEach { option ->
                OutlinedButton(
                    onClick = { onChoose(option) },
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) { Text(DeletionDuration.label(option)) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onDeleteNow) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.overlay_delete_now),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            FilledTonalButton(
                onClick = onCustom,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.duration_custom))
            }
            IconButton(onClick = onDateTime) {
                Icon(
                    Icons.Default.Event,
                    contentDescription = stringResource(R.string.duration_pick_datetime)
                )
            }
        }
    }
}

/** Free-form timer: big live value + snap-ladder slider. */
@Composable
private fun TimerMode(
    seconds: Long,
    onSecondsChange: (Long) -> Unit,
    onArm: () -> Unit,
    onBack: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = DeletionDuration.detailedLabel(seconds),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.duration_preview, DeletionDuration.detailedLabel(seconds)),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(2.dp))
        DurationSlider(
            seconds = seconds,
            onSecondsChange = onSecondsChange,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                DeletionDuration.label(DeletionDuration.LADDER.first()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                DeletionDuration.label(DeletionDuration.LADDER.last()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.duration_back_to_options)
                )
            }
            Button(onClick = onArm, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.duration_schedule))
            }
        }
    }
}

/** Date & time: three snap wheels + absolute preview. */
@Composable
private fun DateTimeMode(
    days: Int,
    hours: Int,
    minutes: Int,
    onDaysChange: (Int) -> Unit,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit,
    targetEpochMs: Long,
    targetValid: Boolean,
    onArm: () -> Unit,
    onBack: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WheelColumn(
                items = (0..DeletionDuration.MAX_TARGET_AHEAD_DAYS).map { it.toString() },
                selectedIndex = days,
                onSelectedIndexChange = onDaysChange,
                modifier = Modifier.weight(1f)
            )
            WheelColumn(
                items = (0..23).map { it.toString() },
                selectedIndex = hours,
                onSelectedIndexChange = onHoursChange,
                modifier = Modifier.weight(1f)
            )
            WheelColumn(
                items = (0..59).map { it.toString() },
                selectedIndex = minutes,
                onSelectedIndexChange = onMinutesChange,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                stringResource(R.string.duration_days),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                stringResource(R.string.duration_hours),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                stringResource(R.string.duration_minutes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.duration_target_preview, TimeUtils.formatDateTime(targetEpochMs)),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.duration_back_to_options)
                )
            }
            Button(onClick = onArm, enabled = targetValid, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.duration_schedule))
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
