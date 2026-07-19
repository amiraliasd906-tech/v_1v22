package com.divarsmartsearch.app.data.filters

import com.divarsmartsearch.app.data.local.dao.BlockedPhoneDao
import com.divarsmartsearch.app.data.local.dao.KeywordFilterDao
import com.divarsmartsearch.app.data.local.dao.ListingInteractionDao
import com.divarsmartsearch.app.data.local.entity.ListingEntity
import com.divarsmartsearch.app.data.local.entity.ListingInteractionEntity
import com.divarsmartsearch.app.data.local.entity.SavedSearchEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the full filter pipeline on a batch of listings, in order,
 * mirroring the original backend's apply_filters.py:
 *   1. Structured range filters (price/area/price-per-meter) from the SavedSearch.
 *   2. Permanent phone-number blocklist (official field + text-embedded numbers).
 *   3. Hard keyword exclusion — every ENABLED [com.divarsmartsearch.app.data.local.entity.KeywordFilterEntity]
 *      row (entirely user-added — the app ships with none of its own) is
 *      checked independently against the title and description; the
 *      listing is rejected the moment it matches ANY one of them (see
 *      [KeywordFilterEngine]).
 *   4. AI owner-detection (heuristic or LLM) for anything not already caught by step 3.
 *      Per explicit user request this is now a single fixed rule for every
 *      search, with no adjustable slider and no separate "owners only"
 *      toggle: a listing enters the results only if its estimated owner
 *      probability is between 50% and 100% — not more, not less (see
 *      [MAX_AGENCY_PROBABILITY_FOR_RESULTS]).
 *
 * Never-auto-hide guarantee (range filters + blocked-phone list only): any
 * listing that is already visible (part of the live results) when a
 * pipeline run starts is protected from stages 1 and 2 above — a later
 * price/area edit or a number getting added to the blocklist afterwards
 * will never retroactively hide it.
 *
 * This guarantee does NOT extend to agency detection (stages 3 and 4):
 * per explicit user request, a listing that turns out to match an exclude
 * keyword or score as likely-agency is hidden the moment that's found,
 * even if it was already visible from an earlier pass that only had
 * incomplete (list-preview) text to go on. Otherwise a listing wrongly
 * shown before its real detail-page description arrived would stay
 * visible forever. Outside of that, a listing only ever leaves the
 * results because of stages 1–4 above or because the person explicitly
 * saves or rejects it themselves from the Results screen (see
 * ListingRepositoryImpl.saveListing/rejectListing and
 * ListingEntity.userDecided).
 *
 * Mutates each ListingEntity's isVisible/isLikelyAgency/ownerProbability
 * fields in place and returns the list that survived every stage.
 */
@Singleton
class FilterPipeline @Inject constructor(
    private val blockedPhoneDao: BlockedPhoneDao,
    private val listingInteractionDao: ListingInteractionDao,
    private val listingEnricher: ListingEnricher,
    private val keywordFilterDao: KeywordFilterDao,
) {
    companion object {
        // Fixed rule, per explicit user request: a listing only belongs in
        // the results if its estimated owner probability is OVER 50% (i.e.
        // agency/agent probability strictly under 50%). This replaces the
        // old adjustable slider entirely — it is no longer configurable.
        // A tied/neutral 0.5 score (OwnerDetector's own baseline when it
        // finds no signal either way) now counts as agency rather than
        // silently defaulting to "owner" — see the >= comparison below.
        private const val MAX_AGENCY_PROBABILITY_FOR_RESULTS = 0.5
    }

    suspend fun apply(
        savedSearch: SavedSearchEntity,
        listings: List<ListingEntity>,
        anthropicApiKey: String?,
        anthropicModel: String,
        // Ids of listings that were ALREADY part of the live results (i.e.
        // isVisible == true in the database) before this run started — NOT
        // inferred from the in-memory isVisible field on [listings], since
        // a brand-new not-yet-decided listing also defaults to isVisible =
        // true and must never be mistaken for an already-shown one. The
        // caller (which just read these from the DB, or knows which rows
        // are brand-new inserts) is the only one who reliably knows which
        // is which. Ids in this set are protected for the whole run — see
        // the never-auto-hide guarantee above.
        alreadyVisibleIds: Set<Long> = emptySet(),
    ): List<ListingEntity> {
        if (listings.isEmpty()) return emptyList()

        populateDetectedPhoneNumbers(listings)

        // Cross-listing signal: how often this ad's phone number(s) show up
        // elsewhere. Computed before the agency check below so it can feed
        // straight into OwnerDetector as an extra signal.
        for (listing in listings) {
            listing.phoneRepeatCount = listingEnricher.computePhoneRepeatCount(listing)
        }

        val protectedIds = alreadyVisibleIds

        applyRangeFilters(savedSearch, listings, protectedIds)
        for (listing in listings) {
            if (!listing.isVisible && listing.id !in protectedIds) recordRejection(listing, "out_of_filter_range")
        }

        val blockedNumbers = blockedPhoneDao.getAllNumbers().toSet()
        for (listing in listings) {
            if (!listing.isVisible) continue // already dropped by range filter above
            if (listing.id in protectedIds) continue // never auto-hidden
            if (PhoneFilter.isBlocked(listing, blockedNumbers)) {
                listing.isVisible = false
                recordRejection(listing, "blocked_phone")
            }
        }
        val phoneSurvivors = listings.filter { it.isVisible }

        // Every enabled keyword filter is its own independent check — a
        // listing must pass ALL "exclude" filters, in order, to survive
        // this stage. "owner_signal" filters (e.g. "من مالک هستم") are
        // handled separately below, per-listing, BEFORE the "exclude"
        // check, and — per explicit user request — OVERRIDE it: if the ad
        // itself claims to be from the owner, it is shown even if it also
        // contains an "exclude" word like "دفتر"/"املاک"/"مشاور".
        val activeKeywordFilters = keywordFilterDao.getAllEnabled()
        val excludeFilters = activeKeywordFilters.filter { it.filterType != "owner_signal" }
        val ownerSignalFilters = activeKeywordFilters.filter { it.filterType == "owner_signal" }

        val finalKept = mutableListOf<ListingEntity>()
        for (listing in phoneSurvivors) {
            // Per explicit user request: an "owner_signal" match (e.g. "من
            // مالک هستم") is now checked FIRST and OVERRIDES every
            // "exclude" filter — even a word like "دفتر"/"املاک"/"مشاور".
            // If the ad claims to be from the owner, it is shown no matter
            // what else appears in its title/description.
            val ownerSignalMatch = KeywordFilterEngine.findFirstMatch(
                listing.title, listing.description, ownerSignalFilters
            )
            if (ownerSignalMatch != null) {
                listing.isLikelyAgency = false
                listing.ownerProbability = 1.0
                finalKept.add(listing)
                continue
            }

            // Checked against BOTH the title and the description: a huge share of
            // agency posts on Divar put the keyword in the TITLE only (e.g.
            // "مشاور املاک رضایی"، "فایل ویژه - املاک ..."), so checking the
            // description alone was letting most of them straight through.
            val matchedFilter = KeywordFilterEngine.findFirstMatch(
                listing.title, listing.description, excludeFilters
            )
            if (matchedFilter != null) {
                listing.isLikelyAgency = true
                listing.ownerProbability = 0.0
                // Per explicit user request: agency detection now overrides
                // the never-auto-hide guarantee. A listing shown earlier
                // based on incomplete (list-preview-only) text and later
                // confirmed by a real detail-page description to match an
                // exclude keyword must be removed from results, not
                // permanently protected. isProtected is intentionally
                // ignored here.
                listing.isVisible = false
                recordRejection(listing, "keyword_filter:${matchedFilter.label}")
                continue
            }

            val agencyProbability = OwnerDetector.agencyProbability(
                listing.description, anthropicApiKey, anthropicModel, listing.phoneRepeatCount
            )
            listing.ownerProbability = 1.0 - agencyProbability
            // Bug fix: this used to require agencyProbability strictly
            // GREATER THAN 0.5 to count as agency. The heuristic's neutral
            // baseline score IS exactly 0.5 (see
            // OwnerDetector.heuristicAgencyProbability), so any ad whose
            // text didn't happen to contain one of the fixed literal
            // phrases (real agency posts very often don't — e.g. plain
            // "مشاور" alone, a company name, or different wording) scored
            // exactly 0.5 and silently passed as "owner" by default instead
            // of being treated as unresolved/uncertain. Using >= means a
            // tied/uncertain score is no longer auto-approved.
            listing.isLikelyAgency = agencyProbability >= MAX_AGENCY_PROBABILITY_FOR_RESULTS

            if (listing.isLikelyAgency) {
                // Per explicit user request: same override as the keyword
                // branch above — once agency probability crosses the
                // threshold, the listing is hidden even if it was already
                // visible from an earlier, less-informed pass.
                listing.isVisible = false
                recordRejection(listing, "likely_agency")
            } else {
                finalKept.add(listing)
            }
        }

        // Enrichment that only makes sense for listings the person will
        // actually see: duplicate/republish detection and price-vs-area
        // comparison, then a combined star rating from everything above.
        for (listing in finalKept) {
            listingEnricher.detectDuplicate(listing)
            listingEnricher.computePriceComparison(listing)
            listing.starRating = listingEnricher.computeStarRating(listing)
        }

        return finalKept
    }

    private fun populateDetectedPhoneNumbers(listings: List<ListingEntity>) {
        for (listing in listings) {
            val numbers = PhoneExtraction.extractPhoneNumbers(listing.title, listing.description)
            listing.detectedPhoneNumbers = if (numbers.isNotEmpty()) numbers.joinToString(",") else null
        }
    }

    private fun applyRangeFilters(
        savedSearch: SavedSearchEntity,
        listings: List<ListingEntity>,
        protectedIds: Set<Long>,
    ) {
        for (listing in listings) {
            if (listing.id in protectedIds) continue // never auto-hidden
            val price = listing.price
            val area = listing.area
            val pricePerMeter = listing.pricePerMeter
            val outOfRange = when {
                savedSearch.minPrice != null && price != null && price < savedSearch.minPrice -> true
                savedSearch.maxPrice != null && price != null && price > savedSearch.maxPrice -> true
                savedSearch.minArea != null && area != null && area < savedSearch.minArea -> true
                savedSearch.maxArea != null && area != null && area > savedSearch.maxArea -> true
                savedSearch.maxPricePerMeter != null && pricePerMeter != null &&
                    pricePerMeter > savedSearch.maxPricePerMeter -> true
                else -> false
            }
            if (outOfRange) listing.isVisible = false
        }
    }

    private suspend fun recordRejection(listing: ListingEntity, reason: String) {
        // Listing may not have a DB id yet if this is its first pass before
        // insertion; the repository re-associates interactions after insert.
        if (listing.id != 0L) {
            listingInteractionDao.insert(
                ListingInteractionEntity(listingId = listing.id, status = "rejected", rejectionReason = reason)
            )
        }
    }
}
