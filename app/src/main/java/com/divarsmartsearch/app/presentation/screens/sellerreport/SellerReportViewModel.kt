package com.divarsmartsearch.app.presentation.screens.sellerreport

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.divarsmartsearch.app.domain.model.SellerReport
import com.divarsmartsearch.app.domain.usecase.GetSellerReportUseCase
import com.divarsmartsearch.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SellerReportUiState(
    val isLoading: Boolean = true,
    val report: SellerReport? = null,
    val error: String? = null,
)

@HiltViewModel
class SellerReportViewModel @Inject constructor(
    private val getSellerReportUseCase: GetSellerReportUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val phoneNumber: String = checkNotNull(savedStateHandle["phoneNumber"])

    private val _uiState = MutableStateFlow(SellerReportUiState())
    val uiState: StateFlow<SellerReportUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = getSellerReportUseCase(phoneNumber)) {
                is AppResult.Success -> _uiState.update { it.copy(isLoading = false, report = result.data) }
                is AppResult.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                AppResult.Loading -> Unit
            }
        }
    }
}
