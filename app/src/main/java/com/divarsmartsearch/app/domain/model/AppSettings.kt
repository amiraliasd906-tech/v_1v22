package com.divarsmartsearch.app.domain.model

data class BlockedPhoneNumber(
    val id: Int,
    val phoneNumber: String,
    val note: String?,
)

/**
 * A single independent keyword filter (see KeywordFilterEngine). Each
 * one is its own on/off switch: a listing must pass every enabled filter
 * to survive the pipeline. [category] only drives which icon/color the
 * filter list shows — "real_estate" | "consultant" | "office" | "key" | "custom".
 */
data class KeywordFilter(
    val id: Int,
    val label: String,
    val keyword: String,
    val category: String,
    val filterType: String = "exclude", // "exclude" | "owner_signal"
    val isEnabled: Boolean,
    val isBuiltIn: Boolean,
)

data class AppSettings(
    val darkModeEnabled: Boolean,
    val notificationSoundEnabled: Boolean,
    val notificationsEnabled: Boolean,
    val notificationSoundUri: String,
    val anthropicApiKey: String? = null,
    val anthropicModel: String = "claude-haiku-4-5-20251001",
    // When true, a background service keeps checking every active saved
    // search on its own, so listings arrive without the person opening
    // the app or scrolling anything themselves.
    val backgroundScanEnabled: Boolean = false,
    val backgroundScanIntervalMinutes: Int = 5,
)
