package com.lotto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "draw_profit_summaries")
data class DrawProfitSummaryEntity(
    @PrimaryKey val drawDate: String,
    val salesTotal: Long,
    val prizeLiability: Long,
    val netProfit: Long,
    val recordedAt: Long,
    @ColumnInfo(defaultValue = "'NEEDS_REVIEW'")
    val dataQuality: String = QUALITY_COMPLETE
)

const val QUALITY_COMPLETE = "COMPLETE"
const val QUALITY_NEEDS_REVIEW = "NEEDS_REVIEW"
