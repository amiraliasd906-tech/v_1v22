package com.divarsmartsearch.app.domain.usecase

import com.divarsmartsearch.app.domain.model.AppSettings
import com.divarsmartsearch.app.domain.repository.SettingsRepository
import com.divarsmartsearch.app.util.AppResult
import javax.inject.Inject

class GetSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(): AppResult<AppSettings> = repository.getSettings()
}

class UpdateSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(settings: AppSettings): AppResult<AppSettings> =
        repository.updateSettings(settings)
}
