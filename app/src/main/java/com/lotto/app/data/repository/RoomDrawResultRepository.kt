package com.lotto.app.data.repository

import com.lotto.app.data.local.dao.DrawResultDao
import com.lotto.app.data.local.entity.DrawResultEntity
import com.lotto.app.data.local.model.WinningSaleSummary
import com.lotto.app.data.local.model.WinningBillSummary
import com.lotto.app.data.local.LottoDatabase
import kotlinx.coroutines.flow.Flow

class RoomDrawResultRepository(private val dao: DrawResultDao) : DrawResultRepository {
    override fun observeAllResults(): Flow<List<DrawResultEntity>> = dao.observeAllResults()
    override fun observeWinners(drawDate: String): Flow<List<WinningSaleSummary>> = dao.observeWinners(drawDate)

    override fun observeWinningBills(status: String): Flow<List<WinningBillSummary>> = dao.observeWinningBills(status)

    override suspend fun getTotalPrizeLiability(drawDate: String): Long = dao.getTotalPrizeLiability(drawDate)

    override suspend fun markWinningBillPaid(drawDate: String, saleId: Long, recipientName: String) {
        dao.markWinningBillPaid(drawDate, saleId, recipientName, System.currentTimeMillis())
    }

    override suspend fun cleanupExpiredPaidWinningBills(): Int {
        val retention = LottoDatabase.PAID_DOCUMENT_RETENTION_DAYS * LottoDatabase.MILLIS_PER_DAY
        return dao.deleteExpiredPaidWinningBills(System.currentTimeMillis() - retention)
    }

    override suspend fun saveResult(
        drawDate: String,
        threeDigit: String,
        twoDigitTop: String,
        twoDigitBottom: String
    ): Int = dao.saveResultAndFindWinners(
        DrawResultEntity(
            drawDate = drawDate,
            threeDigit = threeDigit,
            twoDigitTop = twoDigitTop,
            twoDigitBottom = twoDigitBottom,
            recordedAt = System.currentTimeMillis()
        )
    )
}
