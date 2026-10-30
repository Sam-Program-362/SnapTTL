package dev.snapttl.ui.navigation

/** Navigation routes, clean-room equivalent of Mark's Compose NavHost routes. */
object Routes {
    const val HOME = "home"
    const val SCHEDULED = "scheduled"
    const val SCREENSHOTS = "screenshots"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val EASTER_EGG = "easter_egg"
}

object Tabs {
    val entries = listOf(
        Routes.HOME,
        Routes.SCHEDULED,
        Routes.SCREENSHOTS,
        Routes.SEARCH
    )
}
