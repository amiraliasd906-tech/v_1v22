package com.divarsmartsearch.app.data.repository

import com.divarsmartsearch.app.data.filters.FilterPipeline
import com.divarsmartsearch.app.data.filters.PhoneFilter
import com.divarsmartsearch.app.data.local.dao.AppSettingsDao
import com.divarsmartsearch.app.data.local.dao.ListingDao
import com.divarsmartsearch.app.data.local.dao.ListingInteractionDao
import com.divarsmartsearch.app.data.local.dao.RemovedListingDao
import com.divarsmartsearch.app.data.local.dao.SavedSearchDao
import com.divarsmartsearch.app.data.local.entity.ListingInteractionEntity
import com.divarsmartsearch.app.data.local.entity.RemovedListingEntity
import com.divarsmartsearch.app.data.local.toDomain
import com.divarsmartsearch.app.domain.model.HistoryTab
import com.divarsmartsearch.app.domain.model.Listing
import com.divarsmartsearch.app.domain.model.SellerReport
import com.divarsmartsearch.app.domain.repository.ListingRepository
import com.divarsmartsearch.app.util.AppResult
import com.divarsmartsearch.app.util.safeCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListingRepositoryImpl @Inject constructor(
    private val listingDao: ListingDao,
    private val interactionDao: ListingInteractionDao,
    private val savedSearchDao: SavedSearchDao,
    private val appSettingsDao: AppSettingsDao,
    private val filterPipeline: FilterPipeline,
    private val removedListingDao: RemovedListingDao,
) : ListingRepository {

    override suspend fun getVisibleListings(searchId: Int?): AppResult<List<Listing>> = safeCall {
        listingDao.observeVisible(searchId?.toLong()).first().map { it.toDomain() }
    }

    override fun observeVisibleListings(searchId: Int?): Flow<List<Listing>> =
        listingDao.observeVisible(searchId?.toLong()).map { entities -> entities.map { it.toDomain() } }

    override fun observeVisibleListingsCount(searchId: Int?): Flow<Int> =
        listingDao.observeVisibleCount(searchId?.toLong())

    override suspend fun getHistory(tab: HistoryTab): AppResult<List<Listing>> = safeCall {
        val status = when (tab) {
            HistoryTab.SEEN -> "seen"
            HistoryTab.SAVED -> "saved"
            HistoryTab.REJECTED -> "rejected"
        }
        listingDao.observeByInteractionStatus(status).first().map { it.toDomain() }
    }

    override fun observeHistoryCount(tab: HistoryTab): Flow<Int> {
        val status = when (tab) {
            HistoryTab.SEEN -> "seen"
            HistoryTab.SAVED -> "saved"
            HistoryTab.REJECTED -> "rejected"
        }
        return listingDao.observeInteractionStatusCount(status)
    }

    override suspend fun markSeen(listingId: Int): AppResult<Unit> = safeCall {
        interactionDao.insert(ListingInteractionEntity(listingId = listingId.toLong(), status = "seen"))
    }

    override suspend fun saveListing(listingId: Int): AppResult<Unit> = safeCall {
        interactionDao.insert(ListingInteractionEntity(listingId = listingId.toLong(), status = "saved"))
        // Without this, the listing's `isVisible` flag never changes, so
        // ResultsViewModel's live observeVisibleListings() Flow (see
        // ListingDao.observeVisible) keeps re-emitting it on every
        // collection and silently undoes the screen's optimistic removal —
        // the button looked like it did nothing. `userDecided = true`
        // additionally makes this permanent: no later background scan is
        // allowed to touch isVisible on this listing again (see
        // ListingIngestionService.ingest()), so a saved listing can never
        // silently reappear in the live results.
        val listing = listingDao.getById(listingId.toLong())
        if (listing != null) {
            listingDao.update(listing.copy(isVisible = false, userDecided = true))
        }
    }

    override suspend fun rejectListing(listingId: Int): AppResult<Unit> = safeCall {
        interactionDao.insert(
            ListingInteractionEntity(
                listingId = listingId.toLong(),
                status = "rejected",
                rejectionReason = "user_rejected",
            )
        )
        val listing = listingDao.getById(listingId.toLong())
        if (listing != null) {
            listingDao.update(listing.copy(isVisible = false, userDecided = true))
            // Per explicit user request: a rejected ad must never come back,
            // no matter how many times it gets re-scraped — not on the next
            // cycle, not under a different/recreated saved search. Recording
            // its divarToken here (independent of this `listings` row) is
            // what ListingIngestionService.ingest() checks before it will
            // ever insert a "new" listing again — see RemovedListingEntity.
            removedListingDao.insert(RemovedListingEntity(divarToken = listing.divarToken))
        }
    }

    override suspend fun restoreListing(listingId: Int): AppResult<Unit> = safeCall {
        interactionDao.insert(ListingInteractionEntity(listingId = listingId.toLong(), status = "seen"))
        val listing = listingDao.getById(listingId.toLong())
        if (listing != null) {
            listingDao.update(listing.copy(isVisible = true, userDecided = false))
            removedListingDao.delete(listing.divarToken)
        }
    }

    override suspend fun reapplyFilters(): AppResult<Int> = safeCall {
        val visible = listingDao.observeVisible(null).first()
        if (visible.isEmpty()) return@safeCall 0

        val settings = appSettingsDao.get()
        // Per explicit user request, a manual refresh re-checks every
        // currently visible listing against the filters as they exist right
        // now with NO protection — pass no alreadyVisibleIds, so the
        // never-auto-hide guarantee (which exists for the background
        // scanner) does not apply here. Anything that no longer passes
        // (range, blocked phone, keyword exclude, or agency probability)
        // gets isVisible = false and a "rejected" interaction row (see
        // FilterPipeline.recordRejection), so it shows up in the Rejected
        // history tab exactly like a manual reject.

        // FilterPipeline.apply() works one saved-search at a time (range
        // filters like min/max price come from that search), so listings
        // are regrouped by savedSearchId before each pass.
        var hiddenCount = 0
        for ((savedSearchId, group) in visible.groupBy { it.savedSearchId }) {
            val savedSearch = savedSearchDao.getById(savedSearchId) ?: continue
            val kept = filterPipeline.apply(
                savedSearch = savedSearch,
                listings = group,
                anthropicApiKey = settings?.anthropicApiKey,
                anthropicModel = settings?.anthropicModel ?: "claude-haiku-4-5-20251001",
            )
            // Anything in `group` that didn't survive into `kept` just got
            // isVisible = false during this pass (see FilterPipeline.apply).
            hiddenCount += group.size - kept.size
            for (listing in group) listingDao.update(listing)
        }
        hiddenCount
    }

    override suspend fun getSellerReport(phoneNumber: String): AppResult<SellerReport> = safeCall {
        val normalized = PhoneFilter.normalizePhone(phoneNumber)
        val entities = listingDao.getListingsForPhone(normalized)
        val listings = entities.map { it.toDomain() }

        val agencyPercents = listings.mapNotNull { it.ownerProbability }.map { (1.0 - it) * 100 }

        SellerReport(
            phoneNumber = normalized,
            totalListings = listings.size,
            cities = listings.mapNotNull { it.city }.distinct(),
            neighborhoods = listings.mapNotNull { it.neighborhood }.distinct(),
            averageAgencyLikelihoodPercent = if (agencyPercents.isEmpty()) null else {
                (agencyPercents.sum() / agencyPercents.size).toInt()
            },
            listings = listings,
        )
    }
}
