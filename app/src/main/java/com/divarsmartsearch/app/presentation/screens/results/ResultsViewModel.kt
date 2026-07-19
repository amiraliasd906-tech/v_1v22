package com.divarsmartsearch.app.presentation.screens.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.divarsmartsearch.app.domain.model.Listing
import com.divarsmartsearch.app.domain.usecase.AddBlockedNumberUseCase
import com.divarsmartsearch.app.domain.usecase.ObserveVisibleListingsUseCase
import com.divarsmartsearch.app.domain.usecase.MarkListingSeenUseCase
import com.divarsmartsearch.app.domain.usecase.ReapplyFiltersUseCase
import com.divarsmartsearch.app.domain.usecase.RejectListingUseCase
import com.divarsmartsearch.app.domain.usecase.SaveListingUseCase
import com.divarsmartsearch.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResultsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val listings: List<Listing> = emptyList(),
    val error: String? = null,
    val snackbarMessage: String? = null,
)

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val observeVisibleListingsUseCase: ObserveVisibleListingsUseCase,
    private val markListingSeenUseCase: MarkListingSeenUseCase,
    private val saveListingUseCase: SaveListingUseCase,
    private val rejectListingUseCase: RejectListingUseCase,
    private val addBlockedNumberUseCase: AddBlockedNumberUseCase,
    private val reapplyFiltersUseCase: ReapplyFiltersUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultsUiState())
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    private var loadJob: kotlinx.coroutines.Job? = null

    init {
        load()
    }

    /**
     * Subscribes to the live listings Flow for the lifetime of this
     * ViewModel, instead of fetching once. The background scan service
     * inserts new listings on its own schedule, with nobody looking at the
     * Results screen at the time — a one-shot fetch on init only ever
     * reflects whatever existed at that exact instant, so anything the
     * scanner adds afterwards silently never showed up.
     */
    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            observeVisibleListingsUseCase()
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .onEach { listings -> _uiState.update { it.copy(isLoading = false, listings = listings) } }
                .collect()
        }
    }

    /**
     * User-triggered refresh: re-runs the filter pipeline (fresh keyword
     * filters, fresh owner-detection threshold) against whatever is
     * currently in the results list, with no protection this time — any
     * listing that no longer passes gets hidden from the results and shows
     * up in the Rejected history tab, just like a manual reject. The list
     * itself updates via the already-live [observeVisibleListingsUseCase]
     * Flow once the underlying rows change — this just triggers that
     * re-check and shows a spinner while it runs, separate from the initial
     * full-screen loading state so the list doesn't disappear during the
     * refresh.
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            when (val result = reapplyFiltersUseCase()) {
                is AppResult.Error -> _uiState.update {
                    it.copy(isRefreshing = false, snackbarMessage = result.message)
                }
                is AppResult.Success -> _uiState.update {
                    // Always show something, even when nothing changed —
                    // otherwise a press that finds no new matches (the
                    // normal case on the 2nd/3rd press, since the 1st
                    // press already cleared everything out) shows no
                    // snackbar at all and looks like the button silently
                    // stopped working.
                    val message = if (result.data > 0) {
                        "${result.data} آگهی به حذف‌شده‌ها منتقل شد"
                    } else {
                        "به‌روزرسانی انجام شد — موردی برای حذف پیدا نشد"
                    }
                    it.copy(isRefreshing = false, snackbarMessage = message)
                }
                AppResult.Loading -> _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun onOpened(listingId: Int) {
        viewModelScope.launch { markListingSeenUseCase(listingId) }
    }

    fun onSave(listingId: Int) {
        viewModelScope.launch {
            saveListingUseCase(listingId)
            removeFromList(listingId)
        }
    }

    fun onReject(listingId: Int) {
        viewModelScope.launch {
            rejectListingUseCase(listingId)
            removeFromList(listingId)
        }
    }

    fun onBlockPhoneNumber(phoneNumber: String) {
        viewModelScope.launch {
            when (val result = addBlockedNumberUseCase(phoneNumber, null)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(snackbarMessage = "شماره $phoneNumber مسدود شد") }
                    // Listings from this number will be hidden on next refresh.
                }
                is AppResult.Error -> _uiState.update { it.copy(snackbarMessage = result.message) }
                AppResult.Loading -> Unit
            }
        }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun removeFromList(listingId: Int) {
        _uiState.update { state -> state.copy(listings = state.listings.filterNot { it.id == listingId }) }
    }
}
