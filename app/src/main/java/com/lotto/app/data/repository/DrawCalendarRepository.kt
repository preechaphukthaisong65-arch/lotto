package com.lotto.app.data.repository

import com.lotto.app.data.local.dao.DrawCalendarDao
import com.lotto.app.data.local.entity.DrawDateEntity
import com.lotto.app.data.local.entity.DrawDateConfirmationEntity
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

    suspend fun getUnconfirmedDrawDates(today: LocalDate = todayInThailand()): List<String> =
        dao.getUnconfirmedDrawDates(today.toString(), today.plusDays(7).toString())

    suspend fun confirmDrawDate(drawDate: LocalDate) {
        require(dao.getByDate(drawDate.toString()) != null) { "ไม่พบวันที่ในปฏิทินกลาง" }
        dao.confirmDrawDate(DrawDateConfirmationEntity(drawDate.toString(), System.currentTimeMillis()))
    }

    suspend fun isDrawDateConfirmed(drawDate: LocalDate): Boolean =
        dao.getConfirmation(drawDate.toString()) != null

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
