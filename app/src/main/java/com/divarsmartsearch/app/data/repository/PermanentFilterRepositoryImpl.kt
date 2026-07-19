package com.divarsmartsearch.app.data.repository

import com.divarsmartsearch.app.data.filters.KeywordFilterEngine
import com.divarsmartsearch.app.data.filters.PhoneFilter
import com.divarsmartsearch.app.data.local.dao.BlockedPhoneDao
import com.divarsmartsearch.app.data.local.dao.KeywordFilterDao
import com.divarsmartsearch.app.data.local.entity.BlockedPhoneEntity
import com.divarsmartsearch.app.data.local.entity.KeywordFilterEntity
import com.divarsmartsearch.app.data.local.toDomain
import com.divarsmartsearch.app.domain.model.BlockedPhoneNumber
import com.divarsmartsearch.app.domain.model.KeywordFilter
import com.divarsmartsearch.app.domain.repository.PermanentFilterRepository
import com.divarsmartsearch.app.util.AppResult
import com.divarsmartsearch.app.util.safeCall
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermanentFilterRepositoryImpl @Inject constructor(
    private val blockedPhoneDao: BlockedPhoneDao,
    private val keywordFilterDao: KeywordFilterDao,
) : PermanentFilterRepository {

    override suspend fun getBlockedNumbers(): AppResult<List<BlockedPhoneNumber>> = safeCall {
        blockedPhoneDao.observeAll().first().map { it.toDomain() }
    }

    override suspend fun addBlockedNumber(phoneNumber: String, note: String?): AppResult<BlockedPhoneNumber> = safeCall {
        val normalized = PhoneFilter.normalizePhone(phoneNumber)
        if (normalized.length < 10) throw IllegalArgumentException("شماره تلفن معتبر نیست")

        val existing = blockedPhoneDao.findByNumber(normalized)
        if (existing != null) throw IllegalStateException("این شماره قبلاً در لیست فیلتر دائمی وجود دارد")

        val entity = BlockedPhoneEntity(phoneNumber = normalized, note = note)
        val id = blockedPhoneDao.insert(entity)
        entity.copy(id = id).toDomain()
    }

    override suspend fun removeBlockedNumber(id: Int): AppResult<Unit> = safeCall {
        blockedPhoneDao.deleteById(id.toLong())
    }

    override suspend fun getKeywordFilters(): AppResult<List<KeywordFilter>> = safeCall {
        keywordFilterDao.getAll().map { it.toDomain() }
    }

    override suspend fun addKeywordFilter(
        label: String,
        keyword: String,
        category: String,
        filterType: String,
    ): AppResult<KeywordFilter> = safeCall {
        val normalizedKeyword = KeywordFilterEngine.normalize(keyword.trim())
        if (normalizedKeyword.isBlank()) throw IllegalArgumentException("کلمه نمی‌تواند خالی باشد")

        if (keywordFilterDao.countByKeyword(normalizedKeyword) > 0) {
            throw IllegalStateException("فیلتری با این کلمه قبلاً اضافه شده است")
        }

        val entity = KeywordFilterEntity(
            label = label.trim().ifBlank { keyword.trim() },
            keyword = normalizedKeyword,
            category = category.ifBlank { "custom" },
            filterType = filterType.ifBlank { "exclude" },
            isEnabled = true,
            isBuiltIn = false,
        )
        val id = keywordFilterDao.insert(entity)
        entity.copy(id = id).toDomain()
    }

    override suspend fun removeKeywordFilter(id: Int): AppResult<Unit> = safeCall {
        keywordFilterDao.deleteById(id.toLong())
    }

    override suspend fun setKeywordFilterEnabled(id: Int, enabled: Boolean): AppResult<Unit> = safeCall {
        keywordFilterDao.setEnabled(id.toLong(), enabled)
    }
}
