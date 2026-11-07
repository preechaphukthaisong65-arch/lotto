package com.lotto.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "expired_sale_retention", indices = [Index("eligibleAt")])
data class ExpiredSaleRetentionEntity(
    @PrimaryKey val saleId: Long,
    val eligibleAt: Long
)
