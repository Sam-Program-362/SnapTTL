package dev.snapttl.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.snapttl.data.repository.ScreenshotMedia
import dev.snapttl.di.Services
import dev.snapttl.domain.EntitlementState
import dev.snapttl.domain.TimeChips
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
    val armedChips: List<Long> = emptyList(),
    val allowedChips: List<Long> = emptyList(),
    val premiumPurchased: Boolean = false,
    val refillDaysLeft: Int = 0,
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
        val entitlement = entitlement()
        val now = System.currentTimeMillis()
        _uiState.update {
            HomeUiState(
                serviceEnabled = services.prefs.serviceEnabled,
                permissions = Permissions.required(context).map { PermissionUi(it, Permissions.isGranted(context, it)) },
                armedChips = services.prefs.timeChips,
                allowedChips = TimeChips.allowed(entitlement.hasUnlockedFeatures(now)),
                premiumPurchased = services.prefs.havePremiumPurchased,
                refillDaysLeft = entitlement.refillDaysLeft(now),
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

    /**
     * Arms or disarms a duration chip. Free users may only arm the free chips;
     * advanced chips require premium or an active refill.
     */
    fun toggleChip(seconds: Long) {
        val entitlement = entitlement()
        val now = System.currentTimeMillis()
        val allowed = TimeChips.allowed(entitlement.hasUnlockedFeatures(now))
        if (seconds !in allowed) return

        val current = services.prefs.timeChips.toMutableList()
        if (seconds in current) {
            if (current.size > 1) current.remove(seconds) // keep at least one chip armed
        } else {
            current.add(seconds)
        }
        val next = current.sorted()
        services.prefs.timeChips = next
        services.commonState.update { it.copy(timeChips = next) }
        refresh()
    }

    fun openOverlaySettings() {
        val intent = android.content.Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION")
            .setData(android.net.Uri.parse("package:" + services.context.packageName))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        services.context.startActivity(intent)
    }

    private fun entitlement() = EntitlementState(
        premiumPurchased = services.prefs.havePremiumPurchased,
        adsRemoved = services.prefs.haveAdsRemoved,
        refillExpiryEpochMs = services.prefs.refillExpiryEpochMs
    )
}
