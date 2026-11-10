package com.lotto.app.data.repository

import com.lotto.app.data.local.LottoDatabase
import com.lotto.app.data.local.dao.SaleDao
import com.lotto.app.data.local.entity.SaleEntity
import com.lotto.app.data.local.model.SaleNumberSummary
import com.lotto.app.data.local.entity.SaleItemEntity
import com.lotto.app.data.local.entity.SaleVoidEventEntity
import com.lotto.app.domain.model.SaleDraft

class RoomSaleRepository(private val saleDao: SaleDao) : SaleRepository {
    override suspend fun saveConfirmedSale(draft: SaleDraft, drawDate: String, fullLimitsByPrizeType: Map<String, Long>): Long {
        require(draft.totalAmount > 0L) { "ยอดขายต้องมากกว่า 0 บาท" }
        require(draft.cashAmount >= 0L && draft.creditAmount >= 0L) { "ยอดชำระต้องไม่ติดลบ" }
        require(Math.addExact(draft.cashAmount, draft.creditAmount) == draft.totalAmount) {
            "ยอดเงินสดและเงินเชื่อต้องรวมกันเท่ากับยอดขาย"
        }
        require(draft.items.isNotEmpty()) { "ไม่พบรายการเลขที่ซื้อ" }
        require(draft.items.all { it.amount > 0L }) { "จำนวนเงินแต่ละรายการต้องมากกว่า 0 บาท" }
        require(draft.items.fold(0L) { total, item -> Math.addExact(total, item.amount) } == draft.totalAmount) {
            "ผลรวมรายการซื้อไม่ตรงกับยอดขาย"
        }

        val createdAt = System.currentTimeMillis()
        val sale = SaleEntity(
            customerFirstName = draft.customerFirstName.trim(),
            customerLastName = draft.customerLastName.trim(),
            customerId = draft.customerId,
            customerPhone = draft.customerPhone?.trim()?.takeIf { it.isNotBlank() },
            legacyDebtReviewed = draft.legacyDebtReviewed,
            totalAmount = draft.totalAmount,
            cashAmount = draft.cashAmount,
            creditAmount = draft.creditAmount,
            createdAt = createdAt,
            drawDate = drawDate
        )
        val items = draft.items.map {
            SaleItemEntity(
                saleId = 0L,
                number = it.number,
                prizeType = it.prizeType,
                amount = it.amount,
                payoutMultiplier = it.payoutMultiplier
            )
        }
        return saleDao.insertSaleWithItems(sale, items, fullLimitsByPrizeType)
    }

    override suspend fun getNumberStakeForDraw(drawDate: String, prizeType: String, number: String): Long =
        saleDao.getNumberStakeForDraw(drawDate, prizeType, number)

    override suspend fun getAllSales(): List<SaleEntity> = saleDao.getAllSales()

    override suspend fun getAllSaleVoidEvents(): List<SaleVoidEventEntity> =
        saleDao.getAllSaleVoidEvents()

    override suspend fun voidConfirmedSale(
        saleId: Long,
        reason: String,
        cashRefundConfirmed: Boolean
    ): SaleVoidEventEntity = saleDao.voidConfirmedSale(
        saleId = saleId,
        reason = reason,
        voidedAt = System.currentTimeMillis(),
        cashRefundConfirmed = cashRefundConfirmed
    )
    override suspend fun getNumberSalesSummary(startAt: Long, endAt: Long): List<SaleNumberSummary> =
        saleDao.getNumberSalesSummary(startAt, endAt)

    override suspend fun cleanupExpiredPaidDocuments(now: Long): Int {
        val retentionMillis = LottoDatabase.PAID_DOCUMENT_RETENTION_DAYS * LottoDatabase.MILLIS_PER_DAY
        return saleDao.deleteExpiredPaidDocuments(now - retentionMillis)
    }
}
