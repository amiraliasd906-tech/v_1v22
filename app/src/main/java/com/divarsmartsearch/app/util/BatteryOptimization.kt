package com.divarsmartsearch.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * The background scan service ([com.divarsmartsearch.app.service.BackgroundScanService])
 * only gets to run reliably on a schedule if Android's battery optimizer
 * has been told to leave this app alone. Many OEM skins (Xiaomi, Samsung,
 * Huawei, ...) kill background services aggressively otherwise, silently
 * breaking auto-scan with no error the user would ever see — so we check
 * for this explicitly and ask the person to grant the exemption, the same
 * way we ask for the notification permission.
 */
object BatteryOptimization {

    /** True if the OS is already allowed to kill this app in the background. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Builds the system dialog that lets the user exempt this app from
     * battery optimization directly (no need to dig through Settings).
     * Some OEM builds reject this intent, so callers should fall back to
     * [settingsIntent] if launching this throws.
     */
    fun requestExemptionIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    /** Fallback: the general battery-optimization list screen. */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
