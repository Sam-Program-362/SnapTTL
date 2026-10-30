package dev.snapttl.language

/** In-app language options reconstructed from Mark's 22-language enum. */
enum class AppLanguage(val code: String, val displayName: String) {
    SYSTEM_DEFAULT("system", "System Default"),
    ENGLISH("en", "English"),
    SPANISH("es", "Spanish"),
    FRENCH("fr", "French"),
    GERMAN("de", "German"),
    ARABIC("ar", "Arabic"),
    URDU("ur", "Urdu"),
    HINDI("hi", "Hindi"),
    BENGALI("bn", "Bengali"),
    PORTUGUESE("pt", "Portuguese"),
    RUSSIAN("ru", "Russian"),
    JAPANESE("ja", "Japanese"),
    KOREAN("ko", "Korean"),
    SIMPLIFIED_CHINESE("zh-CN", "Simplified Chinese"),
    TRADITIONAL_CHINESE("zh-TW", "Traditional Chinese"),
    ITALIAN("it", "Italian"),
    TURKISH("tr", "Turkish"),
    INDONESIAN("id", "Indonesian"),
    VIETNAMESE("vi", "Vietnamese"),
    THAI("th", "Thai"),
    POLISH("pl", "Polish"),
    DUTCH("nl", "Dutch"),
    PERSIAN("fa", "Persian"),
    UKRAINIAN("uk", "Ukrainian"),
    TAMIL("ta", "Tamil");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            values().firstOrNull { it.code.equals(code, ignoreCase = true) } ?: SYSTEM_DEFAULT
    }
}
