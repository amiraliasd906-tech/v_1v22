package com.divarsmartsearch.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.divarsmartsearch.app.data.local.entity.ListingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ListingDao {

    @Query(
        "SELECT * FROM listings WHERE isVisible = 1 " +
            "AND (:savedSearchId IS NULL OR savedSearchId = :savedSearchId) " +
            "ORDER BY publishedAt DESC, firstSeenAt DESC"
    )
    fun observeVisible(savedSearchId: Long?): Flow<List<ListingEntity>>

    /**
     * Bug fix: a listing gets a NEW interaction row every time its status
     * changes (seen -> rejected -> ...) rather than the old row being
     * replaced, so the interaction table keeps a full history log. The old
     * version of this query joined on ANY row matching [status], so a
     * listing that was once "seen" and later "rejected" satisfied BOTH
     * queries forever — e.g. pressing reject on a card in the "دیده‌شده"
     * (Seen) tab never actually made it disappear from that tab, since it
     * still had an old "seen" row sitting in the table. This now joins
     * each listing to only its single MOST RECENT interaction row (highest
     * autoincrement id = most recently inserted) and matches [status]
     * against that alone, so only a listing's CURRENT status counts.
     */
    @Query(
        """
        SELECT listings.* FROM listings
        INNER JOIN listing_interactions li ON li.id = (
            SELECT li2.id FROM listing_interactions li2
            WHERE li2.listingId = listings.id
            ORDER BY li2.id DESC
            LIMIT 1
        )
        WHERE li.status = :status
        ORDER BY listings.firstSeenAt DESC
        """
    )
    fun observeByInteractionStatus(status: String): Flow<List<ListingEntity>>

    /**
     * Live total count for a given interaction status (seen/saved/rejected),
     * independent of which History tab is currently selected — used to show
     * every tab's total at once instead of only the one the user happens to
     * be looking at. Same "latest interaction row only" fix as
     * [observeByInteractionStatus] above, for the same reason.
     */
    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT listings.id FROM listings
            INNER JOIN listing_interactions li ON li.id = (
                SELECT li2.id FROM listing_interactions li2
                WHERE li2.listingId = listings.id
                ORDER BY li2.id DESC
                LIMIT 1
            )
            WHERE li.status = :status
            GROUP BY listings.id
        )
        """
    )
    fun observeInteractionStatusCount(status: String): Flow<Int>

    /** Live total count of currently visible (Results tab) listings — the true total, not just whatever has been scrolled into view. */
    @Query("SELECT COUNT(*) FROM listings WHERE isVisible = 1 AND (:savedSearchId IS NULL OR savedSearchId = :savedSearchId)")
    fun observeVisibleCount(savedSearchId: Long?): Flow<Int>

    @Query("SELECT * FROM listings WHERE savedSearchId = :savedSearchId")
    suspend fun getAllForSearch(savedSearchId: Long): List<ListingEntity>

    @Query("SELECT * FROM listings WHERE savedSearchId = :savedSearchId AND divarToken = :token LIMIT 1")
    suspend fun findByToken(savedSearchId: Long, token: String): ListingEntity?

    @Query("SELECT * FROM listings WHERE id = :id")
    suspend fun getById(id: Long): ListingEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ListingEntity): Long

    @Update
    suspend fun update(entity: ListingEntity)

    // --- Owner/agent-repetition, duplicate-detection & price-comparison support ---

    /**
     * Bug fix: this used to be `LIKE '%' || :phone || '%'`, a plain
     * substring match against the comma-joined detectedPhoneNumbers list.
     * That falsely matched any number that happened to be a substring of
     * another stored number (e.g. "0912345678" matching inside
     * "09123456789,..."), and got *more* likely to misfire the longer the
     * app had been running and the more numbers had accumulated. A repeated
     * phone number nudges OwnerDetector's agency-probability score up, so
     * even one such false match was enough to tip a perfectly fine,
     * unique-numbered listing over the 50% cutoff and make it disappear.
     * Every branch below only matches :phone as a whole comma-delimited token.
     */
    @Query(
        """
        SELECT * FROM listings
        WHERE contactPhone = :phone
           OR detectedPhoneNumbers = :phone
           OR detectedPhoneNumbers LIKE :phone || ',%'
           OR detectedPhoneNumbers LIKE '%,' || :phone
           OR detectedPhoneNumbers LIKE '%,' || :phone || ',%'
        ORDER BY firstSeenAt DESC
        """
    )
    suspend fun getListingsForPhone(phone: String): List<ListingEntity>

    /** Distinct listing count for [phone], excluding [excludeId] itself. Same boundary-safe matching as [getListingsForPhone]. */
    @Query(
        """
        SELECT COUNT(DISTINCT id) FROM listings
        WHERE id != :excludeId
          AND (contactPhone = :phone
               OR detectedPhoneNumbers = :phone
               OR detectedPhoneNumbers LIKE :phone || ',%'
               OR detectedPhoneNumbers LIKE '%,' || :phone
               OR detectedPhoneNumbers LIKE '%,' || :phone || ',%')
        """
    )
    suspend fun countListingsForPhone(phone: String, excludeId: Long): Int

    @Query(
        "SELECT AVG(pricePerMeter) FROM listings " +
            "WHERE pricePerMeter IS NOT NULL AND id != :excludeId AND neighborhood = :neighborhood"
    )
    suspend fun averagePricePerMeterForNeighborhood(neighborhood: String, excludeId: Long): Double?

    @Query(
        "SELECT AVG(pricePerMeter) FROM listings " +
            "WHERE pricePerMeter IS NOT NULL AND id != :excludeId AND city = :city"
    )
    suspend fun averagePricePerMeterForCity(city: String, excludeId: Long): Double?

    /**
     * Recent candidates to compare against for duplicate/republish
     * detection. Bounded to a reasonable window since this is a personal,
     * single-device database — a full table scan of a few hundred rows is
     * cheap and simpler than a complex fuzzy-match SQL query.
     */
    @Query(
        "SELECT * FROM listings WHERE id != :excludeId AND (:city IS NULL OR city = :city) " +
            "ORDER BY firstSeenAt DESC LIMIT 300"
    )
    suspend fun getRecentCandidatesForDuplicateCheck(city: String?, excludeId: Long): List<ListingEntity>
}
