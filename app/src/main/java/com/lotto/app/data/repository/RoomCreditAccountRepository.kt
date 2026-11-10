package com.lotto.app.data.repository

import com.lotto.app.data.local.dao.SaleDao
import com.lotto.app.data.local.LottoDatabase
import com.lotto.app.data.local.entity.CreditDocumentEntity
import com.lotto.app.data.local.entity.CreditPaymentEntity

class RoomCreditAccountRepository(private val saleDao: SaleDao) : CreditAccountRepository {
    override suspend fun getOpenBills(): List<CreditDocumentEntity> = saleDao.getOpenCreditDocuments()
    override suspend fun getOutstandingCreditByCustomer(firstName: String, lastName: String): Long =
        saleDao.getOutstandingCreditByCustomer(firstName, lastName)
    override suspend fun getPaidBills(): List<CreditDocumentEntity> = saleDao.getPaidDocuments()
    override suspend fun getPayments(creditDocumentId: Long): List<CreditPaymentEntity> =
        saleDao.getPaymentsForCreditDocument(creditDocumentId)

    override suspend fun receivePayment(creditDocumentId: Long, amount: Long) {
        saleDao.recordCreditPayment(creditDocumentId, amount, System.currentTimeMillis())
    }

    override suspend fun cleanupExpiredPaidBills(): Int {
        val retention = LottoDatabase.PAID_DOCUMENT_RETENTION_DAYS * LottoDatabase.MILLIS_PER_DAY
        return saleDao.deleteExpiredPaidDocuments(System.currentTimeMillis() - retention)
    }
}
