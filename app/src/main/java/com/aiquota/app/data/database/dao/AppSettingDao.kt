package com.aiquota.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aiquota.app.data.database.entity.AppSettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: AppSettingEntity)

    @Query("SELECT * FROM app_setting WHERE `key` = :key")
    fun observe(key: String): Flow<AppSettingEntity?>

    @Query("SELECT * FROM app_setting WHERE `key` = :key")
    suspend fun get(key: String): AppSettingEntity?

    @Query("DELETE FROM app_setting WHERE `key` = :key")
    suspend fun delete(key: String)
}