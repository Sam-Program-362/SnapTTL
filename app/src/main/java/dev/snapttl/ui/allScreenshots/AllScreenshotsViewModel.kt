package dev.snapttl.ui.allScreenshots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.snapttl.data.repository.ScreenshotMedia
import dev.snapttl.di.Services
import dev.snapttl.domain.EntitlementState
import dev.snapttl.domain.TimeChips
import dev.snapttl.service.CaptureActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AllScreenshotsUiState(
    val items: List<ScreenshotMedia> = emptyList(),
    val scheduledIds: Set<Long> = emptySet(),
    val selected: Set<Long> = emptySet(),
    val allowedChips: List<Long> = emptyList()
)

class AllScreenshotsViewModel(private val services: Services) : ViewModel() {

    private val _uiState = MutableStateFlow(AllScreenshotsUiState())
    val uiState: StateFlow<AllScreenshotsUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            services.expiryRepo.observeQueue().collect { queue ->
                _uiState.update { it.copy(scheduledIds = queue.mapTo(mutableSetOf()) { it.screenshotId }) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val unlocked = entitlement().hasUnlockedFeatures(System.currentTimeMillis())
            _uiState.update {
                it.copy(
                    items = services.screenshotRepo.queryScreenshots(),
                    allowedChips = TimeChips.allowed(unlocked)
                )
            }
        }
    }

    fun toggleSelected(id: Long) {
        _uiState.update { state ->
            val current = state.selected
            state.copy(selected = if (id in current) current - id else current + id)
        }
    }

    fun selectAll() {
        _uiState.update { it.copy(selected = it.items.map { m -> m.id }.toSet()) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selected = emptySet()) }
    }

    /**
     * Schedules every selected screenshot for deletion [seconds] from now. Free-tier
     * rules are enforced (15–45 minutes) before anything is armed.
     */
    fun scheduleForSelected(seconds: Long) {
        val ids = _uiState.value.selected.toList()
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!entitlement().isValidDeletionScheduleSeconds(seconds, now)) return
        viewModelScope.launch {
            ids.forEach { id -> services.expiryRepo.scheduleIn(id, seconds) }
            services.analytics.logEvent("bulk_schedule_delete", mapOf("selected_time" to seconds))
            _uiState.update { it.copy(selected = emptySet()) }
        }
    }

    fun cancelTimersForSelected() {
        val ids = _uiState.value.selected.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { services.expiryRepo.cancelByScreenshot(it) }
            services.analytics.logEvent("bulk_cancel_timers")
            _uiState.update { it.copy(selected = emptySet()) }
        }
    }

    fun deleteSelected() {
        val ids = _uiState.value.selected.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { CaptureActions.deleteNow(services, it) }
            refresh()
            _uiState.update { it.copy(selected = emptySet()) }
        }
    }

    private fun entitlement() = EntitlementState(
        premiumPurchased = services.prefs.havePremiumPurchased,
        adsRemoved = services.prefs.haveAdsRemoved,
        refillExpiryEpochMs = services.prefs.refillExpiryEpochMs
    )
}
