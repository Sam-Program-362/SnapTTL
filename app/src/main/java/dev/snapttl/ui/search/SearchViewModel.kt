package dev.snapttl.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.snapttl.data.search.SearchIndexItem
import dev.snapttl.di.Services
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<SearchIndexItem> = emptyList(),
    val indexing: Boolean = false
)

class SearchViewModel(private val services: Services) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.collect { state ->
                services.searchRepo.observeSearch(state.query).collect { results ->
                    _uiState.value = _uiState.value.copy(results = results)
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun indexAll() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(indexing = true)
            services.searchRepo.indexAll()
            _uiState.value = _uiState.value.copy(indexing = false)
        }
    }
}
