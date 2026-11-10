package com.lotto.app.domain.rules

import com.lotto.app.data.local.entity.DrawDateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PendingDrawResultPolicyTest {
    private val zone = ZoneId.of("Asia/Bangkok")
    private val dates = listOf("2026-10-01", "2026-10-16", "2026-11-01").map {
        DrawDateEntity(it, null, "TEST", null, 0L)
    }

    @Test
    fun returnsOldestMissedDrawBeforeLaterPendingDraws() {
        val now = ZonedDateTime.of(2026, 11, 10, 18, 0, 0, 0, zone)
        assertEquals("2026-10-01", PendingDrawResultPolicy.nextPendingDrawDate(dates, emptySet(), now))
    }

    @Test
    fun skipsCompletedDrawAndReturnsNextOldestPendingDraw() {
        val now = ZonedDateTime.of(2026, 11, 10, 18, 0, 0, 0, zone)
        assertEquals("2026-10-16", PendingDrawResultPolicy.nextPendingDrawDate(dates, setOf("2026-10-01"), now))
    }

    @Test
    fun doesNotPromptBeforeSixteenHundredForCurrentDrawWhenOlderDrawsAreComplete() {
        val now = ZonedDateTime.of(2026, 11, 1, 15, 59, 59, 0, zone)
        val completed = setOf("2026-10-01", "2026-10-16")
        assertNull(PendingDrawResultPolicy.nextPendingDrawDate(dates, completed, now))
    }

    @Test
    fun promptsAtSixteenHundredForCurrentDrawWhenOlderDrawsAreComplete() {
        val now = ZonedDateTime.of(2026, 11, 1, 16, 0, 0, 0, zone)
        val completed = setOf("2026-10-01", "2026-10-16")
        assertEquals("2026-11-01", PendingDrawResultPolicy.nextPendingDrawDate(dates, completed, now))
    }

    @Test
    fun returnsNullWhenAllEligibleDrawsAreCompleted() {
        val now = ZonedDateTime.of(2026, 11, 10, 18, 0, 0, 0, zone)
        assertNull(PendingDrawResultPolicy.nextPendingDrawDate(
            dates, dates.mapTo(mutableSetOf()) { it.drawDate }, now
        ))
    }
}
