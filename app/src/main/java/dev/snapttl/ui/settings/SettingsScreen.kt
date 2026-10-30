package dev.snapttl.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.snapttl.R
import dev.snapttl.language.AppLanguage
import dev.snapttl.ui.LocalSnapTtlFactory
import dev.snapttl.ui.common.QuickDurationsCard

@Composable
fun SettingsScreen() {
    val viewModel: SettingsViewModel = viewModel(factory = LocalSnapTtlFactory.current)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var languageExpanded by remember { mutableStateOf(false) }

    // SAF screenshots-folder picker: the persistent tree grant makes silent
    // deletion reliable on Android 11+ (MediaStore alone cannot delete
    // system-owned screenshots).
    val treePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> viewModel.setScreenshotFolder(uri) }

    // A language change below Android 13 needs an activity recreation to apply.
    val recreateTick by viewModel.recreate.collectAsStateWithLifecycle()
    LaunchedEffect(recreateTick) {
        if (recreateTick > 0) (context as? android.app.Activity)?.recreate()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.settings_service), modifier = Modifier.weight(1f))
                        Switch(checked = state.serviceEnabled, onCheckedChange = viewModel::toggleService)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // --- Permissions ---
            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.settings_permissions), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_overlay))
                            Text(
                                if (state.overlayPermissionGranted) stringResource(R.string.settings_overlay_granted)
                                else stringResource(R.string.settings_overlay_missing),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (!state.overlayPermissionGranted) {
                            Button(onClick = viewModel::openOverlaySettings) {
                                Text(stringResource(R.string.home_missing))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // --- Screenshots folder (SAF) ---
            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.settings_folder_title), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.settings_folder_desc), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    if (state.screenshotFolderTreeUri != null) {
                        Text(
                            stringResource(R.string.settings_folder_current),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { treePicker.launch(null) }) {
                            Text(stringResource(R.string.settings_folder_pick))
                        }
                        if (state.screenshotFolderTreeUri != null) {
                            OutlinedButton(onClick = viewModel::clearScreenshotFolder) {
                                Text(stringResource(R.string.settings_folder_clear))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // --- Quick delete durations ---
            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    QuickDurationsCard(
                        durations = state.customDurations,
                        onUpsert = viewModel::upsertDuration,
                        onRemove = viewModel::removeDuration
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // --- Language ---
            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)
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

            // --- Search index ---
            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_ocr))
                            Text(stringResource(R.string.settings_ocr_desc), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = state.ocrIndexEnabled, onCheckedChange = viewModel::setOcrIndexEnabled)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // --- Silent auto-delete ---
            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.extra_silent_auto_delete), modifier = Modifier.weight(1f))
                        Switch(
                            checked = state.silentAutoDelete,
                            onCheckedChange = viewModel::setSilentAutoDelete
                        )
                    }
                    Text(
                        stringResource(R.string.extra_silent_desc),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // --- Auto-start ---
            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.settings_autostart_title), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    if (state.autoStartResolved) {
                        Text(
                            stringResource(R.string.settings_autostart_desc),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = viewModel::openAutoStart) {
                            Text(stringResource(R.string.settings_autostart_open))
                        }
                    } else {
                        Text(
                            stringResource(R.string.settings_autostart_none),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.settings_about, state.versionName),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
