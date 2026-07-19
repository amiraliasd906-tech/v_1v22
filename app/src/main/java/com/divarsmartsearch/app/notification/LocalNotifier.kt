package com.divarsmartsearch.app.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.divarsmartsearch.app.R
import com.divarsmartsearch.app.data.local.entity.ListingEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shows a local notification when a listing passes every filter.
 * There is no server or push service involved — everything runs on
 * this device, so this simply calls Android's own NotificationManager
 * directly from wherever the filter pipeline finishes running.
 */
@Singleton
class LocalNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notifyNewListing(listing: ListingEntity) {
        val priceText = listing.price?.let {
            "${NumberFormat.getNumberInstance(Locale("fa", "IR")).format(it.toLong())} تومان"
        } ?: "قیمت نامشخص"

        val tapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(listing.url))
        val pendingIntent = PendingIntent.getActivity(
            context,
            listing.id.toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(
            context,
            context.getString(R.string.new_listing_notification_channel_id),
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("آگهی جدید مطابق فیلتر شما")
            .setContentText("${listing.title} — $priceText")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(listing.id.toInt(), notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission was denied; nothing more to do.
        }
    }
}
