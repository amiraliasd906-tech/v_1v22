package com.divarsmartsearch.app.domain.repository

import com.divarsmartsearch.app.domain.model.HistoryTab
import com.divarsmartsearch.app.domain.model.Listing
import com.divarsmartsearch.app.domain.model.SellerReport
import com.divarsmartsearch.app.util.AppResult
import kotlinx.coroutines.flow.Flow

interface ListingRepository {
    suspend fun getVisibleListings(searchId: Int? = null): AppResult<List<Listing>>

    /**
     * Live view of the visible listings, straight from the database. The
     * background scanner inserts new listings independently of any screen
     * being open, so the Results screen must observe this Flow (not just
     * fetch once) or newly ingested listings never appear until the
     * ViewModel happens to be recreated.
     */
    fun observeVisibleListings(searchId: Int? = null): Flow<List<Listing>>

    /** Live total count of visible (Results) listings — the real total across everything, not just what's currently scrolled into view. */
    fun observeVisibleListingsCount(searchId: Int? = null): Flow<Int>

    suspend fun getHistory(tab: HistoryTab): AppResult<List<Listing>>

    /**
     * Live total count for [tab], independent of which tab is currently
     * selected on screen — lets the UI show every tab's total (e.g. in the
     * tab label) all at once instead of only the count for whichever tab
     * happens to be open.
     */
    fun observeHistoryCount(tab: HistoryTab): Flow<Int>

    suspend fun markSeen(listingId: Int): AppResult<Unit>
    suspend fun saveListing(listingId: Int): AppResult<Unit>
    suspend fun rejectListing(listingId: Int): AppResult<Unit>

    /**
     * Undoes a reject: the counterpart action for a card sitting in the
     * Rejected history tab. Re-rejecting an already-rejected listing (the
     * old behavior of that tab's "close" button) changed nothing the user
     * could see — the card stayed put with its status unchanged, which
     * read as the button being broken. This instead reverses the decision:
     * makes the listing visible again in Results, clears the
     * userDecided lock so future scans can touch it again, and removes its
     * divarToken from the global rejected blacklist so it isn't silently
     * skipped by [com.divarsmartsearch.app.data.webview.ListingIngestionService]
     * on the next scan.
     */
    suspend fun restoreListing(listingId: Int): AppResult<Unit>

    /** Every stored listing (any search) that shares [phoneNumber], aggregated for display. */
    suspend fun getSellerReport(phoneNumber: String): AppResult<SellerReport>

    /**
     * Re-runs the filter pipeline against every listing currently showing
     * in the results list, using whatever keyword filters / owner-detection
     * settings exist *right now* — not whatever they were when each
     * listing was first ingested. This is what lets a keyword filter (or
     * threshold change) the person just added actually take effect on
     * listings that are already sitting in the list, instead of only
     * affecting listings scanned after the change.
     *
     * Deliberately scoped to isVisible=true listings only: anything the
     * person already saved or rejected keeps that outcome regardless of
     * filter changes, since that was a manual decision, not a filter one.
     *
     * Returns how many previously-visible listings just got hidden by this
     * pass, so callers (e.g. right after adding a new keyword filter) can
     * tell the person how many existing results were just moved into the
     * Rejected/"حذف‌شده‌ها" tab.
     */
    suspend fun reapplyFilters(): AppResult<Int>
}
