package com.divarsmartsearch.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.divarsmartsearch.app.data.local.dao.AppSettingsDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Without this, background scanning would silently stop every time the
 * phone restarts, and the person would have no idea why they stopped
 * getting notifications until they reopened the app.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var appSettingsDao: AppSettingsDao

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = appSettingsDao.get()?.backgroundScanEnabled == true
                if (enabled) {
                    BackgroundScanService.start(context.applicationContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
