package com.divarsmartsearch.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "saved_searches")
data class SavedSearchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val searchUrl: String,
    val status: String, // "active" | "paused"
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val minArea: Double? = null,
    val maxArea: Double? = null,
    val maxPricePerMeter: Double? = null,
    val city: String? = null,
    val neighborhood: String? = null,
    val propertyType: String? = null,
    val maxListingAgeHours: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "listings",
    indices = [Index(value = ["savedSearchId", "divarToken"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = SavedSearchEntity::class,
            parentColumns = ["id"],
            childColumns = ["savedSearchId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class ListingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val savedSearchId: Long,
    val divarToken: String,
    val url: String,
    val title: String,
    var description: String? = null,
    var price: Double? = null,
    var area: Double? = null,
    var pricePerMeter: Double? = null,
    val neighborhood: String? = null,
    val city: String? = null,
    var contactPhone: String? = null,
    var detectedPhoneNumbers: String? = null, // comma-separated
    val publishedAt: Long? = null,
    val firstSeenAt: Long = System.currentTimeMillis(),
    var ownerProbability: Double? = null,
    var isLikelyAgency: Boolean = false,
    var isVisible: Boolean = true,
    // True once the user has explicitly saved or rejected this listing from
    // the Results screen. From that point on it is permanently excluded
    // from any further automatic re-evaluation (background re-scans can
    // still update its raw fields, but must never touch isVisible again) —
    // see ListingIngestionService.ingest().
    var userDecided: Boolean = false,
    var notified: Boolean = false,
    // True once `description` has been filled in from the real detail page
    // (see HeadlessDivarScanner.fetchDetail / ListingIngestionService),
    // rather than the short, lower-quality card-preview text taken from the
    // search results page. Used to stop later re-extractions of the same
    // still-on-screen card from ever downgrading a listing's description
    // back to that preview text — see the bug-fix note in
    // ListingIngestionService.ingest() for the full story.
    var hasDetailDescription: Boolean = false,
    // See ListingEnricher for how each of these is computed.
    var phoneRepeatCount: Int = 0,
    var isDuplicate: Boolean = false,
    var duplicateOfListingId: Long? = null,
    var pricePerMeterVsAreaAveragePercent: Double? = null,
    var starRating: Int = 3,
)

@Entity(tableName = "blocked_phone_numbers")
data class BlockedPhoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "listing_interactions",
    foreignKeys = [
        ForeignKey(
            entity = ListingEntity::class,
            parentColumns = ["id"],
            childColumns = ["listingId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["listingId"])],
)
data class ListingInteractionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listingId: Long,
    val status: String, // "seen" | "saved" | "rejected"
    val rejectionReason: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * A single, independent keyword filter. Per explicit user request, the app
 * ships with none of these pre-installed — every row here (e.g. "دفتر",
 * "املاک", "مشاور", "کلید") is something the user typed in themselves from
 * the "فیلترهای دائمی" screen. Every listing is checked against EVERY row
 * where [isEnabled] is true.
 *
 * [filterType] controls what a match *does*:
 *  - "exclude": matching this REJECTS the listing, UNLESS the same ad also
 *    matches an "owner_signal" row (see below).
 *  - "owner_signal" (e.g. "من مالک هستم"): matching this tells the app
 *    "trust this ad's own claim of being a private owner" — it skips the
 *    AI/heuristic agency-probability guess for THIS ad, AND — per explicit
 *    user request — overrides any "exclude" match on the same ad, so a
 *    word like "دفتر"/"مشاور"/"املاک" no longer hides an ad that also says
 *    something like "مالک".
 *
 * [isBuiltIn] is legacy: nothing sets it to true anymore (the app ships
 * with zero pre-installed filters and every row, without exception, can be
 * toggled and deleted by the user). The field only still exists so old
 * installs' databases don't need a destructive migration; a startup
 * cleanup (see [com.divarsmartsearch.app.di.DatabaseModule]) clears any
 * leftover isBuiltIn=true rows from earlier app versions.
 */
@Entity(tableName = "keyword_filters")
data class KeywordFilterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val keyword: String,
    val category: String = "custom", // "real_estate" | "consultant" | "office" | "key" | "owner" | "custom"
    val filterType: String = "exclude", // "exclude" | "owner_signal"
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Global "حذف‌شده‌ها" (removed) blacklist. Whenever the user rejects a
 * listing from the Results screen (see ListingRepositoryImpl.rejectListing),
 * its [divarToken] is recorded here — independent of which saved search it
 * came from and independent of the `listings` row itself. From then on,
 * [ListingIngestionService.ingest] checks every freshly-scraped item's
 * token against this table BEFORE inserting anything, so a rejected ad can
 * never come back as a "new" listing again: not on the next scan cycle, not
 * under a different/recreated saved search, and not after any number of
 * repeat scans — see the ingest() bug-fix note for the full story.
 */
@Entity(tableName = "removed_listings")
data class RemovedListingEntity(
    @PrimaryKey val divarToken: String,
    val removedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val darkModeEnabled: Boolean = true,
    val notificationSoundEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val notificationSoundUri: String = "default",
    val anthropicApiKey: String? = null,
    val anthropicModel: String = "claude-haiku-4-5-20251001",
    val backgroundScanEnabled: Boolean = false,
    val backgroundScanIntervalMinutes: Int = 5,
)
