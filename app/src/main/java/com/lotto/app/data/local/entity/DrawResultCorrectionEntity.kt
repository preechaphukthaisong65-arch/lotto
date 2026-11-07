package com.lotto.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "draw_result_corrections",
    indices = [Index("drawDate"), Index("correctedAt")]
)
data class DrawResultCorrectionEntity(
    @PrimaryKey(autoGenerate = true) val correctionId: Long = 0L,
    val drawDate: String,
    val previousThreeDigit: String,
    val previousTwoDigitTop: String,
    val previousTwoDigitBottom: String,
    val newThreeDigit: String,
    val newTwoDigitTop: String,
    val newTwoDigitBottom: String,
    val reason: String,
    val correctedAt: Long
)
