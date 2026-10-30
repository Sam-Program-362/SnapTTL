package dev.snapttl.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingItemBehavior
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.snapttl.R
import dev.snapttl.domain.DeletionDuration
import kotlin.math.roundToInt

/**
 * The duration control everywhere in SnapTTL: a Material slider over the snap
 * [DeletionDuration.LADDER]. One gesture covers 5 s … 7 d and every position
 * is a real, sensible duration — no stepping, no typing, no invalid states.
 * The current value is shown by the caller (the slider itself is text-free).
 */
@Composable
fun DurationSlider(
    seconds: Long,
    onSecondsChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val ladder = DeletionDuration.LADDER
    Slider(
        value = DeletionDuration.nearestLadderIndex(seconds).toFloat(),
        onValueChange = { raw ->
            val index = raw.roundToInt().coerceIn(0, ladder.lastIndex)
            val value = ladder[index]
            if (value != seconds) onSecondsChange(value)
        },
        valueRange = 0f..ladder.lastIndex.toFloat(),
        steps = ladder.lastIndex - 1,
        modifier = modifier
    )
}

/**
 * A scroll-wheel picker column (iOS/Cupertino style): items snap to the
 * centre; the centred item is the selection. Built on LazyColumn +
 * [rememberSnapFlingItemBehavior] so it flings naturally and works inside
 * the overlay window.
 *
 * @param items labels to show, in order.
 * @param selectedIndex initial selection (scroll position follows only at
 *   composition; afterwards the wheel drives selection).
 */
@Composable
fun WheelColumn(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeight = 36.dp
    val visibleRows = 3
    val halfItem = with(LocalDensity.current) { itemHeight.toPx() / 2f }
    if (items.isEmpty()) {
        Box(modifier = modifier.height(itemHeight * visibleRows))
        return
    }
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, items.lastIndex)
    )

    // Which item is centred right now? The wheel fills the visible area with
    // vertical content padding, so "index 0 fully visible" == "index 0 centred".
    LaunchedEffect(state, items.size) {
        snapshotFlow {
            state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            val centred = index + if (offset > halfItem) 1 else 0
            onSelectedIndexChange(centred.coerceIn(0, items.lastIndex))
        }
    }

    Box(
        modifier = modifier
            .height(itemHeight * visibleRows)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingItemBehavior(lazyListState = state),
            contentPadding = PaddingValues(vertical = itemHeight),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items.size) { i ->
                val selected = i == selectedIndex
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                ) {
                    Text(
                        text = items[i],
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
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
 * editing a quick option and for ad-hoc choices in bulk actions. The slider
 * can only produce valid values, so the confirm button is always enabled.
 */
@Composable
fun DurationPickerDialog(
    title: String,
    initialSeconds: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var seconds by remember {
        mutableStateOf(DeletionDuration.LADDER[DeletionDuration.nearestLadderIndex(initialSeconds)])
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = DeletionDuration.detailedLabel(seconds),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.duration_preview, DeletionDuration.detailedLabel(seconds)),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                DurationSlider(
                    seconds = seconds,
                    onSecondsChange = { seconds = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(seconds) }) {
                Text(stringResource(R.string.duration_set))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.duration_cancel)) }
        }
    )
}
