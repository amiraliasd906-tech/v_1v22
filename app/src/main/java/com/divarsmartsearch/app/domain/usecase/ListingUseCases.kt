package com.divarsmartsearch.app.domain.usecase

import com.divarsmartsearch.app.domain.model.HistoryTab
import com.divarsmartsearch.app.domain.model.Listing
import com.divarsmartsearch.app.domain.model.SellerReport
import com.divarsmartsearch.app.domain.repository.ListingRepository
import com.divarsmartsearch.app.util.AppResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetVisibleListingsUseCase @Inject constructor(
    private val repository: ListingRepository
) {
    suspend operator fun invoke(searchId: Int? = null): AppResult<List<Listing>> =
        repository.getVisibleListings(searchId)
}

/** Live results feed: emits again automatically whenever the background scanner adds/updates a listing. */
class ObserveVisibleListingsUseCase @Inject constructor(
    private val repository: ListingRepository
) {
    operator fun invoke(searchId: Int? = null): Flow<List<Listing>> =
        repository.observeVisibleListings(searchId)
}

/** Live total count of visible (Results) listings, for all of them — not just whatever's currently scrolled into view. */
class ObserveVisibleListingsCountUseCase @Inject constructor(
    private val repository: ListingRepository
) {
    operator fun invoke(searchId: Int? = null): Flow<Int> = repository.observeVisibleListingsCount(searchId)
}

class GetListingHistoryUseCase @Inject constructor(
    private val repository: ListingRepository
) {
    suspend operator fun invoke(tab: HistoryTab): AppResult<List<Listing>> =
        repository.getHistory(tab)
}

/** Live total count for a History tab (Seen/Saved/Rejected), for all three tabs at once regardless of which is selected. */
class ObserveHistoryCountUseCase @Inject constructor(
    private val repository: ListingRepository
) {
    operator fun invoke(tab: HistoryTab): Flow<Int> = repository.observeHistoryCount(tab)
}

class MarkListingSeenUseCase @Inject constructor(
    private val repository: ListingRepository
) {
    suspend operator fun invoke(listingId: Int): AppResult<Unit> = repository.markSeen(listingId)
}

class SaveListingUseCase @Inject constructor(
    private val repository: ListingRepository
) {
    suspend operator fun invoke(listingId: Int): AppResult<Unit> = repository.saveListing(listingId)
}

class RejectListingUseCase @Inject constructor(
    private val repository: ListingRepository
) {
    suspend operator fun invoke(listingId: Int): AppResult<Unit> = repository.rejectListing(listingId)
}

/** Undoes a reject — the action behind the Rejected tab's "restore" button. */
class RestoreListingUseCase @Inject constructor(
    private val repository: ListingRepository
) {
    suspend operator fun invoke(listingId: Int): AppResult<Unit> = repository.restoreListing(listingId)
}

class GetSellerReportUseCase @Inject constructor(
    private val repository: ListingRepository
) {
    suspend operator fun invoke(phoneNumber: String): AppResult<SellerReport> =
        repository.getSellerReport(phoneNumber)
}

/**
 * Re-checks every currently visible listing against the filters/settings as
 * they exist right now (e.g. a keyword filter added after those listings
 * first appeared), with no never-auto-hide protection: anything that no
 * longer passes gets hidden from the results and recorded as rejected.
 */
class ReapplyFiltersUseCase @Inject constructor(
    private val repository: ListingRepository
) {
    /** Returns how many previously-visible listings just got hidden. */
    suspend operator fun invoke(): AppResult<Int> = repository.reapplyFilters()
}
