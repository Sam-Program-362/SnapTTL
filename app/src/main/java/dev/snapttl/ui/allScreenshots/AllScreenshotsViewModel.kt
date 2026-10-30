package dev.snapttl.ui.allScreenshots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.snapttl.data.expiry.ExpiryQueueItem
import dev.snapttl.data.repository.ScreenshotMedia
import dev.snapttl.di.Services
import dev.snapttl.domain.TimeUnit
import dev.snapttl.service.CaptureActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AllScreenshotsUiState(
    val items: List<ScreenshotMedia> = emptyList(),
    val scheduledIds: Set<Long> = emptySet(),
    val selected: Set<Long> = emptySet(),
)

class AllScreenshotsViewModel(private val services: Services) : ViewModel() {

    private val _uiState = MutableStateFlow(AllScreenshotsUiState())
    val uiState: StateFlow<AllScreenshotsUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            services.expiryRepo.observeQueue().collect { queue ->
                _uiState.value = _uiState.value.copy(
                    scheduledIds = queue.mapTo(mutableSetOf()) { it.screenshotId }
                )
            }
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(
            items = services.screenshotRepo.queryScreenshots()
        )
    }

    fun toggleSelected(id: Long) {
        val current = _uiState.value.selected
        _uiState.value = _uiState.value.copy(
            selected = if (id in current) current - id else current + id
        )
    }

    /** Schedules every selected screenshot for deletion `minutes` from now. */
    fun scheduleForSelected(minutes: Int) {
        val ids = _uiState.value.selected.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            ids.forEach { id ->
                services.expiryRepo.upsert(
                    ExpiryQueueItem(
                        filePath = "",
                        deleteTimeEpochMs = now + minutes * 60_000L,
                        timeUnit = TimeUnit.MINUTES.name,
                        workManagerId = "snapttl_bulk_${id}_${now}",
                        createdAtEpochMillis = now,
                        screenshotId = id
                    )
                )
            }
            services.analytics.logEvent("bulk_schedule_delete", mapOf("selected_time" to minutes))
            _uiState.value = _uiState.value.copy(selected = emptySet())
        }
    }

    fun cancelTimersForSelected() {
        val ids = _uiState.value.selected.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { services.expiryRepo.cancelByScreenshot(it) }
            services.analytics.logEvent("bulk_cancel_timers")
            _uiState.value = _uiState.value.copy(selected = emptySet())
        }
    }

    fun deleteSelected() {
        val ids = _uiState.value.selected.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { CaptureActions.deleteNow(services, it) }
            refresh()
            _uiState.value = _uiState.value.copy(selected = emptySet())
        }
    }
}
