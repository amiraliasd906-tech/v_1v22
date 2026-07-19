package com.divarsmartsearch.app.data.repository

import com.divarsmartsearch.app.data.local.dao.AppSettingsDao
import com.divarsmartsearch.app.data.local.entity.AppSettingsEntity
import com.divarsmartsearch.app.data.local.toDomain
import com.divarsmartsearch.app.data.local.toEntity
import com.divarsmartsearch.app.domain.model.AppSettings
import com.divarsmartsearch.app.domain.repository.SettingsRepository
import com.divarsmartsearch.app.util.AppResult
import com.divarsmartsearch.app.util.safeCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dao: AppSettingsDao,
) : SettingsRepository {

    override suspend fun getSettings(): AppResult<AppSettings> = safeCall {
        (dao.get() ?: AppSettingsEntity().also { dao.upsert(it) }).toDomain()
    }

    override suspend fun updateSettings(settings: AppSettings): AppResult<AppSettings> = safeCall {
        val entity = settings.toEntity()
        dao.upsert(entity)
        entity.toDomain()
    }
}
