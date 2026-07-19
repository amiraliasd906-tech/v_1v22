package com.divarsmartsearch.app.domain.usecase

import com.divarsmartsearch.app.domain.model.PropertyType
import com.divarsmartsearch.app.domain.model.SavedSearch
import com.divarsmartsearch.app.domain.model.SavedSearchDraft
import com.divarsmartsearch.app.domain.repository.SearchRepository
import com.divarsmartsearch.app.util.AppResult
import javax.inject.Inject

class GetSearchesUseCase @Inject constructor(
    private val repository: SearchRepository
) {
    suspend operator fun invoke(): AppResult<List<SavedSearch>> = repository.getSearches()
}

class GetSearchByIdUseCase @Inject constructor(
    private val repository: SearchRepository
) {
    suspend operator fun invoke(id: Int): AppResult<SavedSearch> = repository.getSearchById(id)
}

/**
 * Validates a [SavedSearchDraft] and, if valid, creates the search.
 * Keeping validation here (rather than in the ViewModel) means the same
 * rules apply consistently to both "create" and "edit" flows.
 */
class SaveSearchUseCase @Inject constructor(
    private val repository: SearchRepository
) {
    suspend operator fun invoke(
        draft: SavedSearchDraft,
        editingId: Int? = null,
    ): AppResult<SavedSearch> {
        val validationError = validate(draft)
        if (validationError != null) {
            return AppResult.Error(validationError)
        }

        return if (editingId != null) {
            repository.updateSearch(editingId, draft)
        } else {
            repository.createSearch(draft)
        }
    }

    private fun validate(draft: SavedSearchDraft): String? {
        if (draft.name.isBlank()) return "لطفاً یک نام برای جستجو وارد کنید"
        if (draft.searchUrl.isBlank()) return "لطفاً لینک جستجوی دیوار را وارد کنید"
        if (!draft.searchUrl.startsWith("https://divar.ir") &&
            !draft.searchUrl.startsWith("http://divar.ir")
        ) {
            return "لینک باید از سایت divar.ir باشد"
        }

        val min = draft.minPrice.toDoubleOrNull()
        val max = draft.maxPrice.toDoubleOrNull()
        if (min != null && max != null && min > max) {
            return "حداقل قیمت نمی‌تواند از حداکثر قیمت بیشتر باشد"
        }

        val minArea = draft.minArea.toDoubleOrNull()
        val maxArea = draft.maxArea.toDoubleOrNull()
        if (minArea != null && maxArea != null && minArea > maxArea) {
            return "حداقل متراژ نمی‌تواند از حداکثر متراژ بیشتر باشد"
        }

        return null
    }
}

class ToggleSearchUseCase @Inject constructor(
    private val repository: SearchRepository
) {
    suspend operator fun invoke(id: Int): AppResult<SavedSearch> = repository.toggleSearch(id)
}

class DeleteSearchUseCase @Inject constructor(
    private val repository: SearchRepository
) {
    suspend operator fun invoke(id: Int): AppResult<Unit> = repository.deleteSearch(id)
}

/** Maps a [PropertyType] to its Persian display label, used across screens. */
fun PropertyType.displayLabel(): String = when (this) {
    PropertyType.APARTMENT -> "آپارتمان"
    PropertyType.VILLA -> "ویلایی"
    PropertyType.LAND -> "زمین و کلنگی"
    PropertyType.OFFICE -> "اداری"
    PropertyType.SHOP -> "مغازه و تجاری"
    PropertyType.OTHER -> "سایر"
}
