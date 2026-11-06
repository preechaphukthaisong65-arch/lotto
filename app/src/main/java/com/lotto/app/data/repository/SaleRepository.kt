package com.lotto.app.data.repository

import com.lotto.app.domain.model.SaleDraft

interface SaleRepository {
    suspend fun saveConfirmedSale(draft: SaleDraft): Long
}
