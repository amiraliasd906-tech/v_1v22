package com.divarsmartsearch.app.data.filters

import com.divarsmartsearch.app.data.local.dao.ListingDao
import com.divarsmartsearch.app.data.local.entity.ListingEntity
import kotlin.math.abs
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enriches a [ListingEntity] with signals that need to look across *other*
 * stored listings — something the pure, single-ad checks in OwnerDetector /
 * KeywordFilterEngine can't do on their own:
 *
 *  - phoneRepeatCount: how many other ads (any search, any time) share one
 *    of this ad's phone numbers. Reused by FilterPipeline as an extra
 *    signal that boosts the agency-probability score.
 *  - duplicate/republish detection against recent listings in the same city.
 *  - price-per-meter vs. the neighborhood/city average.
 *  - a simple 1-5 star rating combining all of the above with the
 *    owner-probability score.
 */
@Singleton
class ListingEnricher @Inject constructor(
    private val listingDao: ListingDao,
) {

    /** Highest number of *other* distinct listings sharing any of this ad's numbers. */
    suspend fun computePhoneRepeatCount(listing: ListingEntity): Int {
        val numbers = allPhoneNumbers(listing)
        if (numbers.isEmpty()) return 0
        var max = 0
        for (number in numbers) {
            val count = listingDao.countListingsForPhone(number, excludeId = listing.id)
            if (count > max) max = count
        }
        return max
    }

    /**
     * Looks for an existing listing that is very likely the same ad
     * (matching phone number, or near-identical title + price + area) and,
     * if found, marks [listing] as a duplicate/republish in place.
     */
    suspend fun detectDuplicate(listing: ListingEntity) {
        val candidates = listingDao.getRecentCandidatesForDuplicateCheck(
            city = listing.city,
            excludeId = listing.id,
        )
        if (candidates.isEmpty()) return

        val ownNumbers = allPhoneNumbers(listing)
        val ownTitle = normalizeTitle(listing.title)

        val match = candidates.firstOrNull { candidate ->
            val candidateNumbers = allPhoneNumbers(candidate)
            val sharesPhone = ownNumbers.isNotEmpty() && candidateNumbers.isNotEmpty() &&
                ownNumbers.any { it in candidateNumbers }

            val samePriceAndArea = isClose(listing.price, candidate.price, tolerancePercent = 3.0) &&
                isClose(listing.area, candidate.area, tolerancePercent = 3.0)

            val sameTitle = ownTitle.isNotEmpty() && ownTitle == normalizeTitle(candidate.title)

            sharesPhone || sameTitle || (samePriceAndArea && listing.price != null && listing.area != null)
        }

        if (match != null) {
            listing.isDuplicate = true
            listing.duplicateOfListingId = match.id
        }
    }

    /** Compares [listing]'s price-per-meter to the neighborhood (or city) average. */
    suspend fun computePriceComparison(listing: ListingEntity) {
        val pricePerMeter = listing.pricePerMeter ?: return

        val average = listing.neighborhood?.let {
            listingDao.averagePricePerMeterForNeighborhood(it, excludeId = listing.id)
        } ?: listing.city?.let {
            listingDao.averagePricePerMeterForCity(it, excludeId = listing.id)
        }

        if (average != null && average > 0) {
            listing.pricePerMeterVsAreaAveragePercent =
                ((pricePerMeter - average) / average * 100.0)
        }
    }

    /**
     * Simple, explainable 1-5 rating. Starts neutral at 3 and nudges up or
     * down for each strong signal; this is a heuristic aid for browsing,
     * not a guarantee about the listing.
     */
    fun computeStarRating(listing: ListingEntity): Int {
        var score = 3.0

        listing.ownerProbability?.let { ownerProbability ->
            if (ownerProbability >= 0.8) score += 1.0
            if (ownerProbability < 0.4) score -= 1.0
        }

        listing.pricePerMeterVsAreaAveragePercent?.let { percentDiff ->
            if (percentDiff <= -10.0) score += 1.0
            if (percentDiff >= 20.0) score -= 1.0
        }

        if (listing.isDuplicate) score -= 1.0
        if (listing.phoneRepeatCount >= 3) score -= 1.0

        return score.roundToInt().coerceIn(1, 5)
    }

    private fun allPhoneNumbers(listing: ListingEntity): List<String> {
        val fromField = listing.contactPhone?.let { listOf(it) } ?: emptyList()
        val fromText = listing.detectedPhoneNumbers
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        return (fromField + fromText).distinct()
    }

    private fun normalizeTitle(title: String): String =
        title.trim().lowercase().replace(Regex("""\s+"""), " ").replace(Regex("""[.,،!؟\-_/]"""), "")

    private fun isClose(a: Double?, b: Double?, tolerancePercent: Double): Boolean {
        if (a == null || b == null || a == 0.0) return false
        return abs(a - b) / a * 100.0 <= tolerancePercent
    }
}
