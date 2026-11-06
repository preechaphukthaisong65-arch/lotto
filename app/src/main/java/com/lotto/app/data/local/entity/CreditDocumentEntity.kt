package com.lotto.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "credit_documents")
data class CreditDocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val creditDocumentId: Long = 0L,
    val sourceSaleId: Long,
    val customerFirstName: String,
    val customerLastName: String,
    val saleTotalAmount: Long,
    val cashAmount: Long,
    val creditAmount: Long,
    val createdAt: Long,
    val status: String = "OPEN"
)

@Entity(
    tableName = "credit_document_items",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = CreditDocumentEntity::class,
            parentColumns = ["creditDocumentId"],
            childColumns = ["creditDocumentId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index("creditDocumentId")]
)
data class CreditDocumentItemEntity(
    @PrimaryKey(autoGenerate = true)
    val creditItemId: Long = 0L,
    val creditDocumentId: Long,
    val number: String,
    val prizeType: String,
    val amount: Long,
    val payoutMultiplier: Long
)
