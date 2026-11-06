package com.lotto.app.data.repository

import com.lotto.app.data.local.dao.SaleDao
import com.lotto.app.data.local.entity.CreditDocumentEntity
import com.lotto.app.data.local.entity.CreditPaymentEntity

class RoomCreditAccountRepository(private val saleDao: SaleDao) : CreditAccountRepository {
    override suspend fun getOpenBills(): List<CreditDocumentEntity> = saleDao.getOpenCreditDocuments()
    override suspend fun getPaidBills(): List<CreditDocumentEntity> = saleDao.getPaidDocuments()
    override suspend fun getPayments(creditDocumentId: Long): List<CreditPaymentEntity> =
        saleDao.getPaymentsForCreditDocument(creditDocumentId)

    override suspend fun receivePayment(creditDocumentId: Long, amount: Long) {
        saleDao.recordCreditPayment(creditDocumentId, amount, System.currentTimeMillis())
    }
}
