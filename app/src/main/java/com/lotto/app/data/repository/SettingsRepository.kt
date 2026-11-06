package com.lotto.app.data.repository

import com.lotto.app.data.local.entity.AppSettingEntity
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    suspend fun get(key: String): String?

    fun observeAll(): Flow<List<AppSettingEntity>>

    suspend fun save(key: String, value: String)

    suspend fun delete(key: String)
}
