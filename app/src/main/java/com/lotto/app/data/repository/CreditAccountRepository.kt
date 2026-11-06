package com.lotto.app.data.repository

import com.lotto.app.data.local.entity.CreditDocumentEntity
import com.lotto.app.data.local.entity.CreditPaymentEntity

interface CreditAccountRepository {
    suspend fun getOpenBills(): List<CreditDocumentEntity>
    suspend fun getPaidBills(): List<CreditDocumentEntity>
    suspend fun getPayments(creditDocumentId: Long): List<CreditPaymentEntity>
    suspend fun receivePayment(creditDocumentId: Long, amount: Long)
}
