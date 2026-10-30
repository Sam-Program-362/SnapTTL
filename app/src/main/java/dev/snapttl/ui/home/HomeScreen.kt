package dev.snapttl.ui.home

import android.Manifest
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.snapttl.di.Services
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
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refresh() }

    var easterEggTaps by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "SnapTTL",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
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
                            "Screenshot monitor",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = state.serviceEnabled, onCheckedChange = viewModel::toggleService)
                    }
                    Text(
                        if (state.serviceEnabled) "Watching for new screenshots."
                        else "Turn on the monitor to catch screenshots instantly.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Deleted %1$d · Saved %2$s".format(state.totalDeleted, TimeUtils.formatFileSize(state.totalSaved)),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Text("Required permissions", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            state.permissions.forEach { perm ->
                PermissionRow(
                    name = perm.name,
                    granted = perm.granted,
                    onGrant = {
                        if (perm.name == Permissions.SYSTEM_ALERT_WINDOW) {
                            viewModel.openOverlaySettings()
                        } else {
                            grantLauncher.launch(perm.name)
                        }
                    }
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("Time chips", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.timeChips.isEmpty()) {
                    Text("Default 15 and 45 minutes.", style = MaterialTheme.typography.bodySmall)
                }
                state.timeChips.forEach { chip ->
                    OutlinedButton(onClick = { /* reserved for quick schedule */ }) {
                        Text("${chip} min")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (state.premiumPurchased) {
                Text("Premium unlocked.", style = MaterialTheme.typography.bodySmall)
            } else if (state.refillDaysLeft > 0) {
                Text("Ad-refill active: ${state.refillDaysLeft} days left.", style = MaterialTheme.typography.bodySmall)
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
                    "SnapTTL · time-to-live for every screenshot",
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
                Text("Granted", style = MaterialTheme.typography.bodySmall)
            } else {
                Button(onClick = onGrant) { Text("Grant") }
            }
        }
    }
}

private fun shortName(permission: String): String = when (permission) {
    Permissions.NOTIFICATIONS -> "Notifications"
    Permissions.READ_MEDIA_IMAGES -> "Photos"
    Permissions.READ_MEDIA_VISUAL_USER_SELECTED -> "Partial photos access"
    Permissions.READ_EXTERNAL_STORAGE -> "Storage"
    Permissions.SYSTEM_ALERT_WINDOW -> "Display over other apps"
    else -> permission
}
