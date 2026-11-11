package com.lotto.app.domain.rules

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object DrawResultEntryPolicy {
    private val thailandZone = ZoneId.of("Asia/Bangkok")
    private val resultEntryTime = LocalTime.of(16, 0)

    fun canEnter(drawDate: String, epochMillis: Long): Boolean {
        val date = runCatching { LocalDate.parse(drawDate) }.getOrNull() ?: return false
        val localDateTime = Instant.ofEpochMilli(epochMillis)
            .atZone(thailandZone)
            .toLocalDateTime()
        return !localDateTime.isBefore(date.atTime(resultEntryTime))
    }
}
