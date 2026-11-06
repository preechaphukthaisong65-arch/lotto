package com.lotto.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sale_items",
    foreignKeys = [
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["saleId"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("saleId")]
)
data class SaleItemEntity(
    @PrimaryKey(autoGenerate = true)
    val itemId: Long = 0L,
    val saleId: Long,
    val number: String,
    val prizeType: String,
    val amount: Long,
    val payoutMultiplier: Long
)
