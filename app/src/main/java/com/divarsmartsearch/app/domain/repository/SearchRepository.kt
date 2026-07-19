package com.divarsmartsearch.app.domain.repository

import com.divarsmartsearch.app.domain.model.SavedSearch
import com.divarsmartsearch.app.domain.model.SavedSearchDraft
import com.divarsmartsearch.app.util.AppResult

interface SearchRepository {
    suspend fun getSearches(): AppResult<List<SavedSearch>>
    suspend fun getSearchById(id: Int): AppResult<SavedSearch>
    suspend fun createSearch(draft: SavedSearchDraft): AppResult<SavedSearch>
    suspend fun updateSearch(id: Int, draft: SavedSearchDraft): AppResult<SavedSearch>
    suspend fun toggleSearch(id: Int): AppResult<SavedSearch>
    suspend fun deleteSearch(id: Int): AppResult<Unit>
}
