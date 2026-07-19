package com.divarsmartsearch.app.presentation.screens.webview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.divarsmartsearch.app.data.webview.ExtractedListing
import com.divarsmartsearch.app.data.webview.ListingIngestionService
import com.divarsmartsearch.app.domain.usecase.GetSearchByIdUseCase
import com.divarsmartsearch.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class DivarWebViewUiState(
    val isLoadingSearch: Boolean = true,
    val searchName: String = "",
    val startUrl: String = "https://divar.ir",
    val error: String? = null,
    val statusMessage: String? = null,
    val totalPassed: Int = 0,
)

@HiltViewModel
class DivarWebViewViewModel @Inject constructor(
    private val getSearchByIdUseCase: GetSearchByIdUseCase,
    private val ingestionService: ListingIngestionService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DivarWebViewUiState())
    val uiState: StateFlow<DivarWebViewUiState> = _uiState.asStateFlow()

    private var savedSearchId: Long = 0
    private val json = Json { ignoreUnknownKeys = true }

    // Bug fix: the page's extraction script re-fires every ~3 seconds and
    // resends everything currently on screen (not just new items), and
    // ingest() does a real network round-trip per new listing to fetch its
    // detail page — which routinely takes longer than 3 seconds once
    // several new listings show up in one scroll. Without this, the next
    // timer tick would launch a SECOND, overlapping ingest() call while the
    // first was still mid-flight; both would run `findByToken` before
    // either had inserted its rows, so the same new listings got detected
    // as "new" twice, double-fetched, and sometimes double-inserted. This
    // mutex makes every batch wait its turn instead of racing.
    private val ingestMutex = Mutex()

    fun load(searchId: Int) {
        savedSearchId = searchId.toLong()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSearch = true) }
            when (val result = getSearchByIdUseCase(searchId)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        isLoadingSearch = false,
                        searchName = result.data.name,
                        startUrl = result.data.searchUrl,
                    )
                }
                is AppResult.Error -> _uiState.update {
                    it.copy(isLoadingSearch = false, error = result.message)
                }
                AppResult.Loading -> Unit
            }
        }
    }

    /** Called from the JS bridge whenever the injected script finds listings on the current page. */
    fun onListingsExtracted(rawJson: String) {
        viewModelScope.launch {
            try {
                val listings = json.decodeFromString<List<ExtractedListing>>(rawJson)
                if (listings.isEmpty()) {
                    // Bug fix: previously returned here silently, which
                    // (combined with the JS side only calling the bridge
                    // when it found something) made "the page has zero
                    // matching listings right now" indistinguishable from
                    // "extraction never ran at all". Report it honestly.
                    _uiState.update { it.copy(statusMessage = "این بار هیچ آگهی‌ای روی صفحه پیدا نشد") }
                    return@launch
                }
                val result = ingestMutex.withLock { ingestionService.ingest(savedSearchId, listings) }
                // Bug fix: this used to only update statusMessage when
                // passedFilters > 0, so if the page's extraction script
                // found nothing, or found listings but every single one
                // got rejected by the filters, the user saw no message at
                // all -- indistinguishable from the extraction never having
                // run in the first place. Always reporting the raw counts
                // (received / new / passed) turns that silent failure into
                // something the user can actually diagnose.
                // Bug fix: this text used to say "X مورد از فیلتر رد شد"
                // (X items were rejected by the filter), but result.passedFilters
                // is exactly the opposite -- it's how many SURVIVED every
                // filter stage (see IngestResult / ListingIngestionService's
                // `surviving.size`). The message was telling the user the
                // reverse of what actually happened: a search that worked
                // perfectly and passed 5 good listings through displayed as
                // if 5 had been rejected.
                _uiState.update {
                    it.copy(
                        statusMessage = "${result.received} آگهی از صفحه خونده شد، ${result.new} مورد جدید بود، ${result.passedFilters} مورد از فیلتر عبور کرد",
                        totalPassed = it.totalPassed + result.passedFilters,
                    )
                }
            } catch (e: Exception) {
                // Malformed JSON from the page — ignore this batch silently,
                // extraction will simply try again on the next cycle.
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
