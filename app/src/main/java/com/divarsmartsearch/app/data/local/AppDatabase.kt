package com.divarsmartsearch.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.divarsmartsearch.app.data.local.dao.AppSettingsDao
import com.divarsmartsearch.app.data.local.dao.BlockedPhoneDao
import com.divarsmartsearch.app.data.local.dao.KeywordFilterDao
import com.divarsmartsearch.app.data.local.dao.ListingDao
import com.divarsmartsearch.app.data.local.dao.ListingInteractionDao
import com.divarsmartsearch.app.data.local.dao.RemovedListingDao
import com.divarsmartsearch.app.data.local.dao.SavedSearchDao
import com.divarsmartsearch.app.data.local.entity.AppSettingsEntity
import com.divarsmartsearch.app.data.local.entity.BlockedPhoneEntity
import com.divarsmartsearch.app.data.local.entity.KeywordFilterEntity
import com.divarsmartsearch.app.data.local.entity.ListingEntity
import com.divarsmartsearch.app.data.local.entity.ListingInteractionEntity
import com.divarsmartsearch.app.data.local.entity.RemovedListingEntity
import com.divarsmartsearch.app.data.local.entity.SavedSearchEntity

/**
 * Single on-device database. Everything the app needs (searches,
 * listings, permanent phone filters, history, settings) lives here —
 * there is no remote server. See README for why this replaced the
 * earlier FastAPI backend design.
 */
@Database(
    entities = [
        SavedSearchEntity::class,
        ListingEntity::class,
        BlockedPhoneEntity::class,
        ListingInteractionEntity::class,
        AppSettingsEntity::class,
        KeywordFilterEntity::class,
        RemovedListingEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedSearchDao(): SavedSearchDao
    abstract fun listingDao(): ListingDao
    abstract fun blockedPhoneDao(): BlockedPhoneDao
    abstract fun listingInteractionDao(): ListingInteractionDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun keywordFilterDao(): KeywordFilterDao
    abstract fun removedListingDao(): RemovedListingDao

    companion object {
        const val DATABASE_NAME = "divar_smart_search.db"
    }
}
