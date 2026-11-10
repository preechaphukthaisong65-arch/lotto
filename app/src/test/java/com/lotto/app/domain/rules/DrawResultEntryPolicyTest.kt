package com.lotto.app.domain.rules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DrawResultEntryPolicyTest {
    private val zone = ZoneId.of("Asia/Bangkok")

    private fun epoch(hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 11, 10, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun doesNotAllowEntryBeforeSixteenHundredOnDrawDate() {
        assertFalse(DrawResultEntryPolicy.canEnter("2026-11-10", epoch(15, 59)))
    }

    @Test
    fun allowsEntryAtSixteenHundredOnDrawDate() {
        assertTrue(DrawResultEntryPolicy.canEnter("2026-11-10", epoch(16, 0)))
    }

    @Test
    fun allowsLateEntryForAnEarlierDraw() {
        assertTrue(DrawResultEntryPolicy.canEnter("2026-11-09", epoch(16, 0)))
    }

    @Test
    fun rejectsMalformedDrawDate() {
        assertFalse(DrawResultEntryPolicy.canEnter("not-a-date", epoch(18, 0)))
    }
}
