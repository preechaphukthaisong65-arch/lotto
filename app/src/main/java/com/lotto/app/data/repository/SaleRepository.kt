package com.lotto.app.data.repository

import com.lotto.app.data.local.entity.SaleEntity
import com.lotto.app.data.local.entity.SaleVoidEventEntity
import com.lotto.app.data.local.model.SaleNumberSummary
import com.lotto.app.domain.model.SaleDraft

interface SaleRepository {
    suspend fun saveConfirmedSale(draft: SaleDraft, drawDate: String, fullLimitsByPrizeType: Map<String, Long> = emptyMap()): Long
    suspend fun getNumberStakeForDraw(drawDate: String, prizeType: String, number: String): Long
    suspend fun getAllSales(): List<SaleEntity>
    suspend fun getAllSaleVoidEvents(): List<SaleVoidEventEntity>
    suspend fun voidConfirmedSale(saleId: Long, reason: String, cashRefundConfirmed: Boolean): SaleVoidEventEntity
    suspend fun getNumberSalesSummary(startAt: Long, endAt: Long): List<SaleNumberSummary>
    suspend fun cleanupExpiredPaidDocuments(now: Long = System.currentTimeMillis()): Int
}
