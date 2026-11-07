package com.lotto.app.domain.rules

import com.lotto.app.data.local.entity.DrawDateEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

data class SaleWindowStatus(
    val isOpen: Boolean,
    val nextOpenAt: ZonedDateTime?,
    val closesAt: ZonedDateTime?,
    val message: String
)

object SaleWindowPolicy {
    private val openTime = LocalTime.of(8, 0)
    private val closeTime = LocalTime.of(14, 30)

    fun activeDrawDate(drawDates: List<DrawDateEntity>, now: ZonedDateTime): String? {
        val dates = drawDates.mapNotNull { runCatching { LocalDate.parse(it.drawDate) }.getOrNull() }
            .distinct().sorted()
        val zone = now.zone
        return dates.zipWithNext { previous, draw ->
            val opens = previous.plusDays(1).atTime(openTime).atZone(zone)
            val closes = draw.atTime(closeTime).atZone(zone)
            if (!now.isBefore(opens) && now.isBefore(closes)) draw.toString() else null
        }.firstOrNull { it != null }
    }

    fun evaluate(
        drawDates: List<DrawDateEntity>,
        now: ZonedDateTime
    ): SaleWindowStatus {
        val dates = drawDates.mapNotNull {
            runCatching { LocalDate.parse(it.drawDate) }.getOrNull()
        }.distinct().sorted()

        if (dates.size < 2) {
            return SaleWindowStatus(
                isOpen = false,
                nextOpenAt = null,
                closesAt = null,
                message = "ยังไม่มีข้อมูลวันออกรางวัลเพียงพอสำหรับกำหนดรอบรับขาย"
            )
        }

        val zone = now.zone
        val windows = dates.zipWithNext { previous, draw ->
            val opens = previous.plusDays(1).atTime(openTime).atZone(zone)
            val closes = draw.atTime(closeTime).atZone(zone)
            opens to closes
        }

        val active = windows.firstOrNull { (opens, closes) ->
            !now.isBefore(opens) && now.isBefore(closes)
        }
        if (active != null) {
            return SaleWindowStatus(
                isOpen = true,
                nextOpenAt = active.first,
                closesAt = active.second,
                message = "เปิดรับขาย ถึงเวลา 14:30 น."
            )
        }

        val next = windows.firstOrNull { (opens, closes) -> now.isBefore(opens) }
        return if (next != null) {
            SaleWindowStatus(
                isOpen = false,
                nextOpenAt = next.first,
                closesAt = next.second,
                message = "ยังไม่เปิดรับขาย เปิดรับเวลา 08:00 น. วันที่ ${next.first.toLocalDate()}"
            )
        } else {
            SaleWindowStatus(
                isOpen = false,
                nextOpenAt = null,
                closesAt = null,
                message = "ปิดรับขายแล้ว"
            )
        }
    }
}
