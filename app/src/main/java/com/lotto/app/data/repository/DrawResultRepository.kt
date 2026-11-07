package com.lotto.app.data.repository

import com.lotto.app.data.local.entity.DrawResultEntity
import com.lotto.app.data.local.entity.DrawProfitSummaryEntity
import com.lotto.app.data.local.model.WinningSaleSummary
import com.lotto.app.data.local.model.WinningBillSummary
import kotlinx.coroutines.flow.Flow

interface DrawResultRepository {
    fun observeAllResults(): Flow<List<DrawResultEntity>>
    fun observeProfitSummaries(): Flow<List<DrawProfitSummaryEntity>>
    fun observeWinners(drawDate: String): Flow<List<WinningSaleSummary>>
    fun observeWinningBills(status: String): Flow<List<WinningBillSummary>>
    suspend fun getTotalPrizeLiability(drawDate: String): Long
    suspend fun markWinningBillPaid(drawDate: String, saleId: Long, recipientName: String)
    suspend fun cleanupExpiredPaidWinningBills(): Int
    suspend fun saveResult(drawDate: String, threeDigit: String, twoDigitTop: String, twoDigitBottom: String): Int
    suspend fun correctResult(drawDate: String, threeDigit: String, twoDigitTop: String, twoDigitBottom: String, reason: String): Int
}
