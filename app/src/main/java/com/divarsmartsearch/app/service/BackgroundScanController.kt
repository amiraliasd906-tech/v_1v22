package com.divarsmartsearch.app.service

import android.content.Context
import com.divarsmartsearch.app.data.local.dao.AppSettingsDao
import com.divarsmartsearch.app.data.local.entity.AppSettingsEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bug fix: turning a saved search "فعال" (active) — either by flipping its
 * switch on the "جستجوهای من" list, or simply by creating it (new searches
 * are created active) — used to only ever flip a `status` column in the
 * database. The actual scan loop only runs while
 * [AppSettingsEntity.backgroundScanEnabled] is true, and that flag was ONLY
 * ever set from the completely separate master switch on the Settings
 * screen. So a person could turn a search "on" and see nothing happen,
 * forever, unless they also happened to dig into Settings and flip a
 * second, unrelated-looking switch.
 *
 * Call [ensureRunning] anywhere a search becomes active (toggled on,
 * created, or reactivated) so activating a search always actually starts
 * scanning — matching what the switch visibly promises — instead of
 * silently depending on unrelated global state.
 */
@Singleton
class BackgroundScanController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSettingsDao: AppSettingsDao,
) {
    suspend fun ensureRunning() {
        val current = appSettingsDao.get()
        if (current?.backgroundScanEnabled != true) {
            appSettingsDao.upsert((current ?: AppSettingsEntity()).copy(backgroundScanEnabled = true))
        }
        // Idempotent: BackgroundScanService.onStartCommand only launches a
        // new loop if one isn't already active, so calling this when
        // scanning is already running is harmless.
        BackgroundScanService.start(context)
    }
}
