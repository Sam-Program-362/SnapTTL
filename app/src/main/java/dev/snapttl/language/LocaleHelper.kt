package dev.snapttl.language

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Applies the persisted in-app language to the process resources. */
object LocaleHelper {

    fun apply(context: Context, code: String) {
        val language = AppLanguage.fromCode(code)
        if (language == AppLanguage.SYSTEM_DEFAULT) return
        val locale = Locale(language.code)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setToDefaults()
        config.locale = locale
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}
