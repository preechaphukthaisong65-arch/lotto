package com.lotto.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "setting_audit_log",
    indices = [Index(value = ["settingKey"]), Index(value = ["changedAt"])]
)
data class SettingAuditEntity(
    @PrimaryKey(autoGenerate = true) val auditId: Long = 0,
    val settingKey: String,
    val previousValue: String?,
    val newValue: String?,
    val changedAt: Long
)
