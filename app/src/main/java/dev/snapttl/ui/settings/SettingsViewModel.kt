package dev.snapttl.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.snapttl.di.Services
import dev.snapttl.domain.EntitlementState
import dev.snapttl.domain.OemAutoStart
import dev.snapttl.language.AppLanguage
import dev.snapttl.service.CaptureMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val serviceEnabled: Boolean = true,
    val languageCode: String = "system",
    val premiumPurchased: Boolean = false,
    val adsRemoved: Boolean = false,
    val refillDaysLeft: Int = 0,
    val silentAutoDelete: Boolean = false,
    val autoStartResolved: Boolean = false,
    val versionName: String = ""
)

class SettingsViewModel(private val services: Services) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val context: Context = services.context
        _uiState.update {
            SettingsUiState(
                serviceEnabled = services.prefs.serviceEnabled,
                languageCode = services.prefs.languageCode,
                premiumPurchased = services.prefs.havePremiumPurchased,
                adsRemoved = services.prefs.haveAdsRemoved,
                refillDaysLeft = refillDaysLeft(),
                silentAutoDelete = services.prefs.noDialogAutoDeleteEnabled,
                autoStartResolved = resolveAutoStart() != null,
                versionName = versionName(context)
            )
        }
    }

    fun setLanguage(code: String) {
        services.prefs.languageCode = code
        services.analytics.logEvent("language_selected")
        dev.snapttl.language.LocaleHelper.apply(services.context, code)
        refresh()
    }

    fun setSilentAutoDelete(enabled: Boolean) {
        // Silent auto-delete is a one-time-premium feature (same rule as Mark).
        if (enabled && !services.prefs.havePremiumPurchased) return
        services.prefs.noDialogAutoDeleteEnabled = enabled
        services.commonState.update { it.copy(noDialogAutoDelete = enabled) }
        refresh()
    }

    fun buyPremium() {
        services.prefs.havePremiumPurchased = true
        services.commonState.update { it.copy(premiumPurchased = true) }
        services.analytics.logEvent("premium_click")
        refresh()
    }

    fun removeAds() {
        services.prefs.haveAdsRemoved = true
        services.commonState.update { it.copy(adsRemoved = true) }
        services.analytics.logEvent("remove_ads_click")
        refresh()
    }

    fun applyRewardedRefill() {
        val now = System.currentTimeMillis()
        val state = EntitlementState(
            premiumPurchased = services.prefs.havePremiumPurchased,
            adsRemoved = services.prefs.haveAdsRemoved,
            refillExpiryEpochMs = services.prefs.refillExpiryEpochMs
        )
        state.applyRewardedAdRefill(now)
        services.prefs.refillExpiryEpochMs = state.refillExpiryEpochMs
        services.commonState.update { it.copy(refillDaysLeft = state.refillDaysLeft(now)) }
        services.analytics.logEvent("rewarded_ad_refill")
        refresh()
    }

    /** Opens the OEM auto-start manager so battery killers stop stopping the service. */
    fun openAutoStart() {
        val component = resolveAutoStart() ?: return
        val intent = Intent().apply {
            component = ComponentName(component.packageName, component.activityClass)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        services.context.startActivity(intent)
    }

    private fun resolveAutoStart(): OemAutoStart.Component? {
        val brand = listOfNotNull(Build.MANUFACTURER, Build.MODEL)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        return OemAutoStart.resolve(brand)
    }

    fun toggleService(enabled: Boolean) {
        services.prefs.serviceEnabled = enabled
        services.commonState.update { it.copy(serviceEnabled = enabled) }
        if (enabled) CaptureMonitorService.start(services.context) else CaptureMonitorService.stop(services.context)
        refresh()
    }

    private fun refillDaysLeft(): Int {
        val expiry = services.prefs.refillExpiryEpochMs
        if (expiry <= 0L) return 0
        return ((expiry - System.currentTimeMillis()) / 86_400_000L).coerceIn(0, 40)
    }

    private fun versionName(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            info.versionName ?: ""
        } catch (_: PackageManager.NameNotFoundException) {
            ""
        }
    }

    companion object {
        val LANGUAGES: List<AppLanguage> = AppLanguage.values().toList()
    }
}
