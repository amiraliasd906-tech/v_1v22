package com.divarsmartsearch.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DivarApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val newListingChannel = NotificationChannel(
                getString(R.string.new_listing_notification_channel_id),
                getString(R.string.new_listing_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = getString(R.string.new_listing_notification_channel_desc)
            }

            // Bug fix: this used to be IMPORTANCE_MIN. Per Android's own
            // documentation, an IMPORTANCE_MIN notification does not show
            // in the status bar at all -- on many devices/OS versions that
            // made the persistent "در حال بررسی" notification for this
            // foreground service invisible even while the service itself
            // was genuinely running and scanning, which looked exactly
            // like "background scan doesn't start" from the outside, with
            // nothing to prove otherwise. IMPORTANCE_LOW keeps this
            // channel silent and non-intrusive (no sound, no heads-up
            // popup) while still guaranteeing the ongoing notification is
            // actually visible, which is also required for a long-running
            // foreground service to be trustworthy to the person running it.
            val backgroundScanChannel = NotificationChannel(
                getString(R.string.background_scan_channel_id),
                getString(R.string.background_scan_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.background_scan_channel_desc)
                setShowBadge(false)
            }

            manager.createNotificationChannel(newListingChannel)
            manager.createNotificationChannel(backgroundScanChannel)
        }
    }
}
