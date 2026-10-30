package dev.snapttl.ui.scheduled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.snapttl.data.expiry.ExpiryQueueItem
import dev.snapttl.di.Services
import dev.snapttl.service.CaptureActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScheduledViewModel(private val services: Services) : ViewModel() {

    private val _items = MutableStateFlow<List<ExpiryQueueItem>>(emptyList())
    val items: StateFlow<List<ExpiryQueueItem>> = _items.asStateFlow()

    init {
        viewModelScope.launch {
            services.expiryRepo.observeQueue().collect { _items.value = it }
        }
    }

    fun deleteNow(item: ExpiryQueueItem) {
        viewModelScope.launch {
            CaptureActions.deleteNow(services, item.screenshotId)
        }
    }

    fun cancel(item: ExpiryQueueItem) {
        viewModelScope.launch {
            services.expiryRepo.cancel(item.id)
            services.analytics.logEvent("timer_cancelled")
        }
    }
}
