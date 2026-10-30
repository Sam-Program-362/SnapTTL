package dev.snapttl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.staticCompositionLocalOf
import dev.snapttl.di.Services
import dev.snapttl.ui.allScreenshots.AllScreenshotsViewModel
import dev.snapttl.ui.home.HomeViewModel
import dev.snapttl.ui.scheduled.ScheduledViewModel
import dev.snapttl.ui.search.SearchViewModel
import dev.snapttl.ui.settings.SettingsViewModel

/** Manual dependency injection for the Compose screens. */
class ViewModelFactory(private val services: Services) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(services) as T
            modelClass.isAssignableFrom(ScheduledViewModel::class.java) -> ScheduledViewModel(services) as T
            modelClass.isAssignableFrom(AllScreenshotsViewModel::class.java) ->
                AllScreenshotsViewModel(services) as T
            modelClass.isAssignableFrom(SearchViewModel::class.java) ->
                SearchViewModel(services.context as android.app.Application, services) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(services) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}

val LocalSnapTtlFactory = staticCompositionLocalOf<ViewModelFactory> {
    error("SnapTTL ViewModelFactory is not provided")
}
