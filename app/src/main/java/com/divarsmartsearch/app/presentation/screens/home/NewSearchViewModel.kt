package com.divarsmartsearch.app.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.divarsmartsearch.app.domain.model.BlockedPhoneNumber
import com.divarsmartsearch.app.domain.model.PropertyType
import com.divarsmartsearch.app.domain.model.SavedSearchDraft
import com.divarsmartsearch.app.domain.model.SearchStatus
import com.divarsmartsearch.app.domain.model.toDraft
import com.divarsmartsearch.app.domain.usecase.AddBlockedNumberUseCase
import com.divarsmartsearch.app.domain.usecase.GetBlockedNumbersUseCase
import com.divarsmartsearch.app.domain.usecase.GetSearchByIdUseCase
import com.divarsmartsearch.app.domain.usecase.RemoveBlockedNumberUseCase
import com.divarsmartsearch.app.domain.usecase.SaveSearchUseCase
import com.divarsmartsearch.app.service.BackgroundScanController
import com.divarsmartsearch.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewSearchUiState(
    val draft: SavedSearchDraft = SavedSearchDraft(),
    val editingId: Int? = null,
    val isLoadingForEdit: Boolean = false,
    val formError: String? = null,
    val blockedNumbers: List<BlockedPhoneNumber> = emptyList(),
    val isLoadingBlockedNumbers: Boolean = false,
    val newPhoneNumber: String = "",
    val newPhoneNote: String = "",
    val addPhoneError: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val didSaveSuccessfully: Boolean = false,
) {
    val isEditMode: Boolean get() = editingId != null
}

/**
 * Scoped to the "new_search_graph" / "edit_search_graph" navigation graph so
 * both step screens (filters form, then permanent-filters review) share the
 * same draft.
 */
@HiltViewModel
class NewSearchViewModel @Inject constructor(
    private val saveSearchUseCase: SaveSearchUseCase,
    private val getSearchByIdUseCase: GetSearchByIdUseCase,
    private val getBlockedNumbersUseCase: GetBlockedNumbersUseCase,
    private val addBlockedNumberUseCase: AddBlockedNumberUseCase,
    private val removeBlockedNumberUseCase: RemoveBlockedNumberUseCase,
    private val backgroundScanController: BackgroundScanController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewSearchUiState())
    val uiState: StateFlow<NewSearchUiState> = _uiState.asStateFlow()

    /**
     * Loads an existing search into the form for editing. Safe to call
     * multiple times (e.g. on recomposition) — only fetches once per
     * ViewModel instance.
     */
    fun loadForEdit(searchId: Int) {
        if (_uiState.value.editingId == searchId) return // already loaded
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingForEdit = true, editingId = searchId) }
            when (val result = getSearchByIdUseCase(searchId)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(draft = result.data.toDraft(), isLoadingForEdit = false)
                }
                is AppResult.Error -> _uiState.update {
                    it.copy(isLoadingForEdit = false, formError = result.message)
                }
                AppResult.Loading -> Unit
            }
        }
    }

    // --- Step 1: filters form ---

    fun updateName(value: String) = updateDraft { it.copy(name = value) }
    fun updateSearchUrl(value: String) = updateDraft { it.copy(searchUrl = value) }
    fun updateMinPrice(value: String) = updateDraft { it.copy(minPrice = value) }
    fun updateMaxPrice(value: String) = updateDraft { it.copy(maxPrice = value) }
    fun updateMinArea(value: String) = updateDraft { it.copy(minArea = value) }
    fun updateMaxArea(value: String) = updateDraft { it.copy(maxArea = value) }
    fun updateMaxPricePerMeter(value: String) = updateDraft { it.copy(maxPricePerMeter = value) }
    fun updateCity(value: String) = updateDraft { it.copy(city = value) }
    fun updateNeighborhood(value: String) = updateDraft { it.copy(neighborhood = value) }
    fun updatePropertyType(value: PropertyType?) = updateDraft { it.copy(propertyType = value) }
    fun updateMaxListingAgeHours(value: String) = updateDraft { it.copy(maxListingAgeHours = value) }

    private inline fun updateDraft(transform: (SavedSearchDraft) -> SavedSearchDraft) {
        _uiState.update { it.copy(draft = transform(it.draft), formError = null) }
    }

    /** Validates step 1 locally before letting navigation proceed to step 2. */
    fun validateStepOne(): Boolean {
        val draft = _uiState.value.draft
        val error = when {
            draft.name.isBlank() -> "لطفاً یک نام برای جستجو وارد کنید"
            draft.searchUrl.isBlank() -> "لطفاً لینک جستجوی دیوار را وارد کنید"
            !draft.searchUrl.startsWith("http") -> "لینک وارد شده معتبر نیست"
            else -> null
        }
        _uiState.update { it.copy(formError = error) }
        return error == null
    }

    // --- Step 2: permanent filters (blocked numbers) ---

    fun loadBlockedNumbers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBlockedNumbers = true) }
            when (val result = getBlockedNumbersUseCase()) {
                is AppResult.Success -> _uiState.update {
                    it.copy(blockedNumbers = result.data, isLoadingBlockedNumbers = false)
                }
                is AppResult.Error -> _uiState.update {
                    it.copy(isLoadingBlockedNumbers = false)
                }
                AppResult.Loading -> Unit
            }
        }
    }

    fun updateNewPhoneNumber(value: String) =
        _uiState.update { it.copy(newPhoneNumber = value, addPhoneError = null) }

    fun updateNewPhoneNote(value: String) = _uiState.update { it.copy(newPhoneNote = value) }

    fun addBlockedNumber() {
        val state = _uiState.value
        viewModelScope.launch {
            when (val result = addBlockedNumberUseCase(state.newPhoneNumber, state.newPhoneNote.ifBlank { null })) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        blockedNumbers = it.blockedNumbers + result.data,
                        newPhoneNumber = "",
                        newPhoneNote = "",
                        addPhoneError = null,
                    )
                }
                is AppResult.Error -> _uiState.update { it.copy(addPhoneError = result.message) }
                AppResult.Loading -> Unit
            }
        }
    }

    /** Same as [addBlockedNumber] but for a number picked straight from the system Contact Picker. */
    fun addBlockedNumberFromContact(phoneNumber: String, contactName: String?) {
        viewModelScope.launch {
            when (val result = addBlockedNumberUseCase(phoneNumber, contactName)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(blockedNumbers = it.blockedNumbers + result.data, addPhoneError = null)
                }
                is AppResult.Error -> _uiState.update { it.copy(addPhoneError = result.message) }
                AppResult.Loading -> Unit
            }
        }
    }

    fun removeBlockedNumber(id: Int) {
        viewModelScope.launch {
            when (removeBlockedNumberUseCase(id)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(blockedNumbers = it.blockedNumbers.filterNot { n -> n.id == id })
                }
                else -> Unit
            }
        }
    }

    // --- Final save ---

    fun saveSearch() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = null) }
            val state = _uiState.value
            when (val result = saveSearchUseCase(state.draft, editingId = state.editingId)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isSaving = false, didSaveSuccessfully = true) }
                    // New searches are created active by default (and an
                    // edited one may already be active) — same fix as the
                    // list screen's switch, so a freshly created search
                    // starts being watched immediately instead of silently
                    // doing nothing. See BackgroundScanController.
                    if (result.data.status == SearchStatus.ACTIVE) {
                        backgroundScanController.ensureRunning()
                    }
                }
                is AppResult.Error -> _uiState.update {
                    it.copy(isSaving = false, saveError = result.message)
                }
                AppResult.Loading -> Unit
            }
        }
    }

    /** Resets the draft after a successful save so the form is fresh for next time. */
    fun resetAfterSave() {
        _uiState.update { NewSearchUiState() }
    }
}
