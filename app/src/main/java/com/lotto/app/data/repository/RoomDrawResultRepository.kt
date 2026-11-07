package com.lotto.app.data.repository

import com.lotto.app.data.local.dao.DrawResultDao
import com.lotto.app.data.local.entity.DrawResultEntity
import com.lotto.app.data.local.entity.DrawProfitSummaryEntity
import com.lotto.app.data.local.model.WinningSaleSummary
import com.lotto.app.data.local.model.WinningBillSummary
import com.lotto.app.data.local.LottoDatabase
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

class RoomDrawResultRepository(private val dao: DrawResultDao) : DrawResultRepository {
    override fun observeAllResults(): Flow<List<DrawResultEntity>> = dao.observeAllResults()
    override fun observeCorrections(drawDate: String) = dao.observeCorrections(drawDate)
    override fun observeProfitSummaries(): Flow<List<DrawProfitSummaryEntity>> = dao.observeProfitSummaries()
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

    override suspend fun cleanupExpiredProfitSummaries(): Int {
        val cutoffDate = LocalDate.now(ZoneId.of("Asia/Bangkok")).minusYears(1).toString()
        return dao.deleteExpiredProfitSummaries(cutoffDate)
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

    override suspend fun correctResult(
        drawDate: String,
        threeDigit: String,
        twoDigitTop: String,
        twoDigitBottom: String,
        reason: String
    ): Int = dao.correctResultAndRebuildWinners(
        drawDate = drawDate,
        threeDigit = threeDigit,
        twoDigitTop = twoDigitTop,
        twoDigitBottom = twoDigitBottom,
        reason = reason,
        correctedAt = System.currentTimeMillis()
    )
}
