package com.divarsmartsearch.app.data.webview

import com.divarsmartsearch.app.data.filters.FilterPipeline
import com.divarsmartsearch.app.data.filters.KeywordFilterEngine
import com.divarsmartsearch.app.data.filters.PhoneFilter
import com.divarsmartsearch.app.data.local.dao.AppSettingsDao
import com.divarsmartsearch.app.data.local.dao.KeywordFilterDao
import com.divarsmartsearch.app.data.local.dao.ListingDao
import com.divarsmartsearch.app.data.local.dao.RemovedListingDao
import com.divarsmartsearch.app.data.local.dao.SavedSearchDao
import com.divarsmartsearch.app.data.local.entity.ListingEntity
import com.divarsmartsearch.app.notification.LocalNotifier
import android.util.Log
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

data class IngestResult(val received: Int, val new: Int, val passedFilters: Int)

/**
 * Handles listings extracted by the in-app WebView. This is the
 * Kotlin/Room equivalent of the old backend's app/services/ingestion.py:
 * new listings are inserted, listings seen before are enriched in place
 * (e.g. a detail-page visit revealing a phone number), and each change
 * re-runs the full filter pipeline. Notifications are local and are
 * only ever sent once per listing (see `notified` on ListingEntity).
 */
@Singleton
class ListingIngestionService @Inject constructor(
    private val savedSearchDao: SavedSearchDao,
    private val listingDao: ListingDao,
    private val appSettingsDao: AppSettingsDao,
    private val filterPipeline: FilterPipeline,
    private val headlessDivarScanner: HeadlessDivarScanner,
    private val localNotifier: LocalNotifier,
    private val keywordFilterDao: KeywordFilterDao,
    private val removedListingDao: RemovedListingDao,
) {
    suspend fun ingest(savedSearchId: Long, items: List<ExtractedListing>): IngestResult {
        val savedSearch = savedSearchDao.getById(savedSearchId) ?: return IngestResult(items.size, 0, 0)

        val brandNew = mutableListOf<ListingEntity>()
        val enriched = mutableListOf<ListingEntity>()

        // Global "حذف‌شده‌ها" blacklist (see RemovedListingEntity): every
        // token the user has ever rejected, across every saved search.
        // Loaded once per ingest() call instead of one query per item.
        val removedTokens = removedListingDao.getAllTokens().toSet()

        for (item in items) {
            if (item.divarToken in removedTokens) {
                // Already rejected by the user at some point in the past —
                // must never be treated as new and must never be added back
                // to the results, no matter how many more times it gets
                // scraped (even under a different/recreated saved search).
                continue
            }

            val existing = listingDao.findByToken(savedSearchId, item.divarToken)

            if (existing == null) {
                val entity = ListingEntity(
                    savedSearchId = savedSearchId,
                    divarToken = item.divarToken,
                    url = item.url,
                    title = item.title,
                    description = item.description,
                    price = item.price,
                    area = item.area,
                    pricePerMeter = item.pricePerMeter,
                    neighborhood = item.neighborhood ?: savedSearch.neighborhood,
                    city = savedSearch.city,
                    contactPhone = normalizeOrNull(item.contactPhone),
                )
                val newId = listingDao.insert(entity)
                brandNew.add(entity.copy(id = newId))
            } else if (!existing.userDecided) {
                // If the user has already explicitly saved or rejected this
                // listing (existing.userDecided), it is skipped entirely —
                // see ListingEntity.userDecided. A later scan noticing its
                // price changed by a few million tomans must never revive it
                // or touch its visibility; that is exactly what used to make
                // the Save/Reject buttons look broken (the listing would
                // silently reappear in the results a few cycles later).
                var changed = false
                var updated = existing
                // Bug fix: the on-screen/background scan re-runs extraction
                // every few seconds against whatever is CURRENTLY rendered,
                // and for a listing already sitting on the results page that
                // resends the exact same short card-preview text every
                // single cycle -- text that is near-guaranteed to differ
                // from the fuller, more reliable description this same
                // listing already got from its one-time detail-page fetch
                // (see the loop below / HeadlessDivarScanner.fetchDetail).
                // Treating that constant mismatch as a real "change" made
                // an untouched listing get flagged `enriched` on almost
                // every cycle. Once a listing has a real detail
                // description, only genuinely new information (e.g. a
                // revealed phone number) should trigger reprocessing --
                // a plain list-preview re-scrape of the same old card
                // should never downgrade it or count as a change.
                if (item.description != null &&
                    item.description != existing.description &&
                    !existing.hasDetailDescription
                ) {
                    updated = updated.copy(description = item.description); changed = true
                }
                if (item.price != null && item.price != existing.price) {
                    updated = updated.copy(price = item.price); changed = true
                }
                if (item.area != null && item.area != existing.area) {
                    updated = updated.copy(area = item.area); changed = true
                }
                if (item.pricePerMeter != null && item.pricePerMeter != existing.pricePerMeter) {
                    updated = updated.copy(pricePerMeter = item.pricePerMeter); changed = true
                }
                if (item.contactPhone != null) {
                    val normalized = normalizeOrNull(item.contactPhone)
                    if (normalized != null && normalized != existing.contactPhone) {
                        updated = updated.copy(contactPhone = normalized); changed = true
                    }
                }
                if (changed) {
                    // Deliberately NOT forcing isVisible/isLikelyAgency here
                    // anymore — whether this listing stays visible is
                    // FilterPipeline's job, and it already guarantees an
                    // already-visible listing (see alreadyVisibleIds below)
                    // is never auto-hidden. Forcing it true unconditionally
                    // used to let a changed listing jump back into the
                    // results regardless of what the filters said about it.
                    enriched.add(updated)
                }
            }
        }

        val toProcess = brandNew + enriched
        if (toProcess.isEmpty()) return IngestResult(items.size, 0, 0)

        // Listings in `enriched` that were already part of the live results
        // before this cycle — protected from auto-hiding by FilterPipeline,
        // per the never-auto-hide guarantee. Brand-new listings are never
        // in this set: they haven't been decided on yet.
        val alreadyVisibleIds = enriched.filter { it.isVisible }.map { it.id }.toSet()

        // Actively go read each listing's real description from its detail
        // page in a headless, JS-rendering WebView (see
        // HeadlessDivarScanner.fetchDetail) — this is what lets the
        // "مشاور"/"املاک" filter (and everything else in FilterPipeline)
        // work against the actual ad text instead of just the short,
        // sometimes-unreliable preview visible on the search-results card.
        // Runs sequentially and best-effort: if a fetch fails or times out
        // for any reason, that listing simply keeps whatever weaker
        // description it already had (from the card, or the old stored
        // value) and still goes through the rest of the pipeline.
        //
        // Bug fix: brandNew listings are inserted a few lines above with
        // isVisible defaulting to true (see ListingEntity.isVisible).
        // Everything from here down — the detail fetch loop AND
        // filterPipeline.apply() (which reads the keyword-filter table,
        // calls OwnerDetector, ListingEnricher, etc.) — used to run with
        // no local error handling at all. The ONLY safety net was
        // BackgroundScanService's outer `catch (e: Exception) {}`, which
        // swallowed the exception with no logging and moved on to the
        // next saved search. That meant: if any single listing or filter
        // triggered an exception anywhere in this stage, every brand-new
        // listing already inserted this cycle was left sitting in the DB
        // at its isVisible=true default — permanently, since a listing is
        // only ever re-touched by this same crash-prone stage — with the
        // exclude keyword filters never having had a chance to run on it
        // at all. From the user's side this looks exactly like "filters
        // stopped working": ads that should have been rejected just stay
        // in the results, forever, and there is no error anywhere to
        // explain why. Catching it HERE, logging the real cause, and
        // failing safe (hide rather than show) closes that hole.
        try {
            for ((index, listing) in toProcess.withIndex()) {
                val detail = headlessDivarScanner.fetchDetail(listing.url)
                if (!detail?.description.isNullOrBlank()) {
                    listing.description = detail?.description
                    listing.hasDetailDescription = true
                }
                if (!detail?.contactPhone.isNullOrBlank()) {
                    normalizeOrNull(detail?.contactPhone)?.let { listing.contactPhone = it }
                }
                // Small, deliberate pacing between requests: a cycle that
                // surfaces many new listings at once would otherwise fire
                // their detail-page fetches back-to-back, which looks like a
                // scraping burst to Divar and risks the IP getting
                // rate-limited or temporarily blocked. Skipped after the last
                // item so this never adds a trailing delay for nothing.
                if (index < toProcess.lastIndex) {
                    delay(DETAIL_FETCH_SPACING_MS)
                }
            }

            val settings = appSettingsDao.get()
            val surviving = filterPipeline.apply(
                savedSearch = savedSearch,
                listings = toProcess,
                anthropicApiKey = settings?.anthropicApiKey,
                anthropicModel = settings?.anthropicModel ?: "claude-haiku-4-5-20251001",
                alreadyVisibleIds = alreadyVisibleIds,
            )

            for (listing in toProcess) listingDao.update(listing)

            // Final, redundant safety net right before a notification is ever sent.
            // `surviving` should already exclude every "exclude"-type
            // keyword-filter-matched listing (FilterPipeline checks it before
            // this point), but the user was explicit that this must NEVER be
            // allowed to slip through, so every ENABLED exclude filter is
            // checked one more time, independently, at the very last moment —
            // if either the title or the description matches any one of them,
            // no notification. "owner_signal" rows (e.g. "من مالک هستم") are
            // deliberately left out of this recheck: a match there is a reason
            // TO notify, never a reason to block one.
            val activeExcludeFilters = keywordFilterDao.getAllEnabled().filter { it.filterType != "owner_signal" }
            val toNotify = surviving.filter { listing ->
                !listing.notified &&
                    KeywordFilterEngine.findFirstMatch(listing.title, listing.description, activeExcludeFilters) == null
            }
            for (listing in toNotify) {
                if (settings?.notificationsEnabled != false) {
                    localNotifier.notifyNewListing(listing)
                }
                listingDao.update(listing.copy(notified = true))
            }

            return IngestResult(received = items.size, new = brandNew.size, passedFilters = surviving.size)
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Filter pipeline crashed for savedSearchId=$savedSearchId while processing " +
                    "${toProcess.size} listing(s) — hiding them instead of leaving them visible " +
                    "unfiltered. Root cause:",
                e,
            )
            // Fail safe: a brand-new listing that never made it through the
            // filters must never default to visible. Already-enriched
            // listings that were already visible keep whatever visibility
            // they had (never-auto-hide guarantee), everything else is hidden.
            for (listing in toProcess) {
                if (listing.id !in alreadyVisibleIds) listing.isVisible = false
                listingDao.update(listing)
            }
            return IngestResult(received = items.size, new = brandNew.size, passedFilters = 0)
        }
    }

    /**
     * Bug fix: contactPhone used to be stored exactly as it came off the
     * page (sometimes "+98912...", sometimes "0912...", sometimes with
     * dashes/spaces from the tel: link) while every OTHER phone comparison
     * in the app (blocklist, keyword-adjacent checks) went through
     * [PhoneFilter.normalizePhone] first. Two representations of the same
     * real-world number therefore looked like two different numbers to
     * anything comparing raw contactPhone values — see
     * [com.divarsmartsearch.app.data.filters.ListingEnricher.computePhoneRepeatCount]
     * and the SQL in [com.divarsmartsearch.app.data.local.dao.ListingDao].
     * Normalizing once, right here at storage time, means every later
     * comparison is apples-to-apples.
     */
    private fun normalizeOrNull(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val normalized = PhoneFilter.normalizePhone(raw)
        return normalized.ifBlank { null }
    }

    private companion object {
        const val TAG = "ListingIngestion"
        const val DETAIL_FETCH_SPACING_MS = 600L
    }
}
