package com.lotto.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "draw_profit_summaries")
data class DrawProfitSummaryEntity(
    @PrimaryKey val drawDate: String,
    val salesTotal: Long,
    val prizeLiability: Long,
    val netProfit: Long,
    val recordedAt: Long
)
