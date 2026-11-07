package com.lotto.app.data.repository

import com.lotto.app.data.local.entity.DrawResultEntity
import com.lotto.app.data.local.model.WinningSaleSummary
import kotlinx.coroutines.flow.Flow

interface DrawResultRepository {
    fun observeAllResults(): Flow<List<DrawResultEntity>>
    fun observeWinners(drawDate: String): Flow<List<WinningSaleSummary>>
    suspend fun saveResult(drawDate: String, threeDigit: String, twoDigitTop: String, twoDigitBottom: String): Int
}
