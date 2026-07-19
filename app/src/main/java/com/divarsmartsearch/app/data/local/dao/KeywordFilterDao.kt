package com.divarsmartsearch.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.divarsmartsearch.app.data.local.entity.KeywordFilterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KeywordFilterDao {
    @Query("SELECT * FROM keyword_filters ORDER BY isBuiltIn DESC, createdAt ASC")
    fun observeAll(): Flow<List<KeywordFilterEntity>>

    @Query("SELECT * FROM keyword_filters ORDER BY isBuiltIn DESC, createdAt ASC")
    suspend fun getAll(): List<KeywordFilterEntity>

    /** Used by [com.divarsmartsearch.app.data.filters.FilterPipeline] on every run. */
    @Query("SELECT * FROM keyword_filters WHERE isEnabled = 1")
    suspend fun getAllEnabled(): List<KeywordFilterEntity>

    @Query("SELECT COUNT(*) FROM keyword_filters WHERE keyword = :keyword")
    suspend fun countByKeyword(keyword: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: KeywordFilterEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<KeywordFilterEntity>)

    @Query("UPDATE keyword_filters SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    // Per explicit user request every filter row — including any legacy
    // isBuiltIn row left over from an older app version — can be deleted
    // by the user. Nothing is protected anymore.
    @Query("DELETE FROM keyword_filters WHERE id = :id")
    suspend fun deleteById(id: Long)
}
