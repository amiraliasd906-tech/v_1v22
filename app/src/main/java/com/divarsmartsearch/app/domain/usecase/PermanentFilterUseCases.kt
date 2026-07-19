package com.divarsmartsearch.app.domain.usecase

import com.divarsmartsearch.app.domain.model.BlockedPhoneNumber
import com.divarsmartsearch.app.domain.model.KeywordFilter
import com.divarsmartsearch.app.domain.repository.PermanentFilterRepository
import com.divarsmartsearch.app.util.AppResult
import javax.inject.Inject

class GetBlockedNumbersUseCase @Inject constructor(
    private val repository: PermanentFilterRepository
) {
    suspend operator fun invoke(): AppResult<List<BlockedPhoneNumber>> =
        repository.getBlockedNumbers()
}

class AddBlockedNumberUseCase @Inject constructor(
    private val repository: PermanentFilterRepository
) {
    suspend operator fun invoke(phoneNumber: String, note: String?): AppResult<BlockedPhoneNumber> {
        val digitsOnly = phoneNumber.filter { it.isDigit() }
        if (digitsOnly.length < 10) {
            return AppResult.Error("شماره تلفن معتبر نیست")
        }
        return repository.addBlockedNumber(phoneNumber, note)
    }
}

class RemoveBlockedNumberUseCase @Inject constructor(
    private val repository: PermanentFilterRepository
) {
    suspend operator fun invoke(id: Int): AppResult<Unit> = repository.removeBlockedNumber(id)
}

class GetKeywordFiltersUseCase @Inject constructor(
    private val repository: PermanentFilterRepository
) {
    suspend operator fun invoke(): AppResult<List<KeywordFilter>> = repository.getKeywordFilters()
}

class AddKeywordFilterUseCase @Inject constructor(
    private val repository: PermanentFilterRepository
) {
    suspend operator fun invoke(
        label: String,
        keyword: String,
        category: String = "custom",
        filterType: String = "exclude",
    ): AppResult<KeywordFilter> {
        if (keyword.isBlank()) return AppResult.Error("کلمه نمی‌تواند خالی باشد")
        return repository.addKeywordFilter(label, keyword, category, filterType)
    }
}

class RemoveKeywordFilterUseCase @Inject constructor(
    private val repository: PermanentFilterRepository
) {
    suspend operator fun invoke(id: Int): AppResult<Unit> = repository.removeKeywordFilter(id)
}

class SetKeywordFilterEnabledUseCase @Inject constructor(
    private val repository: PermanentFilterRepository
) {
    suspend operator fun invoke(id: Int, enabled: Boolean): AppResult<Unit> =
        repository.setKeywordFilterEnabled(id, enabled)
}
