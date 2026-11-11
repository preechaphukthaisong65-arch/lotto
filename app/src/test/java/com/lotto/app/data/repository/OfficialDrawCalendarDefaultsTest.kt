package com.lotto.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OfficialDrawCalendarDefaultsTest {
    @Test
    fun generatesRegularDatesAcrossPastAndFutureHorizon() {
        val today = LocalDate.of(2026, 11, 11)
        val dates = OfficialDrawCalendarDefaults.generate(today)
        val values = dates.map { it.drawDate }

        assertTrue(values.contains("2016-11-01"))
        assertTrue(values.contains("2016-11-16"))
        assertTrue(values.contains("2026-11-16"))
        assertTrue(values.contains("2028-11-01"))
        assertEquals(values.size, values.distinct().size)
        assertTrue(dates.all { it.source == OfficialDrawCalendarDefaults.SOURCE })
    }

    @Test
    fun generatesTwoOrderedDrawDatesPerMonth() {
        val dates = OfficialDrawCalendarDefaults.generate(LocalDate.of(2026, 11, 11))
        val november = dates.filter { it.drawDate.startsWith("2026-11-") }.map { it.drawDate }
        assertEquals(listOf("2026-11-01", "2026-11-16"), november)
    }
}
