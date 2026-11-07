package com.lotto.app.domain.rules

import com.lotto.app.data.local.entity.DrawDateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SaleWindowPolicyTest {
    private val zone = ZoneId.of("Asia/Bangkok")
    private val dates = listOf("2026-11-01", "2026-11-16").map {
        DrawDateEntity(it, null, "TEST", null, 0L)
    }

    @Test
    fun opensAtEightOnDayAfterPreviousDraw() {
        val before = ZonedDateTime.of(2026, 11, 2, 7, 59, 59, 0, zone)
        val opens = ZonedDateTime.of(2026, 11, 2, 8, 0, 0, 0, zone)
        assertEquals(null, SaleWindowPolicy.activeDrawDate(dates, before))
        assertEquals("2026-11-16", SaleWindowPolicy.activeDrawDate(dates, opens))
    }

    @Test
    fun closesAtFourteenThirtyOnDrawDate() {
        val lastOpenSecond = ZonedDateTime.of(2026, 11, 16, 14, 29, 59, 0, zone)
        val closed = ZonedDateTime.of(2026, 11, 16, 14, 30, 0, 0, zone)
        assertEquals("2026-11-16", SaleWindowPolicy.activeDrawDate(dates, lastOpenSecond))
        assertEquals(null, SaleWindowPolicy.activeDrawDate(dates, closed))
    }

    @Test
    fun evaluateReportsOpenAndClosedWindows() {
        val open = SaleWindowPolicy.evaluate(
            dates,
            ZonedDateTime.of(2026, 11, 7, 12, 0, 0, 0, zone)
        )
        val closed = SaleWindowPolicy.evaluate(
            dates,
            ZonedDateTime.of(2026, 11, 16, 14, 30, 0, 0, zone)
        )
        assertTrue(open.isOpen)
        assertFalse(closed.isOpen)
    }
}
