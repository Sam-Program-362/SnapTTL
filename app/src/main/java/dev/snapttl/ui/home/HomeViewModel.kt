package dev.snapttl.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.snapttl.data.repository.ScreenshotMedia
import dev.snapttl.di.Services
import dev.snapttl.domain.DeletionDuration
import dev.snapttl.service.CaptureMonitorService
import dev.snapttl.ui.common.Permissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PermissionUi(val name: String, val granted: Boolean)

data class HomeUiState(
    val serviceEnabled: Boolean = true,
    val permissions: List<PermissionUi> = emptyList(),
    val customDurations: List<Long> = emptyList(),
    val totalDeleted: Long = 0,
    val totalSaved: Long = 0,
    val recent: List<ScreenshotMedia> = emptyList()
)

class HomeViewModel(private val services: Services) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val context: Context = services.context
        _uiState.update {
            HomeUiState(
                serviceEnabled = services.prefs.serviceEnabled,
                permissions = Permissions.required(context).map { PermissionUi(it, Permissions.isGranted(context, it)) },
                customDurations = services.prefs.customDurations,
                totalDeleted = services.prefs.totalScreenshotsDeleted,
                totalSaved = services.prefs.totalMemorySavedBytes,
                recent = it.recent
            )
        }
        viewModelScope.launch {
            val recent = services.screenshotRepo.queryScreenshots().take(10)
            _uiState.update { it.copy(recent = recent) }
        }
    }

    fun toggleService(enabled: Boolean) {
        services.prefs.serviceEnabled = enabled
        services.commonState.update { it.copy(serviceEnabled = enabled) }
        if (enabled) {
            CaptureMonitorService.start(services.context)
        } else {
            CaptureMonitorService.stop(services.context)
        }
        refresh()
    }

    /** Adds or edits one of the user's quick-delete options (see [DeletionDuration.upsertChip]). */
    fun upsertDuration(oldSeconds: Long?, newSeconds: Long) {
        val next = DeletionDuration.upsertChip(services.prefs.customDurations, oldSeconds, newSeconds)
            ?: return
        services.prefs.customDurations = next
        services.commonState.update { it.copy(customDurations = next) }
        refresh()
    }

    /** Removes one of the user's quick-delete options (at least one always stays). */
    fun removeDuration(seconds: Long) {
        val next = DeletionDuration.removeChip(services.prefs.customDurations, seconds)
            ?: return
        services.prefs.customDurations = next
        services.commonState.update { it.copy(customDurations = next) }
        refresh()
    }

    fun openOverlaySettings() {
        val intent = android.content.Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION")
            .setData(android.net.Uri.parse("package:" + services.context.packageName))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        services.context.startActivity(intent)
    }
}
