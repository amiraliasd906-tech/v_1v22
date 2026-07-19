package com.divarsmartsearch.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "divar_local_prefs")

/**
 * Local-only preference cache so the UI (e.g. dark mode) responds
 * instantly without waiting on a network round-trip. The backend's
 * AppSettings remains the source of truth and is synced whenever the
 * Settings screen saves changes.
 */
@Singleton
class LocalPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode_enabled")
    }

    val darkModeEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.DARK_MODE] ?: true }

    suspend fun setDarkModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_MODE] = enabled }
    }
}
