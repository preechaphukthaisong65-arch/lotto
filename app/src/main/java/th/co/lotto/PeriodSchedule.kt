package th.co.lotto

import th.co.lotto.data.LottoDao
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Local draw-period schedule. It is evaluated while the app is active and
 * whenever a sale is about to be posted; missed transitions are caught up
 * on the next foreground check.
 */
object PeriodSchedule {
    fun closeAt(drawDateEpochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        scheduledTime(drawDateEpochMillis, LocalTime.of(14, 0), zoneId)

    fun openNextAt(drawDateEpochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        scheduledTime(drawDateEpochMillis, LocalTime.of(18, 0), zoneId)

    private fun scheduledTime(
        drawDateEpochMillis: Long,
        localTime: LocalTime,
        zoneId: ZoneId
    ): Long {
        val drawDate = Instant.ofEpochMilli(drawDateEpochMillis).atZone(zoneId).toLocalDate()
        return drawDate.atTime(localTime).atZone(zoneId).toInstant().toEpochMilli()
    }

    fun advance(dao: LottoDao, nowEpochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()) {
        val open = dao.getOpenPeriod()
        val drawDate = open?.drawDateEpochMillis
        if (open != null && drawDate != null && nowEpochMillis >= closeAt(drawDate, zoneId)) {
            dao.closePeriod(open.periodId, nowEpochMillis)
        }
        dao.openDuePeriod(nowEpochMillis)
    }
}
