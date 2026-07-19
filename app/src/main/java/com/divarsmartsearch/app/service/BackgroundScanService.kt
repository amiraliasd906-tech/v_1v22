package com.divarsmartsearch.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.divarsmartsearch.app.MainActivity
import com.divarsmartsearch.app.R
import com.divarsmartsearch.app.data.local.dao.AppSettingsDao
import com.divarsmartsearch.app.data.local.dao.SavedSearchDao
import com.divarsmartsearch.app.data.webview.HeadlessDivarScanner
import com.divarsmartsearch.app.data.webview.ListingIngestionService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Runs entirely on its own, in the background: on a loop, it reads every
 * saved search with status == "active", loads it in an invisible WebView,
 * auto-scrolls it, runs it through the exact same [ListingIngestionService]
 * pipeline used everywhere else in the app (range filters -> phone
 * blocklist -> "مشاور"/"املاک" hard keyword block -> owner-probability
 * scoring), and only ever notifies for listings that survive every stage.
 *
 * This is what removes the need to manually open the app and scroll —
 * once started, this keeps running (subject to Android's own battery /
 * Doze rules) until the person turns background scanning off in Settings.
 */
@AndroidEntryPoint
class BackgroundScanService : Service() {

    @Inject lateinit var savedSearchDao: SavedSearchDao
    @Inject lateinit var appSettingsDao: AppSettingsDao
    @Inject lateinit var headlessDivarScanner: HeadlessDivarScanner
    @Inject lateinit var ingestionService: ListingIngestionService

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob)
    private var loopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        if (loopJob?.isActive != true) {
            loopJob = serviceScope.launch { runLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    private suspend fun runLoop() {
        while (true) {
            val intervalMinutes = (appSettingsDao.get()?.backgroundScanIntervalMinutes ?: DEFAULT_INTERVAL_MINUTES)
                .coerceAtLeast(1)

            try {
                scanAllActiveSearches()
            } catch (e: Exception) {
                // A single failed cycle (network hiccup, etc.) must never
                // stop future cycles from running — but it must be logged,
                // not silently swallowed, or a recurring failure here is
                // invisible from the outside (see ListingIngestionService's
                // own try/catch for why that used to be dangerous).
                Log.e(TAG_LOG, "Background scan cycle failed", e)
            }

            delay(intervalMinutes * 60_000L)
        }
    }

    private suspend fun scanAllActiveSearches() {
        val activeSearches = savedSearchDao.getAll().filter { it.status == "active" }
        for (search in activeSearches) {
            try {
                val extracted = headlessDivarScanner.scan(search.searchUrl)
                if (extracted.isNotEmpty()) {
                    ingestionService.ingest(search.id, extracted)
                }
            } catch (e: Exception) {
                // Keep going with the next saved search even if this one
                // failed, but log it — this used to be a silent catch,
                // which is exactly what let a filter-pipeline crash hide
                // itself indefinitely instead of surfacing anywhere.
                Log.e(TAG_LOG, "Scan failed for savedSearchId=${search.id}", e)
            }
        }
    }

    private fun buildForegroundNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, getString(R.string.background_scan_channel_id))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.background_scan_notification_title))
            .setContentText(getString(R.string.background_scan_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.divarsmartsearch.app.action.STOP_BACKGROUND_SCAN"
        private const val TAG_LOG = "BackgroundScanService"
        private const val NOTIFICATION_ID = 42
        private const val DEFAULT_INTERVAL_MINUTES = 5

        fun start(context: Context) {
            val intent = Intent(context, BackgroundScanService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, BackgroundScanService::class.java).setAction(ACTION_STOP))
        }
    }
}
