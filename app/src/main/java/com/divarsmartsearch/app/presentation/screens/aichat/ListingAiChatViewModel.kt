package com.divarsmartsearch.app.presentation.screens.aichat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.divarsmartsearch.app.data.ai.ListingAiAssistant
import com.divarsmartsearch.app.domain.model.Listing
import com.divarsmartsearch.app.domain.usecase.GetSettingsUseCase
import com.divarsmartsearch.app.domain.usecase.GetVisibleListingsUseCase
import com.divarsmartsearch.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(val fromUser: Boolean, val text: String)

data class ListingAiChatUiState(
    val isLoadingListing: Boolean = true,
    val listing: Listing? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isAsking: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ListingAiChatViewModel @Inject constructor(
    private val getVisibleListingsUseCase: GetVisibleListingsUseCase,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val listingAiAssistant: ListingAiAssistant,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val listingId: Int = checkNotNull(savedStateHandle["listingId"])

    private val _uiState = MutableStateFlow(ListingAiChatUiState())
    val uiState: StateFlow<ListingAiChatUiState> = _uiState.asStateFlow()

    init {
        loadListing()
    }

    private fun loadListing() {
        viewModelScope.launch {
            when (val result = getVisibleListingsUseCase()) {
                is AppResult.Success -> {
                    val listing = result.data.firstOrNull { it.id == listingId }
                    _uiState.update { it.copy(isLoadingListing = false, listing = listing) }
                }
                is AppResult.Error -> _uiState.update {
                    it.copy(isLoadingListing = false, error = result.message)
                }
                AppResult.Loading -> Unit
            }
        }
    }

    fun ask(question: String) {
        val listing = _uiState.value.listing ?: return
        if (question.isBlank() || _uiState.value.isAsking) return

        _uiState.update {
            it.copy(
                isAsking = true,
                error = null,
                messages = it.messages + ChatMessage(fromUser = true, text = question),
            )
        }

        viewModelScope.launch {
            val settingsResult = getSettingsUseCase()
            val settings = (settingsResult as? AppResult.Success)?.data

            when (val result = listingAiAssistant.askQuestion(
                listing = listing,
                question = question,
                apiKey = settings?.anthropicApiKey,
                model = settings?.anthropicModel ?: "claude-haiku-4-5-20251001",
            )) {
                is ListingAiAssistant.AskResult.Success -> _uiState.update {
                    it.copy(
                        isAsking = false,
                        messages = it.messages + ChatMessage(fromUser = false, text = result.answer),
                    )
                }
                is ListingAiAssistant.AskResult.Failure -> _uiState.update {
                    it.copy(isAsking = false, error = result.message)
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
