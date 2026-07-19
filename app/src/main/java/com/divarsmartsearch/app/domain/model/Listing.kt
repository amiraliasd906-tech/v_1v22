package com.divarsmartsearch.app.domain.model

data class Listing(
    val id: Int,
    val savedSearchId: Int,
    val url: String,
    val title: String,
    val description: String?,
    val price: Double?,
    val area: Double?,
    val pricePerMeter: Double?,
    val neighborhood: String?,
    val city: String?,
    val publishedAtEpochMillis: Long?,
    val firstSeenAtEpochMillis: Long,
    val ownerProbability: Double?,
    val isLikelyAgency: Boolean,
    val isVisible: Boolean,
    val detectedPhoneNumbers: List<String> = emptyList(),
    // How many *distinct* other listings (across all searches) share one of
    // this ad's phone numbers. A high count is a strong signal of a
    // professional agent/dealer rather than a one-off private seller.
    val phoneRepeatCount: Int = 0,
    // True when this ad looks like a republish/duplicate of another listing
    // already stored (same contact number or a near-identical price/area/title).
    val isDuplicate: Boolean = false,
    val duplicateOfListingId: Int? = null,
    // How this ad's price-per-meter compares to the average of other stored
    // listings in the same neighborhood (or city, if no neighborhood match).
    // Positive = more expensive than average, negative = cheaper.
    val pricePerMeterVsAreaAveragePercent: Double? = null,
    // 1-5 overall quality score combining owner-likelihood, price vs area
    // average, and duplicate/agent signals. See ListingEnricher.
    val starRating: Int = 3,
)

enum class HistoryTab {
    SEEN,
    SAVED,
    REJECTED,
}
