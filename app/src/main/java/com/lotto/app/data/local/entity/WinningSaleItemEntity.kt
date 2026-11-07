package com.lotto.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "winning_sale_items",
    indices = [Index(value = ["drawDate", "saleItemId"], unique = true), Index("saleId")]
)
data class WinningSaleItemEntity(
    @PrimaryKey(autoGenerate = true) val winId: Long = 0L,
    val drawDate: String,
    val saleId: Long,
    val saleItemId: Long,
    val prizeType: String,
    val number: String,
    val stakeAmount: Long,
    val payoutMultiplier: Long,
    val prizeAmount: Long
)
