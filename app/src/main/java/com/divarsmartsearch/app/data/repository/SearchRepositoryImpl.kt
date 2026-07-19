package com.divarsmartsearch.app.data.repository

import com.divarsmartsearch.app.data.local.dao.SavedSearchDao
import com.divarsmartsearch.app.data.local.toDomain
import com.divarsmartsearch.app.data.local.toEntity
import com.divarsmartsearch.app.domain.model.SavedSearch
import com.divarsmartsearch.app.domain.model.SavedSearchDraft
import com.divarsmartsearch.app.domain.repository.SearchRepository
import com.divarsmartsearch.app.util.AppResult
import com.divarsmartsearch.app.util.safeCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val dao: SavedSearchDao,
) : SearchRepository {

    override suspend fun getSearches(): AppResult<List<SavedSearch>> = safeCall {
        dao.getAll().map { it.toDomain() }
    }

    override suspend fun getSearchById(id: Int): AppResult<SavedSearch> = safeCall {
        dao.getById(id.toLong())?.toDomain()
            ?: throw NoSuchElementException("جستجو یافت نشد")
    }

    override suspend fun createSearch(draft: SavedSearchDraft): AppResult<SavedSearch> = safeCall {
        val entity = draft.toEntity()
        val newId = dao.insert(entity)
        entity.copy(id = newId).toDomain()
    }

    override suspend fun updateSearch(id: Int, draft: SavedSearchDraft): AppResult<SavedSearch> = safeCall {
        val existing = dao.getById(id.toLong()) ?: throw NoSuchElementException("جستجو یافت نشد")
        val updated = draft.toEntity(existingId = id.toLong()).copy(
            status = existing.status,
            createdAt = existing.createdAt,
            updatedAt = System.currentTimeMillis(),
        )
        dao.update(updated)
        updated.toDomain()
    }

    override suspend fun toggleSearch(id: Int): AppResult<SavedSearch> = safeCall {
        val existing = dao.getById(id.toLong()) ?: throw NoSuchElementException("جستجو یافت نشد")
        val updated = existing.copy(
            status = if (existing.status == "active") "paused" else "active",
            updatedAt = System.currentTimeMillis(),
        )
        dao.update(updated)
        updated.toDomain()
    }

    override suspend fun deleteSearch(id: Int): AppResult<Unit> = safeCall {
        dao.deleteById(id.toLong())
    }
}
