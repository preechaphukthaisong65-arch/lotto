package com.lotto.app.data.repository

import com.lotto.app.data.local.entity.SaleEntity
import com.lotto.app.domain.model.SaleDraft

interface SaleRepository {
    suspend fun saveConfirmedSale(draft: SaleDraft): Long

    /**
     * Returns every sale document, including cash, credit, and split-payment sales.
     */
    suspend fun getAllSales(): List<SaleEntity>
}
