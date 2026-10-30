package dev.snapttl.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.snapttl.di.Services
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
    val timeChips: List<Int> = emptyList(),
    val premiumPurchased: Boolean = false,
    val refillDaysLeft: Int = 0,
    val totalDeleted: Long = 0,
    val totalSaved: Long = 0
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
                timeChips = services.prefs.timeChips,
                premiumPurchased = services.prefs.havePremiumPurchased,
                refillDaysLeft = refillDaysLeft(),
                totalDeleted = services.prefs.totalScreenshotsDeleted,
                totalSaved = services.prefs.totalMemorySavedBytes
            )
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

    fun openOverlaySettings() {
        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSIONS)
            .setData(android.net.Uri.parse("package:" + services.context.packageName))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        services.context.startActivity(intent)
    }

    private fun refillDaysLeft(): Int {
        val expiry = services.prefs.refillExpiryEpochMs
        if (expiry <= 0L) return 0
        return ((expiry - System.currentTimeMillis()) / 86_400_000L).coerceIn(0, 40)
    }
}
