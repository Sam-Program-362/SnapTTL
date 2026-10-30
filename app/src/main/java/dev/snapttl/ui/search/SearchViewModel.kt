package dev.snapttl.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.snapttl.data.search.SearchIndexItem
import dev.snapttl.di.Services
import dev.snapttl.worker.OcrIndexWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<SearchIndexItem> = emptyList(),
    val indexing: Boolean = false,
    val ocrIndexEnabled: Boolean = true
)

/**
 * Search over the on-device OCR + label index. The live query is bound with
 * [flatMapLatest] so each keystroke replaces (and cancels) the previous collector,
 * and indexing runs as [OcrIndexWorker] WorkManager work — never in the ViewModel
 * scope.
 */
class SearchViewModel(application: Application, private val services: Services) :
    AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val results = _uiState
        .map { it.query }
        .flatMapLatest { query -> services.searchRepo.observeSearch(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val indexing: StateFlow<Boolean> = WorkManager.getInstance(application)
        .getWorkInfosForUniqueWorkFlow(OcrIndexWorker.UNIQUE_NAME)
        .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            results.collect { items ->
                _uiState.value = _uiState.value.copy(results = items)
            }
        }
        _uiState.value = _uiState.value.copy(
            ocrIndexEnabled = services.prefs.ocrIndexEnabled
        )
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun indexAll() {
        if (!services.prefs.ocrIndexEnabled) return
        viewModelScope.launch {
            services.searchRepo.enqueueIndexWork(getApplication<Application>())
        }
    }

    fun setOcrIndexEnabled(enabled: Boolean) {
        services.prefs.ocrIndexEnabled = enabled
        _uiState.value = _uiState.value.copy(ocrIndexEnabled = enabled)
    }
}
