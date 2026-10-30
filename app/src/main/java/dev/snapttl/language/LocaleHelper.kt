package dev.snapttl.language

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Applies the persisted in-app language.
 *
 * - API 33+: the platform per-app locale (`LocaleManager.setApplicationLocales`), so
 *   the system handles persistence, activity recreation and per-app language
 *   settings. The stored preference mirrors the system state for Settings UI.
 * - API 26–32: locale applied via configuration wrapping; activities must use
 *   [wrap] in `attachBaseContext` and recreate after a change.
 */
object LocaleHelper {

    fun apply(context: Context, code: String) {
        val language = AppLanguage.fromCode(code)
        if (language == AppLanguage.SYSTEM_DEFAULT) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                    LocaleList.getEmptyLocaleList()
            }
            return
        }
        val locale = Locale.forLanguageTag(language.code)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java) ?: return
            manager.applicationLocales = LocaleList(locale)
        } else {
            @Suppress("DEPRECATION")
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }
    }

    /** Base-context wrapper for API < 33 so activities pick up the stored locale. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val language = AppLanguage.fromCode(persistedCode(base))
        if (language == AppLanguage.SYSTEM_DEFAULT) return base
        val locale = Locale.forLanguageTag(language.code)
        @Suppress("DEPRECATION")
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    private fun persistedCode(context: Context): String =
        context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            .getString("language", "system") ?: "system"
}
