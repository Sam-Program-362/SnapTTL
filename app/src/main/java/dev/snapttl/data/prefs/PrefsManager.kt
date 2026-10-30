package dev.snapttl.data.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * Dual SharedPreferences wrapper reconstructed from Mark's `app_preferences` /
 * `retain_app_preferences` pair. Values in `retain_app_preferences` are included in
 * backup rules so the deleted-screenshot counters survive a reinstall.
 */
class PrefsManager(context: Context) {

    private val app: SharedPreferences =
        context.applicationContext.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    private val retain: SharedPreferences =
        context.applicationContext.getSharedPreferences(RETAIN_PREFS, Context.MODE_PRIVATE)

    var serviceEnabled: Boolean
        get() = app.getBoolean(KEY_SERVICE_ENABLED, true)
        set(value) = app.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var languageCode: String
        get() = app.getString(KEY_LANGUAGE, "system") ?: "system"
        set(value) = app.edit().putString(KEY_LANGUAGE, value).apply()

    var havePremiumPurchased: Boolean
        get() = app.getBoolean(KEY_PREMIUM, false)
        set(value) = app.edit().putBoolean(KEY_PREMIUM, value).apply()

    var haveAdsRemoved: Boolean
        get() = app.getBoolean(KEY_ADS_REMOVED, false)
        set(value) = app.edit().putBoolean(KEY_ADS_REMOVED, value).apply()

    var refillExpiryEpochMs: Long
        get() = app.getLong(KEY_REFILL_EXPIRY, 0L)
        set(value) = app.edit().putLong(KEY_REFILL_EXPIRY, value).apply()

    var timeTillNoAdsEpochMs: Long
        get() = app.getLong(KEY_TIME_TILL_NO_ADS, 0L)
        set(value) = app.edit().putLong(KEY_TIME_TILL_NO_ADS, value).apply()

    var noDialogAutoDeleteEnabled: Boolean
        get() = app.getBoolean(KEY_NO_DIALOG_AUTO_DELETE, false)
        set(value) = app.edit().putBoolean(KEY_NO_DIALOG_AUTO_DELETE, value).apply()

    var screenshotFolderTreeUri: String?
        get() = app.getString(KEY_SCREENSHOT_TREE_URI, null)
        set(value) = app.edit().putString(KEY_SCREENSHOT_TREE_URI, value).apply()

    /** Armed deletion chips, in seconds (see [dev.snapttl.domain.TimeChips]). */
    var timeChips: List<Long>
        get() {
            val raw = app.getString(KEY_TIME_CHIPS_SECONDS, null) ?: return TimeChipsDefaults.FREE
            val parsed = dev.snapttl.domain.TimeChips.decode(raw)
            return parsed.ifEmpty { TimeChipsDefaults.FREE }
        }
        set(value) = app.edit()
            .putString(KEY_TIME_CHIPS_SECONDS, dev.snapttl.domain.TimeChips.encode(value))
            .apply()

    /** Whether the on-device OCR/label index may run in the background. */
    var ocrIndexEnabled: Boolean
        get() = app.getBoolean(KEY_OCR_INDEX_ENABLED, true)
        set(value) = app.edit().putBoolean(KEY_OCR_INDEX_ENABLED, value).apply()

    // --- Retained counters (survive reinstall via backup rules) ---

    var totalScreenshotsDeleted: Long
        get() = retain.getLong(RETAIN_DELETED, 0L)
        set(value) = retain.edit().putLong(RETAIN_DELETED, value).apply()

    var totalMemorySavedBytes: Long
        get() = retain.getLong(RETAIN_MEMORY, 0L)
        set(value) = retain.edit().putLong(RETAIN_MEMORY, value).apply()

    companion object {
        const val APP_PREFS = "app_preferences"
        const val RETAIN_PREFS = "retain_app_preferences"

        const val KEY_SERVICE_ENABLED = "service_enabled"
        const val KEY_LANGUAGE = "language"
        const val KEY_PREMIUM = "have_premium_purchased"
        const val KEY_ADS_REMOVED = "have_ads_removed"
        const val KEY_REFILL_EXPIRY = "refill_expiry_date"
        const val KEY_TIME_TILL_NO_ADS = "time_till_no_ads"
        const val KEY_NO_DIALOG_AUTO_DELETE = "no_dialog_auto_delete_enabled"
        const val KEY_SCREENSHOT_TREE_URI = "screenshot_folder_tree_uri"
        const val KEY_TIME_CHIPS_SECONDS = "time_chips_seconds"
        const val KEY_OCR_INDEX_ENABLED = "ocr_index_enabled"

        const val RETAIN_DELETED = "total_screenshots_deleted"
        const val RETAIN_MEMORY = "total_memory_saved"
    }

    private object TimeChipsDefaults {
        val FREE = dev.snapttl.domain.TimeChips.FREE
    }
}
