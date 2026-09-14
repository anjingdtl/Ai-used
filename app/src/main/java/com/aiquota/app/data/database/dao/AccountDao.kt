package com.aiquota.app.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aiquota.app.data.database.entity.ProviderAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM provider_account ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ProviderAccountEntity>>

    @Query("SELECT * FROM provider_account ORDER BY createdAt DESC")
    suspend fun getAll(): List<ProviderAccountEntity>

    @Query("SELECT * FROM provider_account WHERE id = :id")
    suspend fun getById(id: String): ProviderAccountEntity?

    @Query("SELECT * FROM provider_account WHERE id = :id")
    fun observeById(id: String): Flow<ProviderAccountEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: ProviderAccountEntity)

    @Query("UPDATE provider_account SET enabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, now: Long)

    @Delete
    suspend fun delete(account: ProviderAccountEntity)
}