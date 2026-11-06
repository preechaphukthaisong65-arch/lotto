package com.lotto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lotto.app.data.local.entity.AppSettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingDao {
    @Query("SELECT * FROM app_settings WHERE key = :key LIMIT 1")
    suspend fun getByKey(key: String): AppSettingEntity?

    @Query("SELECT * FROM app_settings ORDER BY key ASC")
    fun observeAll(): Flow<List<AppSettingEntity>>

    @Upsert
    suspend fun upsert(setting: AppSettingEntity)

    @Query("DELETE FROM app_settings WHERE key = :key")
    suspend fun deleteByKey(key: String)
}
