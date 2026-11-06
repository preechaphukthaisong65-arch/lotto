package com.lotto.app.data.repository

import com.lotto.app.data.local.dao.SaleDao
import com.lotto.app.data.local.entity.SaleEntity
import com.lotto.app.data.local.entity.SaleItemEntity
import com.lotto.app.domain.model.SaleDraft

class RoomSaleRepository(
    private val saleDao: SaleDao
) : SaleRepository {

    override suspend fun saveConfirmedSale(draft: SaleDraft): Long {
        require(draft.totalAmount > 0L) { "ยอดขายต้องมากกว่า 0 บาท" }
        require(draft.cashAmount >= 0L && draft.creditAmount >= 0L) {
            "ยอดชำระต้องไม่ติดลบ"
        }
        require(draft.cashAmount + draft.creditAmount == draft.totalAmount) {
            "ยอดเงินสดและเงินเชื่อต้องรวมกันเท่ากับยอดขาย"
        }
        require(draft.items.isNotEmpty()) { "ไม่พบรายการเลขที่ซื้อ" }
        require(draft.items.all { it.amount > 0L }) { "จำนวนเงินแต่ละรายการต้องมากกว่า 0 บาท" }
        require(draft.items.sumOf { it.amount } == draft.totalAmount) {
            "ผลรวมรายการซื้อไม่ตรงกับยอดขาย"
        }

        val sale = SaleEntity(
            customerFirstName = draft.customerFirstName.trim(),
            customerLastName = draft.customerLastName.trim(),
            totalAmount = draft.totalAmount,
            cashAmount = draft.cashAmount,
            creditAmount = draft.creditAmount,
            createdAt = System.currentTimeMillis()
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
        return saleDao.insertSaleWithItems(sale, items)
    }

    override suspend fun getAllSales(): List<SaleEntity> = saleDao.getAllSales()
}
