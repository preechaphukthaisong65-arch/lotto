package com.lotto.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "draw_results")
data class DrawResultEntity(
    @PrimaryKey val drawDate: String,
    val threeDigit: String,
    val twoDigitTop: String,
    val twoDigitBottom: String,
    val recordedAt: Long
)
