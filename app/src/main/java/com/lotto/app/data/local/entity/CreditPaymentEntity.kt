package com.lotto.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "credit_payments",
    foreignKeys = [
        ForeignKey(
            entity = CreditDocumentEntity::class,
            parentColumns = ["creditDocumentId"],
            childColumns = ["creditDocumentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("creditDocumentId")]
)
data class CreditPaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val paymentId: Long = 0L,
    val creditDocumentId: Long,
    val amount: Long,
    val paidAt: Long
)
