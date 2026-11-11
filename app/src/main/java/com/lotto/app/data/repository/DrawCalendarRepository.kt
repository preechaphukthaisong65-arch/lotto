package com.lotto.app.data.repository

import com.lotto.app.data.local.dao.DrawCalendarDao
import com.lotto.app.data.local.entity.DrawDateEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

class DrawCalendarRepository(
    private val dao: DrawCalendarDao
) {
    fun observeDrawDates(startDate: LocalDate, endDate: LocalDate): Flow<List<DrawDateEntity>> =
        dao.observeDrawDates(startDate.toString(), endDate.toString())

    fun observeAllDrawDates(): Flow<List<DrawDateEntity>> = dao.observeAllDrawDates()

    /** Only dates accepted by the verification bot may drive sales or the app calendar. */
    fun observeBotVerifiedDrawDates(): Flow<List<DrawDateEntity>> = dao.observeBotVerifiedDrawDates()

    companion object {
        val THAILAND_ZONE: ZoneId = ZoneId.of("Asia/Bangkok")
        fun todayInThailand(): LocalDate = LocalDate.now(THAILAND_ZONE)
        fun nowInThailand(): java.time.ZonedDateTime = java.time.ZonedDateTime.now(THAILAND_ZONE)
    }
}
