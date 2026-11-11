package com.lotto.app.data.repository

import com.lotto.app.data.local.entity.AppSettingEntity
import com.lotto.app.data.local.entity.SettingAuditEntity
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    suspend fun get(key: String): String?

    fun observeAll(): Flow<List<AppSettingEntity>>
    fun observeAuditLog(): Flow<List<SettingAuditEntity>>

    suspend fun save(key: String, value: String)
    suspend fun saveMany(values: Map<String, String>)

    suspend fun delete(key: String)
}
