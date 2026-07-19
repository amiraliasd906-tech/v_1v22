package com.divarsmartsearch.app.domain.repository

import com.divarsmartsearch.app.domain.model.BlockedPhoneNumber
import com.divarsmartsearch.app.domain.model.KeywordFilter
import com.divarsmartsearch.app.util.AppResult

interface PermanentFilterRepository {
    suspend fun getBlockedNumbers(): AppResult<List<BlockedPhoneNumber>>
    suspend fun addBlockedNumber(phoneNumber: String, note: String?): AppResult<BlockedPhoneNumber>
    suspend fun removeBlockedNumber(id: Int): AppResult<Unit>

    /** Every keyword filter, built-in + custom, in display order. */
    suspend fun getKeywordFilters(): AppResult<List<KeywordFilter>>

    /** Adds a brand-new, independent, custom keyword filter (starts enabled). */
    suspend fun addKeywordFilter(label: String, keyword: String, category: String = "custom", filterType: String = "exclude"): AppResult<KeywordFilter>

    /** Only ever removes a custom filter — built-in ones can be disabled but not deleted. */
    suspend fun removeKeywordFilter(id: Int): AppResult<Unit>

    suspend fun setKeywordFilterEnabled(id: Int, enabled: Boolean): AppResult<Unit>
}
