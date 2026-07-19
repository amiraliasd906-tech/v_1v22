package com.divarsmartsearch.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.divarsmartsearch.app.data.local.entity.AppSettingsEntity
import com.divarsmartsearch.app.data.local.entity.ListingInteractionEntity
import com.divarsmartsearch.app.data.local.entity.RemovedListingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ListingInteractionDao {
    @Insert
    suspend fun insert(entity: ListingInteractionEntity)
}

/** See [RemovedListingEntity] KDoc: the global "حذف‌شده‌ها" blacklist. */
@Dao
interface RemovedListingDao {
    // REPLACE (not ABORT/IGNORE): re-rejecting an already-removed token just
    // refreshes removedAt instead of erroring out.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RemovedListingEntity)

    @Query("SELECT divarToken FROM removed_listings")
    suspend fun getAllTokens(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM removed_listings WHERE divarToken = :token)")
    suspend fun isRemoved(token: String): Boolean

    /** Used when the user restores a previously-rejected listing (undoes the reject). */
    @Query("DELETE FROM removed_listings WHERE divarToken = :token")
    suspend fun delete(token: String)
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observe(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun get(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppSettingsEntity)
}
