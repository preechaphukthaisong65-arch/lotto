package com.lotto.app.data.repository

import com.lotto.app.data.local.entity.SaleEntity
import com.lotto.app.data.local.model.SaleNumberSummary
import com.lotto.app.domain.model.SaleDraft

interface SaleRepository {
    suspend fun saveConfirmedSale(draft: SaleDraft): Long
    suspend fun getAllSales(): List<SaleEntity>
    suspend fun getNumberSalesSummary(startAt: Long, endAt: Long): List<SaleNumberSummary>
    suspend fun cleanupExpiredPaidDocuments(now: Long = System.currentTimeMillis()): Int
}
