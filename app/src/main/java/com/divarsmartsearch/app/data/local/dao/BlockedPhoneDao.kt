package com.divarsmartsearch.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.divarsmartsearch.app.data.local.entity.BlockedPhoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedPhoneDao {
    @Query("SELECT * FROM blocked_phone_numbers ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BlockedPhoneEntity>>

    @Query("SELECT phoneNumber FROM blocked_phone_numbers")
    suspend fun getAllNumbers(): List<String>

    @Query("SELECT * FROM blocked_phone_numbers WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun findByNumber(phoneNumber: String): BlockedPhoneEntity?

    @Insert
    suspend fun insert(entity: BlockedPhoneEntity): Long

    @Delete
    suspend fun delete(entity: BlockedPhoneEntity)

    @Query("DELETE FROM blocked_phone_numbers WHERE id = :id")
    suspend fun deleteById(id: Long)
}
