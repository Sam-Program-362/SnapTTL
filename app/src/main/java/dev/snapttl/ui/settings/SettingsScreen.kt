package dev.snapttl.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.snapttl.language.AppLanguage
import dev.snapttl.ui.LocalSnapTtlFactory

@Composable
fun SettingsScreen() {
    val viewModel: SettingsViewModel = viewModel(factory = LocalSnapTtlFactory.current)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var languageExpanded by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Settings", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Screenshot monitor", modifier = Modifier.weight(1f))
                        Switch(checked = state.serviceEnabled, onCheckedChange = viewModel::toggleService)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Language", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    TextField(
                        value = AppLanguage.fromCode(state.languageCode).displayName,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().clickable { languageExpanded = true }
                    )
                    DropdownMenu(expanded = languageExpanded, onDismissRequest = { languageExpanded = false }) {
                        SettingsViewModel.LANGUAGES.forEach { language ->
                            DropdownMenuItem(
                                text = { Text(language.displayName) },
                                onClick = {
                                    viewModel.setLanguage(language.code)
                                    languageExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Silent auto-delete", modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.silentAutoDelete,
                        enabled = state.premiumPurchased,
                        onCheckedChange = viewModel::setSilentAutoDelete
                    )
                    if (!state.premiumPurchased) {
                        Text("Premium-only feature; not covered by a refill.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Subscriptions", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.premiumPurchased) {
                            Text("Premium", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Button(onClick = viewModel::buyPremium) { Text("Buy Premium") }
                        }
                        if (state.adsRemoved) {
                            Text("Ads removed", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            OutlinedButton(onClick = viewModel::removeAds) { Text("Remove ads") }
                        }
                    }
                    if (state.refillDaysLeft > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text("Ad-refill active: ${state.refillDaysLeft} days left.")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = viewModel::applyRewardedRefill) {
                        Text("Watch ad · +20 days refill")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Background auto-start", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    if (state.autoStartResolved) {
                        Text(
                            "Aggressive battery killers may stop the monitor. Open the OEM auto-start screen to allow it.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = viewModel::openAutoStart) { Text("Open auto-start settings") }
                    } else {
                        Text(
                            "No OEM auto-start routing is needed on this device.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "SnapTTL ${state.versionName}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
