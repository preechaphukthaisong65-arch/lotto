package com.lotto.app.data.repository

import com.lotto.app.data.local.entity.DrawDateEntity
import java.time.LocalDate
import java.time.YearMonth

/**
 * Offline baseline based on GLO's published regular schedule (1st and 16th).
 * GLO also announces exceptions and religious-holiday postponements separately;
 * those dates must be applied as explicit calendar edits, never guessed here.
 */
internal object OfficialDrawCalendarDefaults {
    const val SOURCE = "GLO_STANDARD_RULE"

    fun generate(today: LocalDate, pastYears: Long = 10, futureYears: Long = 2): List<DrawDateEntity> {
        require(pastYears >= 0 && futureYears >= 1)
        val start = today.minusYears(pastYears).withDayOfMonth(1)
        val end = today.plusYears(futureYears).withDayOfMonth(1)
        val firstMonth = YearMonth.from(start)
        val lastMonth = YearMonth.from(end)
        val result = mutableListOf<DrawDateEntity>()
        var month = firstMonth
        while (!month.isAfter(lastMonth)) {
            listOf(1, 16).forEach { day ->
                val date = month.atDay(day)
                result += DrawDateEntity(
                    drawDate = date.toString(),
                    referenceDate = date.toString(),
                    source = SOURCE,
                    note = "วันตามรอบปกติ 1/16 — โปรดตรวจประกาศ ก.สลากฯ หากมีการเลื่อนวัน",
                    updatedAt = 0L
                )
            }
            month = month.plusMonths(1)
        }
        return result
    }
}
