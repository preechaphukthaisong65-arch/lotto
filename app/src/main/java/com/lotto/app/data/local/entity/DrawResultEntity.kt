package com.lotto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "draw_results")
data class DrawResultEntity(
    @PrimaryKey val drawDate: String,
    val threeDigit: String,
    // Persisted for backward-compatible Room storage; always derived from threeDigit.takeLast(2).
    val twoDigitTop: String,
    val twoDigitBottom: String,
    val recordedAt: Long,
    @ColumnInfo(defaultValue = "0")
    val payoutLocked: Boolean = false
)
