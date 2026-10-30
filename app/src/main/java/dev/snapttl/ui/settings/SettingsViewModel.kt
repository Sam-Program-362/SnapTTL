package dev.snapttl.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.snapttl.di.Services
import dev.snapttl.domain.EntitlementState
import dev.snapttl.domain.OemAutoStart
import dev.snapttl.domain.TimeChips
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
    val versionName: String = "",
    val overlayPermissionGranted: Boolean = false,
    val screenshotFolderTreeUri: String? = null,
    val ocrIndexEnabled: Boolean = true,
    val armedChips: List<Long> = emptyList()
)

class SettingsViewModel(private val services: Services) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Signals the UI to recreate so a language change is applied immediately. */
    val recreate = MutableStateFlow(0)

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
                refillDaysLeft = entitlement().refillDaysLeft(System.currentTimeMillis()),
                silentAutoDelete = services.prefs.noDialogAutoDeleteEnabled,
                autoStartResolved = resolveAutoStart() != null,
                versionName = versionName(context),
                overlayPermissionGranted = Settings.canDrawOverlays(context),
                screenshotFolderTreeUri = services.prefs.screenshotFolderTreeUri,
                ocrIndexEnabled = services.prefs.ocrIndexEnabled,
                armedChips = services.prefs.timeChips
            )
        }
    }

    fun setLanguage(code: String) {
        services.prefs.languageCode = code
        services.analytics.logEvent("language_selected")
        dev.snapttl.language.LocaleHelper.apply(services.context, code)
        refresh()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            recreate.value += 1
        }
    }

    fun setSilentAutoDelete(enabled: Boolean) {
        // Silent auto-delete is a one-time-premium feature (same rule as Mark).
        if (enabled && !services.prefs.havePremiumPurchased) return
        services.prefs.noDialogAutoDeleteEnabled = enabled
        services.commonState.update { it.copy(noDialogAutoDelete = enabled) }
        refresh()
    }

    fun setOcrIndexEnabled(enabled: Boolean) {
        services.prefs.ocrIndexEnabled = enabled
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
        val state = entitlement()
        state.applyRewardedAdRefill(now)
        services.prefs.refillExpiryEpochMs = state.refillExpiryEpochMs
        services.commonState.update { it.copy(refillDaysLeft = state.refillDaysLeft(now)) }
        services.analytics.logEvent("rewarded_ad_refill")
        refresh()
    }

    /** Persists the SAF screenshots folder the user picked (persistent grant kept). */
    fun setScreenshotFolder(uri: Uri?) {
        if (uri == null) return
        try {
            services.context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some pickers do not offer a persistable grant; the tree still works
            // for this session and MediaStore remains the fallback.
        }
        services.prefs.screenshotFolderTreeUri = uri.toString()
        services.analytics.logEvent("screenshot_folder_selected")
        refresh()
    }

    fun clearScreenshotFolder() {
        val stored = services.prefs.screenshotFolderTreeUri ?: return
        try {
            services.context.contentResolver.releasePersistableUriPermission(
                Uri.parse(stored),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // already released
        }
        services.prefs.screenshotFolderTreeUri = null
        refresh()
    }

    /** Arms/disarms an advanced duration chip (premium/refill gated, mirrors Home). */
    fun toggleChip(seconds: Long) {
        val entitlement = entitlement()
        val now = System.currentTimeMillis()
        val allowed = TimeChips.allowed(entitlement.hasUnlockedFeatures(now))
        if (seconds !in allowed) return
        val current = services.prefs.timeChips.toMutableList()
        if (seconds in current) {
            if (current.size > 1) current.remove(seconds)
        } else {
            current.add(seconds)
        }
        val next = current.sorted()
        services.prefs.timeChips = next
        services.commonState.update { it.copy(timeChips = next) }
        refresh()
    }

    /** Opens the OEM auto-start manager so battery killers stop stopping the service. */
    fun openAutoStart() {
        val target = resolveAutoStart() ?: return
        val intent = Intent().apply {
            component = ComponentName(target.packageName, target.activityClass)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        services.analytics.logEvent("oem_auto_start_opened")
        services.context.startActivity(intent)
    }

    fun openOverlaySettings() {
        val intent = Intent(
            "android.settings.action.MANAGE_OVERLAY_PERMISSION",
            android.net.Uri.parse("package:" + services.context.packageName)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

    private fun entitlement() = EntitlementState(
        premiumPurchased = services.prefs.havePremiumPurchased,
        adsRemoved = services.prefs.haveAdsRemoved,
        refillExpiryEpochMs = services.prefs.refillExpiryEpochMs
    )

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
        val LANGUAGES: List<AppLanguage> = AppLanguage.entries.toList()
    }
}
