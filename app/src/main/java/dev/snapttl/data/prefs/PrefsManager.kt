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

    var noDialogAutoDeleteEnabled: Boolean
        get() = app.getBoolean(KEY_NO_DIALOG_AUTO_DELETE, false)
        set(value) = app.edit().putBoolean(KEY_NO_DIALOG_AUTO_DELETE, value).apply()

    var screenshotFolderTreeUri: String?
        get() = app.getString(KEY_SCREENSHOT_TREE_URI, null)
        set(value) = app.edit().putString(KEY_SCREENSHOT_TREE_URI, value).apply()

    /**
     * The user's own quick-delete options (chips), in seconds — the one-tap
     * buttons on the floating overlay. See [dev.snapttl.domain.DeletionDuration].
     */
    var customDurations: List<Long>
        get() {
            val parsed = dev.snapttl.domain.DeletionDuration.decode(
                app.getString(KEY_CUSTOM_DURATIONS, null)
            )
            return parsed.ifEmpty { dev.snapttl.domain.DeletionDuration.DEFAULT_CHIPS }
        }
        set(value) = app.edit()
            .putString(KEY_CUSTOM_DURATIONS, dev.snapttl.domain.DeletionDuration.encode(value))
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
        const val KEY_NO_DIALOG_AUTO_DELETE = "no_dialog_auto_delete_enabled"
        const val KEY_SCREENSHOT_TREE_URI = "screenshot_folder_tree_uri"
        const val KEY_CUSTOM_DURATIONS = "custom_durations_seconds"
        const val KEY_OCR_INDEX_ENABLED = "ocr_index_enabled"

        const val RETAIN_DELETED = "total_screenshots_deleted"
        const val RETAIN_MEMORY = "total_memory_saved"
    }
}
