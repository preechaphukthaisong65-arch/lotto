package com.lotto.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sale_void_events",
    foreignKeys = [
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["saleId"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["saleId"], unique = true), Index("voidedAt")]
)
data class SaleVoidEventEntity(
    @PrimaryKey(autoGenerate = true) val voidId: Long = 0L,
    val saleId: Long,
    val drawDate: String,
    val reason: String,
    val cashRefundAmount: Long,
    val cancelledCreditAmount: Long,
    val voidedAt: Long
)
