package com.lotto.app.data.repository

import com.lotto.app.data.local.dao.AppSettingDao
import com.lotto.app.data.local.entity.AppSettingEntity
import com.lotto.app.data.local.entity.SettingAuditEntity
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

    override fun observeAuditLog(): Flow<List<SettingAuditEntity>> =
        appSettingDao.observeAuditLog()

    override suspend fun save(key: String, value: String) {
        appSettingDao.saveWithAudit(
            AppSettingEntity(
                key = key,
                value = value,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun delete(key: String) {
        appSettingDao.deleteWithAudit(key, System.currentTimeMillis())
    }
}
