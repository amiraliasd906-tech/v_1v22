package com.divarsmartsearch.app.presentation.screens.searches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.divarsmartsearch.app.domain.model.SavedSearch
import com.divarsmartsearch.app.domain.model.SearchStatus
import com.divarsmartsearch.app.domain.usecase.DeleteSearchUseCase
import com.divarsmartsearch.app.domain.usecase.GetSearchesUseCase
import com.divarsmartsearch.app.domain.usecase.ToggleSearchUseCase
import com.divarsmartsearch.app.service.BackgroundScanController
import com.divarsmartsearch.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchListUiState(
    val isLoading: Boolean = true,
    val searches: List<SavedSearch> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class SearchListViewModel @Inject constructor(
    private val getSearchesUseCase: GetSearchesUseCase,
    private val toggleSearchUseCase: ToggleSearchUseCase,
    private val deleteSearchUseCase: DeleteSearchUseCase,
    private val backgroundScanController: BackgroundScanController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchListUiState())
    val uiState: StateFlow<SearchListUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = getSearchesUseCase()) {
                is AppResult.Success -> _uiState.update {
                    it.copy(isLoading = false, searches = result.data)
                }
                is AppResult.Error -> _uiState.update {
                    it.copy(isLoading = false, error = result.message)
                }
                AppResult.Loading -> Unit
            }
        }
    }

    fun toggle(id: Int) {
        viewModelScope.launch {
            when (val result = toggleSearchUseCase(id)) {
                is AppResult.Success -> {
                    _uiState.update { state ->
                        state.copy(searches = state.searches.map { if (it.id == id) result.data else it })
                    }
                    // Bug fix: flipping this switch on used to just flip a
                    // database column with nothing actually watching for
                    // new listings behind it — see BackgroundScanController.
                    if (result.data.status == SearchStatus.ACTIVE) {
                        backgroundScanController.ensureRunning()
                    }
                }
                else -> Unit
            }
        }
    }

    fun delete(id: Int) {
        viewModelScope.launch {
            when (deleteSearchUseCase(id)) {
                is AppResult.Success -> _uiState.update { state ->
                    state.copy(searches = state.searches.filterNot { it.id == id })
                }
                else -> Unit
            }
        }
    }
}
