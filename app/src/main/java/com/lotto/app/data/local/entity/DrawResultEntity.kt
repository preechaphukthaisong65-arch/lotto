package com.lotto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "draw_results")
data class DrawResultEntity(
    @PrimaryKey val drawDate: String,
    val threeDigit: String,
    val twoDigitTop: String,
    val twoDigitBottom: String,
    val recordedAt: Long,
    @ColumnInfo(defaultValue = "0")
    val payoutLocked: Boolean = false,
    @ColumnInfo(defaultValue = "'LEGACY_MANUAL'")
    val source: String = "LEGACY_MANUAL",
    @ColumnInfo(defaultValue = "'UNRESOLVED'")
    val verificationDecision: String = "UNRESOLVED",
    val verificationRunId: String? = null,
    val approvedAt: Long? = null
)
