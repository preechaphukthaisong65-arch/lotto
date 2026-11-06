package com.lotto.app.data.repository

import com.lotto.app.data.local.dao.AppSettingDao
import com.lotto.app.data.local.entity.AppSettingEntity
import kotlinx.coroutines.flow.Flow

class RoomSettingsRepository(
    private val appSettingDao: AppSettingDao
) : SettingsRepository {

    override suspend fun get(key: String): String? {
        return appSettingDao.getByKey(key)?.value
    }

    override fun observeAll(): Flow<List<AppSettingEntity>> {
        return appSettingDao.observeAll()
    }

    override suspend fun save(key: String, value: String) {
        appSettingDao.upsert(
            AppSettingEntity(
                key = key,
                value = value,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun delete(key: String) {
        appSettingDao.deleteByKey(key)
    }
}
