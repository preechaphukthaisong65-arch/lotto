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

    fun observeAllChanges(): Flow<List<com.lotto.app.data.local.entity.DrawDateChangeEntity>> =
        dao.observeAllChanges()

    /** Seeds missing regular draw dates without overwriting user edits or existing records. */
    suspend fun ensureBaselineCalendar(today: LocalDate = todayInThailand()) {
        dao.insertDefaultDrawDates(OfficialDrawCalendarDefaults.generate(today))
    }

    suspend fun addDrawDate(date: LocalDate, note: String? = null) {
        require(dao.getByDate(date.toString()) == null) { "มีงวดออกรางวัลในวันที่เลือกแล้ว" }
        val now = System.currentTimeMillis()
        dao.insertDrawDate(
            DrawDateEntity(
                drawDate = date.toString(),
                referenceDate = null,
                source = "USER_ADDED",
                note = note,
                updatedAt = now
            )
        )
    }

    suspend fun moveDrawDate(oldDate: LocalDate, newDate: LocalDate) {
        val changedAt = System.currentTimeMillis()
        dao.moveDrawDate(oldDate.toString(), newDate.toString(), changedAt)
    }

    companion object {
        val THAILAND_ZONE: ZoneId = ZoneId.of("Asia/Bangkok")
        fun todayInThailand(): LocalDate = LocalDate.now(THAILAND_ZONE)
        fun nowInThailand(): java.time.ZonedDateTime = java.time.ZonedDateTime.now(THAILAND_ZONE)
    }
}
