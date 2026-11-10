package com.lotto.app.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey(autoGenerate = true)
    val saleId: Long = 0L,
    val customerFirstName: String,
    val customerLastName: String,
    val totalAmount: Long,
    val cashAmount: Long,
    val creditAmount: Long,
    val createdAt: Long,
    val status: String = "CONFIRMED",
    val drawDate: String? = null,
    val customerId: Long? = null,
    val customerPhone: String? = null,
    @ColumnInfo(defaultValue = "0")
    val legacyDebtReviewed: Boolean = false
)
