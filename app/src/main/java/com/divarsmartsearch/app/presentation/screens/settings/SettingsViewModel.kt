package com.divarsmartsearch.app.presentation.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.divarsmartsearch.app.data.local.LocalPreferencesDataStore
import com.divarsmartsearch.app.domain.model.AppSettings
import com.divarsmartsearch.app.domain.usecase.GetSettingsUseCase
import com.divarsmartsearch.app.domain.usecase.UpdateSettingsUseCase
import com.divarsmartsearch.app.service.BackgroundScanService
import com.divarsmartsearch.app.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isLoading: Boolean = true,
    val settings: AppSettings = AppSettings(
        darkModeEnabled = true,
        notificationSoundEnabled = true,
        notificationsEnabled = true,
        notificationSoundUri = "default",
    ),
    val isSaving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getSettingsUseCase: GetSettingsUseCase,
    private val updateSettingsUseCase: UpdateSettingsUseCase,
    private val localPreferences: LocalPreferencesDataStore,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = getSettingsUseCase()) {
                is AppResult.Success -> _uiState.update { it.copy(isLoading = false, settings = result.data) }
                is AppResult.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                AppResult.Loading -> Unit
            }
        }
    }

    fun updateDarkMode(enabled: Boolean) {
        // Applied to the UI instantly via local DataStore, then persisted to Room.
        viewModelScope.launch { localPreferences.setDarkModeEnabled(enabled) }
        persist { it.copy(darkModeEnabled = enabled) }
    }

    fun updateNotificationSound(enabled: Boolean) = persist { it.copy(notificationSoundEnabled = enabled) }
    fun updateNotificationsEnabled(enabled: Boolean) = persist { it.copy(notificationsEnabled = enabled) }
    fun updateAnthropicApiKey(key: String) = persist { it.copy(anthropicApiKey = key.ifBlank { null }) }
    fun updateAnthropicModel(model: String) = persist { it.copy(anthropicModel = model) }

    /**
     * Turns automatic background scanning on/off. When enabled, the
     * person no longer has to open the app and scroll — a foreground
     * service checks every active saved search on its own from then on.
     */
    fun updateBackgroundScanEnabled(enabled: Boolean) {
        if (enabled) {
            BackgroundScanService.start(appContext)
        } else {
            BackgroundScanService.stop(appContext)
        }
        persist { it.copy(backgroundScanEnabled = enabled) }
    }

    fun updateBackgroundScanInterval(minutes: Int) = persist { it.copy(backgroundScanIntervalMinutes = minutes) }

    private fun persist(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_uiState.value.settings)
        _uiState.update { it.copy(settings = updated, isSaving = true) }
        viewModelScope.launch {
            when (val result = updateSettingsUseCase(updated)) {
                is AppResult.Success -> _uiState.update { it.copy(settings = result.data, isSaving = false) }
                is AppResult.Error -> _uiState.update { it.copy(isSaving = false, error = result.message) }
                AppResult.Loading -> Unit
            }
        }
    }
}
