package com.lotto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.lotto.app.data.local.entity.AppSettingEntity
import com.lotto.app.data.local.entity.SettingAuditEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AppSettingDao {
    @Query("SELECT * FROM app_settings WHERE key = :key LIMIT 1")
    abstract suspend fun getByKey(key: String): AppSettingEntity?

    @Query("SELECT * FROM app_settings ORDER BY key ASC")
    abstract fun observeAll(): Flow<List<AppSettingEntity>>

    @Query("SELECT * FROM setting_audit_log ORDER BY changedAt DESC, auditId DESC LIMIT 500")
    abstract fun observeAuditLog(): Flow<List<SettingAuditEntity>>

    @Upsert
    protected abstract suspend fun upsertInternal(setting: AppSettingEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertAudit(entry: SettingAuditEntity)

    @Query("DELETE FROM app_settings WHERE key = :key")
    protected abstract suspend fun deleteInternal(key: String)

    @Transaction
    open suspend fun saveWithAudit(setting: AppSettingEntity) {
        val previous = getByKey(setting.key)
        val highWaterRaw = getByKey("clock_high_water_epoch_ms")?.value
        val highWater = highWaterRaw?.toLongOrNull()
            ?: if (highWaterRaw == null) 0L else throw IllegalStateException("ข้อมูลตรวจสอบเวลาของระบบเสียหาย")
        if (setting.key == "clock_high_water_epoch_ms") {
            val requested = setting.value.toLongOrNull()
                ?: throw IllegalArgumentException("ค่าเวลาตรวจสอบระบบไม่ถูกต้อง")
            require(requested >= highWater) { "ไม่อนุญาตให้ลดเวลาตรวจสอบสูงสุดของระบบ" }
        } else {
            require(setting.updatedAt >= highWater) {
                "ตรวจพบนาฬิกาเครื่องเดินย้อนเวลา ไม่สามารถเปลี่ยนการตั้งค่าได้"
            }
        }
        upsertInternal(setting)
        if (setting.key != "clock_high_water_epoch_ms") {
            insertAudit(
                SettingAuditEntity(
                    settingKey = setting.key,
                    previousValue = previous?.value,
                    newValue = setting.value,
                    changedAt = setting.updatedAt
                )
            )
        }
    }

    @Transaction
    open suspend fun deleteWithAudit(key: String, changedAt: Long) {
        require(key != "clock_high_water_epoch_ms") { "ไม่อนุญาตให้ลบข้อมูลตรวจสอบเวลาของระบบ" }
        val highWaterRaw = getByKey("clock_high_water_epoch_ms")?.value
        val highWater = highWaterRaw?.toLongOrNull()
            ?: if (highWaterRaw == null) 0L else throw IllegalStateException("ข้อมูลตรวจสอบเวลาของระบบเสียหาย")
        require(changedAt >= highWater) {
            "ตรวจพบนาฬิกาเครื่องเดินย้อนเวลา ไม่สามารถเปลี่ยนการตั้งค่าได้"
        }
        val previous = getByKey(key) ?: return
        deleteInternal(key)
        insertAudit(
            SettingAuditEntity(
                settingKey = key,
                previousValue = previous.value,
                newValue = null,
                changedAt = changedAt
            )
        )
    }
}
