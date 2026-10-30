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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import dev.snapttl.domain.TimeChips
import dev.snapttl.domain.TimeUtils
import dev.snapttl.service.CaptureActions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Floating delete-timer card shown over other apps. Renders a preview of the
 * screenshot with a live countdown once a deletion time has been chosen.
 */
@Composable
fun CaptureOverlay(
    screenshotId: Long,
    displayName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val services = (context.applicationContext as SnapTtlApp).services
    val scope = rememberCoroutineScope()

    val chips = remember { services.prefs.timeChips.ifEmpty { TimeChips.FREE } }
    val defaultChip = chips.firstOrNull() ?: TimeChips.FREE.first()

    var media by remember { mutableStateOf<ScreenshotMedia?>(null) }
    var remainingMs by remember { mutableLongStateOf(0L) }
    var totalMs by remember { mutableLongStateOf(0L) }
    var hasTimer by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(screenshotId) {
        media = services.screenshotRepo.findScreenshot(screenshotId)
    }

    LaunchedEffect(screenshotId) {
        while (true) {
            val item = services.expiryRepo.findByScreenshot(screenshotId)
            if (item != null) {
                remainingMs = (item.deleteTimeEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
                totalMs = (item.deleteTimeEpochMs - item.createdAtEpochMillis).coerceAtLeast(1L)
                hasTimer = true
            }
            delay(1000)
        }
    }

    val previewUri = media?.uri ?: ""

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Spacer(Modifier.height(8.dp))
            } else {
                Text(
                    text = stringResource(R.string.overlay_choose),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { seconds ->
                    OutlinedButton(onClick = { scheduleDelete(scope, services, screenshotId, seconds) }) {
                        Text(stringResource(R.string.overlay_delete_in_fmt, TimeChips.label(seconds)))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
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
                Button(
                    onClick = {
                        share(context, screenshotId)
                        scheduleDelete(scope, services, screenshotId, defaultChip)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Share, contentDescription = stringResource(R.string.screenshots_bulk_share))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.overlay_share_and_delete, TimeChips.label(defaultChip)))
                }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.overlay_keep))
                }
            }
        }
    }
}

private fun scheduleDelete(
    scope: kotlinx.coroutines.CoroutineScope,
    services: dev.snapttl.di.Services,
    screenshotId: Long,
    seconds: Long
) {
    scope.launch {
        services.expiryRepo.scheduleIn(screenshotId = screenshotId, durationSeconds = seconds)
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
    context.startActivity(chooser)
}
