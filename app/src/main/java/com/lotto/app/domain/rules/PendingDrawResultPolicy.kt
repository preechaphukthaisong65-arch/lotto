package com.lotto.app.domain.rules

import com.lotto.app.data.local.entity.DrawDateEntity
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.ZoneId

/**
 * Selects the oldest draw whose result is due but has not been recorded.
 * A later draw must never hide an earlier pending draw.
 */
object PendingDrawResultPolicy {
    private val thailandZone = ZoneId.of("Asia/Bangkok")

    fun nextPendingDrawDate(
        drawDates: List<DrawDateEntity>,
        completedDrawDates: Set<String>,
        nowThailand: ZonedDateTime
    ): String? {
        val thailandNow = nowThailand.withZoneSameInstant(thailandZone)
        val today = thailandNow.toLocalDate()
        val eligibleCutoff = thailandNow.toLocalDateTime()

        return drawDates.asSequence()
            .mapNotNull { entity -> runCatching { LocalDate.parse(entity.drawDate) }.getOrNull() }
            .filter { date ->
                !date.isAfter(today) &&
                    !eligibleCutoff.isBefore(date.atTime(16, 0)) &&
                    date.toString() !in completedDrawDates
            }
            .minOrNull()
            ?.toString()
    }
}
