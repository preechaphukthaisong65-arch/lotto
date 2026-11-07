package com.lotto.app.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lotto.app.data.local.entity.DrawDateEntity
import com.lotto.app.data.local.model.SaleNumberSummary
import com.lotto.app.data.repository.DrawCalendarRepository
import com.lotto.app.data.repository.DrawResultRepository
import com.lotto.app.data.repository.SaleRepository
import com.lotto.app.domain.rules.PrizeType
import com.lotto.app.ui.components.buttons.BackButton
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class CurrentDrawPeriod(
    val drawDate: LocalDate,
    val startAt: Long,
    val endAt: Long
)

private fun resolveCurrentDrawPeriod(
    drawDates: List<DrawDateEntity>,
    now: ZonedDateTime
): CurrentDrawPeriod? {
    val dates = drawDates.mapNotNull {
        runCatching { LocalDate.parse(it.drawDate) }.getOrNull()
    }.distinct().sorted()
    if (dates.size < 2) return null

    val openTime = LocalTime.of(8, 0)
    val closeTime = LocalTime.of(14, 30)
    val activeDraw = dates.zipWithNext().firstOrNull { (previous, draw) ->
        val opens = previous.plusDays(1).atTime(openTime).atZone(now.zone)
        val closes = draw.atTime(closeTime).atZone(now.zone)
        !now.isBefore(opens) && now.isBefore(closes)
    }?.second

    val latestClosedDraw = dates.lastOrNull { draw ->
        !now.isBefore(draw.atTime(closeTime).atZone(now.zone))
    }
    val targetDraw = activeDraw ?: latestClosedDraw ?: dates.firstOrNull() ?: return null
    val previousDraw = dates.lastOrNull { it.isBefore(targetDraw) } ?: return null

    val startAt = previousDraw.plusDays(1)
        .atTime(openTime)
        .atZone(DrawCalendarRepository.THAILAND_ZONE)
        .toInstant()
        .toEpochMilli()
    val endAt = targetDraw.atTime(closeTime)
        .atZone(DrawCalendarRepository.THAILAND_ZONE)
        .toInstant()
        .toEpochMilli()

    return CurrentDrawPeriod(targetDraw, startAt, endAt)
}

@Composable
fun CurrentSalesReportScreen(
    saleRepository: SaleRepository,
    drawResultRepository: DrawResultRepository,
    drawDates: List<DrawDateEntity>,
    now: ZonedDateTime,
    onBack: () -> Unit
) {
    val period = remember(drawDates, now) { resolveCurrentDrawPeriod(drawDates, now) }
    var summaries by remember { mutableStateOf<List<SaleNumberSummary>?>(null) }
    var totalPrizeLiability by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(period?.startAt, period?.endAt) {
        if (period == null) {
            summaries = emptyList()
            totalPrizeLiability = 0L
        } else {
            summaries = saleRepository.getNumberSalesSummary(period.startAt, period.endAt)
            totalPrizeLiability = drawResultRepository.getTotalPrizeLiability(period.drawDate.toString())
        }
    }

    val categories = listOf(
        PrizeType.THREE_DIGIT_STRAIGHT to "3 ตัวตรง",
        PrizeType.THREE_DIGIT_TOD to "3 ตัวโต๊ด",
        PrizeType.TWO_DIGIT_TOP to "2 ตัวบน",
        PrizeType.TWO_DIGIT_BOTTOM to "2 ตัวล่าง"
    )
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("th", "TH"))
    }

    Scaffold(
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BackButton(onBack = onBack)
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "ยอดขายงวดปัจจุบัน",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (period != null) {
                    Text(
                        text = "งวดวันที่ ${period.drawDate.format(dateFormatter)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (now.toInstant().toEpochMilli() >= period.endAt) {
                            "ปิดรับขายแล้ว · สรุปยอดประจำงวด"
                        } else {
                            "กำลังรับขาย · ยอดสะสมถึงปัจจุบัน"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (period == null) {
                item {
                    Text(
                        text = "ยังไม่มีข้อมูลวันออกรางวัลเพียงพอสำหรับสรุปยอดงวด",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (summaries == null || totalPrizeLiability == null) {
                item { Text("กำลังคำนวณยอดขาย...") }
            } else {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("ยอดขายรวมทั้งหมด", fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "${summaries.orEmpty().fold(BigInteger.ZERO) { total, item -> total.add(BigInteger.valueOf(item.totalAmount)) }} บาท",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("ยอดที่ต้องจ่ายรางวัลทั้งหมด", fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "${totalPrizeLiability ?: 0L} บาท",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                categories.forEach { (type, title) ->
                    val rows = summaries.orEmpty()
                        .filter { it.prizeType == type.name }
                        .sortedWith(compareByDescending<SaleNumberSummary> { it.totalAmount }.thenBy { it.number })

                    item(key = "header-${type.name}") {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (rows.isEmpty()) {
                        item(key = "empty-${type.name}") {
                            Text(
                                text = "ยังไม่มีรายการซื้อ",
                                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        items(rows, key = { "${type.name}-${it.number}" }) { row ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = row.number,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        text = "${row.totalAmount} บาท",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
