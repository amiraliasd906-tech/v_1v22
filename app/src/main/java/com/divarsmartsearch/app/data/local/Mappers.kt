package com.divarsmartsearch.app.data.local

import com.divarsmartsearch.app.data.local.entity.AppSettingsEntity
import com.divarsmartsearch.app.data.local.entity.BlockedPhoneEntity
import com.divarsmartsearch.app.data.local.entity.KeywordFilterEntity
import com.divarsmartsearch.app.data.local.entity.ListingEntity
import com.divarsmartsearch.app.data.local.entity.SavedSearchEntity
import com.divarsmartsearch.app.domain.model.AppSettings
import com.divarsmartsearch.app.domain.model.BlockedPhoneNumber
import com.divarsmartsearch.app.domain.model.KeywordFilter
import com.divarsmartsearch.app.domain.model.Listing
import com.divarsmartsearch.app.domain.model.PropertyType
import com.divarsmartsearch.app.domain.model.SavedSearch
import com.divarsmartsearch.app.domain.model.SavedSearchDraft
import com.divarsmartsearch.app.domain.model.SearchStatus

fun SavedSearchEntity.toDomain(): SavedSearch = SavedSearch(
    id = id.toInt(),
    name = name,
    searchUrl = searchUrl,
    status = if (status == "active") SearchStatus.ACTIVE else SearchStatus.PAUSED,
    minPrice = minPrice,
    maxPrice = maxPrice,
    minArea = minArea,
    maxArea = maxArea,
    maxPricePerMeter = maxPricePerMeter,
    city = city,
    neighborhood = neighborhood,
    propertyType = propertyType?.let { raw -> PropertyType.entries.find { it.name.equals(raw, ignoreCase = true) } },
    maxListingAgeHours = maxListingAgeHours,
)

fun SavedSearchDraft.toEntity(existingId: Long = 0): SavedSearchEntity = SavedSearchEntity(
    id = existingId,
    name = name.trim(),
    searchUrl = searchUrl.trim(),
    status = "active",
    minPrice = minPrice.toDoubleOrNull(),
    maxPrice = maxPrice.toDoubleOrNull(),
    minArea = minArea.toDoubleOrNull(),
    maxArea = maxArea.toDoubleOrNull(),
    maxPricePerMeter = maxPricePerMeter.toDoubleOrNull(),
    city = city.trim().ifBlank { null },
    neighborhood = neighborhood.trim().ifBlank { null },
    propertyType = propertyType?.name?.lowercase(),
    maxListingAgeHours = maxListingAgeHours.toIntOrNull(),
)

fun ListingEntity.toDomain(): Listing = Listing(
    id = id.toInt(),
    savedSearchId = savedSearchId.toInt(),
    url = url,
    title = title,
    description = description,
    price = price,
    area = area,
    pricePerMeter = pricePerMeter,
    neighborhood = neighborhood,
    city = city,
    publishedAtEpochMillis = publishedAt,
    firstSeenAtEpochMillis = firstSeenAt,
    ownerProbability = ownerProbability,
    isLikelyAgency = isLikelyAgency,
    isVisible = isVisible,
    detectedPhoneNumbers = detectedPhoneNumbers
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList(),
    phoneRepeatCount = phoneRepeatCount,
    isDuplicate = isDuplicate,
    duplicateOfListingId = duplicateOfListingId?.toInt(),
    pricePerMeterVsAreaAveragePercent = pricePerMeterVsAreaAveragePercent,
    starRating = starRating,
)

fun BlockedPhoneEntity.toDomain(): BlockedPhoneNumber = BlockedPhoneNumber(
    id = id.toInt(),
    phoneNumber = phoneNumber,
    note = note,
)

fun KeywordFilterEntity.toDomain(): KeywordFilter = KeywordFilter(
    id = id.toInt(),
    label = label,
    keyword = keyword,
    category = category,
    filterType = filterType,
    isEnabled = isEnabled,
    isBuiltIn = isBuiltIn,
)

fun AppSettingsEntity.toDomain(): AppSettings = AppSettings(
    darkModeEnabled = darkModeEnabled,
    notificationSoundEnabled = notificationSoundEnabled,
    notificationsEnabled = notificationsEnabled,
    notificationSoundUri = notificationSoundUri,
    anthropicApiKey = anthropicApiKey,
    anthropicModel = anthropicModel,
    backgroundScanEnabled = backgroundScanEnabled,
    backgroundScanIntervalMinutes = backgroundScanIntervalMinutes,
)

fun AppSettings.toEntity(): AppSettingsEntity = AppSettingsEntity(
    id = 1,
    darkModeEnabled = darkModeEnabled,
    notificationSoundEnabled = notificationSoundEnabled,
    notificationsEnabled = notificationsEnabled,
    notificationSoundUri = notificationSoundUri,
    anthropicApiKey = anthropicApiKey,
    anthropicModel = anthropicModel,
    backgroundScanEnabled = backgroundScanEnabled,
    backgroundScanIntervalMinutes = backgroundScanIntervalMinutes,
)
