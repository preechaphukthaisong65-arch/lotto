package com.lotto.app.data.repository

import com.lotto.app.data.local.dao.DrawResultDao
import com.lotto.app.data.local.entity.DrawResultEntity
import com.lotto.app.data.local.model.WinningSaleSummary
import kotlinx.coroutines.flow.Flow

class RoomDrawResultRepository(private val dao: DrawResultDao) : DrawResultRepository {
    override fun observeAllResults(): Flow<List<DrawResultEntity>> = dao.observeAllResults()
    override fun observeWinners(drawDate: String): Flow<List<WinningSaleSummary>> = dao.observeWinners(drawDate)

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
