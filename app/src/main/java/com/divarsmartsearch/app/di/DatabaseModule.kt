package com.divarsmartsearch.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.divarsmartsearch.app.data.local.AppDatabase
import com.divarsmartsearch.app.data.local.dao.AppSettingsDao
import com.divarsmartsearch.app.data.local.dao.BlockedPhoneDao
import com.divarsmartsearch.app.data.local.dao.KeywordFilterDao
import com.divarsmartsearch.app.data.local.dao.ListingDao
import com.divarsmartsearch.app.data.local.dao.ListingInteractionDao
import com.divarsmartsearch.app.data.local.dao.RemovedListingDao
import com.divarsmartsearch.app.data.local.dao.SavedSearchDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Everything the app needs lives in a single on-device Room database —
 * there is no remote server/backend in this build (see README).
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * The four keyword filters the app ships with, per explicit user
     * request: each word is now its own independent, toggleable filter
     * row (instead of one hardcoded combined list) — see
     * [com.divarsmartsearch.app.data.filters.KeywordFilterEngine].
     * Triple = (label shown to the user, matched root, category — used
     * only to pick an icon/color in the UI).
     */
    /**
     * The keyword filters the app ships with. Fourth element is
     * [KeywordFilterEntity.filterType] — "exclude" (reject on match) or
     * "owner_signal" (trust the ad's own claim, skip agency-probability
     * checks for it) — see the KDoc on that entity for the full story.
     */
    private data class KeywordSeed(val label: String, val keyword: String, val category: String, val filterType: String)

    // Per explicit user request: the app no longer ships with any
    // pre-installed keyword filters. Every filter word (exclude or
    // owner-signal) is now something the user adds themselves from the
    // "فیلترهای دائمی" screen — nothing is chosen for them in advance.
    private val defaultKeywordFilters = emptyList<KeywordSeed>()

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration() // fine for a personal, single-user local DB
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    val now = System.currentTimeMillis()
                    defaultKeywordFilters.forEach { seed ->
                        db.execSQL(
                            "INSERT INTO keyword_filters (label, keyword, category, filterType, isEnabled, isBuiltIn, createdAt) " +
                                "VALUES (?, ?, ?, ?, 1, 1, ?)",
                            arrayOf(seed.label, seed.keyword, seed.category, seed.filterType, now),
                        )
                    }
                }

                // People who installed an earlier version of the app still
                // have the old pre-installed keyword rows (دفتر/املاک/مشاور/
                // کلید) sitting in their database, marked isBuiltIn = 1 —
                // and the old code refused to ever delete those rows. Per
                // explicit user request nothing is protected anymore, so on
                // every app open we clear out any leftover isBuiltIn row
                // automatically. This runs every time the DB is opened, not
                // just once, but it's a no-op after the first cleanup since
                // no code path inserts isBuiltIn = 1 rows anymore.
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("DELETE FROM keyword_filters WHERE isBuiltIn = 1")
                }
            })
            .build()

    @Provides
    fun provideSavedSearchDao(db: AppDatabase): SavedSearchDao = db.savedSearchDao()

    @Provides
    fun provideListingDao(db: AppDatabase): ListingDao = db.listingDao()

    @Provides
    fun provideBlockedPhoneDao(db: AppDatabase): BlockedPhoneDao = db.blockedPhoneDao()

    @Provides
    fun provideListingInteractionDao(db: AppDatabase): ListingInteractionDao = db.listingInteractionDao()

    @Provides
    fun provideAppSettingsDao(db: AppDatabase): AppSettingsDao = db.appSettingsDao()

    @Provides
    fun provideKeywordFilterDao(db: AppDatabase): KeywordFilterDao = db.keywordFilterDao()

    @Provides
    fun provideRemovedListingDao(db: AppDatabase): RemovedListingDao = db.removedListingDao()
}
