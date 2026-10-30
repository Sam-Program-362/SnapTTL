package dev.snapttl.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import dev.snapttl.R
import dev.snapttl.domain.TimeChips
import dev.snapttl.domain.TimeUtils
import dev.snapttl.ui.LocalSnapTtlFactory
import dev.snapttl.ui.common.Permissions

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenEasterEgg: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel(factory = LocalSnapTtlFactory.current)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val grantLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refresh() }
    val runtimeRequest = remember { Permissions.runtimeRequestList() }

    var easterEggTaps by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            easterEggTaps++
                            if (easterEggTaps >= 5) {
                                easterEggTaps = 0
                                onOpenEasterEgg()
                            }
                        }
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.settings_service),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = state.serviceEnabled, onCheckedChange = viewModel::toggleService)
                    }
                    Text(
                        if (state.serviceEnabled) stringResource(R.string.home_service_on)
                        else stringResource(R.string.home_service_off),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.home_stats, state.totalDeleted, TimeUtils.formatFileSize(state.totalSaved)),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Text(stringResource(R.string.home_permissions_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            state.permissions.forEach { perm ->
                PermissionRow(
                    name = perm.name,
                    granted = perm.granted,
                    onGrant = {
                        if (perm.name == Permissions.SYSTEM_ALERT_WINDOW) {
                            viewModel.openOverlaySettings()
                        } else {
                            grantLauncher.launch(runtimeRequest.toTypedArray())
                        }
                    }
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.home_chips_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.home_chips_hint), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.allowedChips.forEach { chip ->
                    FilterChip(
                        selected = chip in state.armedChips,
                        onClick = { viewModel.toggleChip(chip) },
                        label = { Text(TimeChips.label(chip)) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            if (state.premiumPurchased) {
                Text(stringResource(R.string.home_premium_active), style = MaterialTheme.typography.bodySmall)
            } else if (state.refillDaysLeft > 0) {
                Text(stringResource(R.string.extra_refill_days, state.refillDaysLeft), style = MaterialTheme.typography.bodySmall)
            }

            if (state.recent.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.home_recent_title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.recent, key = { it.id }) { media ->
                        Column(modifier = Modifier.width(96.dp)) {
                            AsyncImage(
                                model = media.uri,
                                contentDescription = media.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                TimeUtils.formatFileSize(media.sizeBytes),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        easterEggTaps++
                        if (easterEggTaps >= 5) {
                            easterEggTaps = 0
                            onOpenEasterEgg()
                        }
                    }
            ) {
                Text(
                    stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(name: String, granted: Boolean, onGrant: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(shortName(name), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (granted) {
                Text(stringResource(R.string.home_granted), style = MaterialTheme.typography.bodySmall)
            } else {
                Button(onClick = onGrant) { Text(stringResource(R.string.home_missing)) }
            }
        }
    }
}

@Composable
private fun shortName(permission: String): String = when (permission) {
    Permissions.NOTIFICATIONS -> stringResource(R.string.perm_notifications)
    Permissions.READ_MEDIA_IMAGES -> stringResource(R.string.perm_photos)
    Permissions.READ_MEDIA_VISUAL_USER_SELECTED -> stringResource(R.string.perm_partial_photos)
    Permissions.READ_EXTERNAL_STORAGE -> stringResource(R.string.perm_storage)
    Permissions.SYSTEM_ALERT_WINDOW -> stringResource(R.string.perm_overlay)
    else -> permission
}
