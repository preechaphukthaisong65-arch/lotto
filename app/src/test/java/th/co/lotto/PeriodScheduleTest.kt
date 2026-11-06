package th.co.lotto

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class PeriodScheduleTest {
    private val zone = ZoneId.of("Asia/Bangkok")

    @Test
    fun defaultScheduleUsesBangkokTimeRegardlessOfDeviceTimezone() {
        val drawDate = LocalDate.of(2026, 11, 16)
            .atStartOfDay(ZoneId.of("Asia/Bangkok")).toInstant().toEpochMilli()
        assertEquals(
            LocalTime.of(14, 0),
            Instant.ofEpochMilli(PeriodSchedule.closeAt(drawDate)).atZone(zone).toLocalTime()
        )
        assertEquals(
            LocalTime.of(18, 0),
            Instant.ofEpochMilli(PeriodSchedule.openNextAt(drawDate)).atZone(zone).toLocalTime()
        )
    }

    @Test
    fun salesCloseAtFourteenAndNextPeriodOpensAtEighteenLocalTime() {
        val drawDate = LocalDate.of(2026, 11, 16)
            .atStartOfDay(zone).toInstant().toEpochMilli()

        val close = Instant.ofEpochMilli(PeriodSchedule.closeAt(drawDate, zone))
            .atZone(zone).toLocalTime()
        val open = Instant.ofEpochMilli(PeriodSchedule.openNextAt(drawDate, zone))
            .atZone(zone).toLocalTime()

        assertEquals(LocalTime.of(14, 0), close)
        assertEquals(LocalTime.of(18, 0), open)
    }
}
