package com.divarsmartsearch.app.presentation.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.divarsmartsearch.app.domain.model.HistoryTab
import com.divarsmartsearch.app.domain.model.Listing
import com.divarsmartsearch.app.domain.usecase.AddBlockedNumberUseCase
import com.divarsmartsearch.app.domain.usecase.GetListingHistoryUseCase
import com.divarsmartsearch.app.domain.usecase.ObserveHistoryCountUseCase
import com.divarsmartsearch.app.domain.usecase.RejectListingUseCase
import com.divarsmartsearch.app.domain.usecase.RestoreListingUseCase
import com.divarsmartsearch.app.domain.usecase.SaveListingUseCase
import com.divarsmartsearch.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val selectedTab: HistoryTab = HistoryTab.SEEN,
    val isLoading: Boolean = true,
    val listings: List<Listing> = emptyList(),
    val error: String? = null,
    val snackbarMessage: String? = null,
    // Total count per tab (Seen/Saved/Rejected), across the *entire* dataset —
    // not just however many rows happen to be loaded/visible for the
    // currently selected tab — so every tab can show its real total at once.
    val tabCounts: Map<HistoryTab, Int> = emptyMap(),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getListingHistoryUseCase: GetListingHistoryUseCase,
    private val observeHistoryCountUseCase: ObserveHistoryCountUseCase,
    private val saveListingUseCase: SaveListingUseCase,
    private val rejectListingUseCase: RejectListingUseCase,
    private val restoreListingUseCase: RestoreListingUseCase,
    private val addBlockedNumberUseCase: AddBlockedNumberUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        load()
        observeTabCounts()
    }

    /**
     * Keeps every tab's total count live and independent of [selectedTab],
     * so switching tabs (or a save/reject happening elsewhere) never leaves
     * a stale count showing on a tab the user isn't currently looking at.
     */
    private fun observeTabCounts() {
        HistoryTab.entries.forEach { tab ->
            observeHistoryCountUseCase(tab)
                .catch { /* keep whatever count we last had rather than blanking it out */ }
                .onEach { count ->
                    _uiState.update { it.copy(tabCounts = it.tabCounts + (tab to count)) }
                }
                .launchIn(viewModelScope)
        }
    }

    fun selectTab(tab: HistoryTab) {
        if (tab == _uiState.value.selectedTab) return
        _uiState.update { it.copy(selectedTab = tab) }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = getListingHistoryUseCase(_uiState.value.selectedTab)) {
                is AppResult.Success -> _uiState.update { it.copy(isLoading = false, listings = result.data) }
                is AppResult.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                AppResult.Loading -> Unit
            }
        }
    }

    fun onSave(listingId: Int) {
        viewModelScope.launch {
            // Bug fix: this used to call saveListingUseCase and blindly
            // reload, ignoring the AppResult it returns. If the DB call
            // failed (e.g. a Room/FK error) nothing told the user and the
            // listing just stayed put with no error shown — from the
            // Seen tab it looked exactly like the Save button did nothing.
            // Now a failure surfaces as a snackbar instead of silently
            // no-oping, matching onBlockPhoneNumber's existing pattern.
            when (val result = saveListingUseCase(listingId)) {
                // Also confirm success with a snackbar: from the Seen tab,
                // saving makes the card disappear from the *current* list
                // (it no longer matches the "seen" status) with no other
                // visual change, which on its own reads as the button
                // having done nothing.
                is AppResult.Success -> {
                    _uiState.update { it.copy(snackbarMessage = "به ذخیره‌شده‌ها منتقل شد") }
                    load()
                }
                is AppResult.Error -> _uiState.update { it.copy(snackbarMessage = result.message) }
                AppResult.Loading -> Unit
            }
        }
    }

    fun onReject(listingId: Int) {
        viewModelScope.launch {
            when (val result = rejectListingUseCase(listingId)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(snackbarMessage = "رد شد") }
                    load()
                }
                is AppResult.Error -> _uiState.update { it.copy(snackbarMessage = result.message) }
                AppResult.Loading -> Unit
            }
        }
    }

    /**
     * Undo a reject. This is what the Rejected tab's second button now
     * calls instead of onReject: re-rejecting an already-rejected listing
     * changed nothing visible (the card just stayed put with no feedback),
     * which is exactly what made that button look broken. Restoring
     * actually does something the user can see: the card disappears from
     * this tab and the listing becomes visible again in Results.
     */
    fun onRestore(listingId: Int) {
        viewModelScope.launch {
            when (val result = restoreListingUseCase(listingId)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(snackbarMessage = "به نتایج بازگردانده شد") }
                    load()
                }
                is AppResult.Error -> _uiState.update { it.copy(snackbarMessage = result.message) }
                AppResult.Loading -> Unit
            }
        }
    }

    fun onBlockPhoneNumber(phoneNumber: String) {
        viewModelScope.launch {
            when (val result = addBlockedNumberUseCase(phoneNumber, null)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(snackbarMessage = "شماره $phoneNumber مسدود شد") }
                    load()
                }
                is AppResult.Error -> _uiState.update { it.copy(snackbarMessage = result.message) }
                AppResult.Loading -> Unit
            }
        }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
