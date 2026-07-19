package com.divarsmartsearch.app.domain.repository

import com.divarsmartsearch.app.domain.model.AppSettings
import com.divarsmartsearch.app.util.AppResult

interface SettingsRepository {
    suspend fun getSettings(): AppResult<AppSettings>
    suspend fun updateSettings(settings: AppSettings): AppResult<AppSettings>
}
