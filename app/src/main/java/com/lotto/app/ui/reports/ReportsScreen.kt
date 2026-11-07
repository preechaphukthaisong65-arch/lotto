package com.lotto.app.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.lotto.app.data.local.entity.DrawProfitSummaryEntity
import com.lotto.app.data.repository.DrawResultRepository
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.ZonedDateTime
import java.util.Locale
import com.lotto.app.ui.components.buttons.BackButton

private val monthLabels = listOf(
    "ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.",
    "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค."
)

@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    onOpenTotalSales: () -> Unit,
    onOpenPerformance: () -> Unit
) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "รายงานยอด",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            ReportMenuItem(
                title = "ยอดขายทั้งหมด",
                icon = Icons.Outlined.ReceiptLong,
                onClick = onOpenTotalSales
            )
            ReportMenuItem(
                title = "ยอดผลประกอบการ",
                icon = Icons.Outlined.Assessment,
                onClick = onOpenPerformance
            )
        }
    }
}

@Composable
private fun ReportMenuItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null
            )
        }
    }
}

@Composable
fun ReportDetailScreen(
    title: String,
    onBack: () -> Unit,
    drawResultRepository: DrawResultRepository,
    now: ZonedDateTime,
    onOpenCurrentSales: () -> Unit = {},
    onOpenSalesHistory: () -> Unit = {}
) {
    val summaries by drawResultRepository.observeProfitSummaries().collectAsState(initial = emptyList())
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
        when {
            title == "ยอดขายทั้งหมด" -> TotalSalesLayout(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                onOpenCurrentSales = onOpenCurrentSales,
                onOpenSalesHistory = onOpenSalesHistory,
                summaries = summaries,
                now = now
            )
            title.startsWith("ยอดขายทั้งหมด (ย้อนหลัง 24 งวด)") -> SalesHistoryLayout(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                summaries = summaries
            )
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "หน้านี้ยังไม่มีรายงานที่เชื่อมกับข้อมูลจริง",
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TotalSalesLayout(
    modifier: Modifier = Modifier,
    onOpenCurrentSales: () -> Unit,
    onOpenSalesHistory: () -> Unit,
    summaries: List<DrawProfitSummaryEntity>,
    now: ZonedDateTime
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "ยอดขายทั้งหมด",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "กราฟยอดขายรายปี",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
            )
            YearlySalesChart(
                modifier = Modifier.fillMaxWidth().weight(1f),
                summaries = summaries,
                now = now
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "รายการยอดขาย",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            ReportMenuItem(
                title = "ยอดขายงวดปัจจุบัน",
                icon = Icons.Outlined.ReceiptLong,
                onClick = onOpenCurrentSales
            )
            ReportMenuItem(
                title = "ยอดขายทั้งหมด (ย้อนหลัง 24 งวด)",
                icon = Icons.Outlined.Assessment,
                onClick = onOpenSalesHistory
            )
        }
    }
}

@Composable
private fun SalesHistoryLayout(
    modifier: Modifier = Modifier,
    summaries: List<DrawProfitSummaryEntity>
) {
    val money = NumberFormat.getNumberInstance(Locale("th", "TH"))
    val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("th", "TH"))
    val rows = summaries.sortedByDescending { it.drawDate }.take(24)
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ยอดขายทั้งหมด (ย้อนหลัง 24 งวด)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ยอดขายรวม 24 งวดล่าสุด", fontWeight = FontWeight.SemiBold)
                Text("${money.format(rows.sumOf { it.salesTotal })} บาท", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("แสดงเฉพาะงวดที่บันทึกผลรางวัลและมีสรุปยอดไว้", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (rows.isEmpty()) {
            Text("ยังไม่มีข้อมูลยอดขายย้อนหลัง", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { it.drawDate }) { row ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val date = runCatching { LocalDate.parse(row.drawDate) }.getOrNull()
                            Text("งวดวันที่ ${date?.format(dateFormat) ?: row.drawDate}", fontWeight = FontWeight.SemiBold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("ยอดขาย")
                                Text("${money.format(row.salesTotal)} บาท", fontWeight = FontWeight.Bold)
                            }
                            if (row.dataQuality != "COMPLETE") {
                                Text("ข้อมูลเงินรางวัลย้อนหลังต้องตรวจสอบ", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearlySalesChart(
    modifier: Modifier = Modifier,
    summaries: List<DrawProfitSummaryEntity>,
    now: ZonedDateTime
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val barColor = MaterialTheme.colorScheme.primary
    val months = (11 downTo 0).map { offset -> now.toLocalDate().withDayOfMonth(1).minusMonths(offset.toLong()) }
    val totals = months.map { month ->
        summaries.filter { item ->
            val date = runCatching { LocalDate.parse(item.drawDate) }.getOrNull()
            date != null && date.year == month.year && date.monthValue == month.monthValue
        }.fold(0L) { total, item -> Math.addExact(total, item.salesTotal) }
    }
    val maxValue = totals.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val hasData = totals.any { it > 0L }

    Column(modifier = modifier.fillMaxSize()) {
        Text("ยอดขาย (บาท) · 12 เดือนล่าสุด", color = axisColor, fontSize = 11.sp)
        Canvas(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            val left = 2.dp.toPx()
            val right = size.width - 2.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - 4.dp.toPx()
            for (index in 0..4) {
                val y = top + (bottom - top) * index / 4f
                drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
            }
            drawLine(axisColor, Offset(left, top), Offset(left, bottom), strokeWidth = 1.5.dp.toPx())
            drawLine(axisColor, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.5.dp.toPx())
            val gap = 5.dp.toPx()
            val barWidth = ((right - left) - gap * (totals.size - 1)) / totals.size
            totals.forEachIndexed { index, value ->
                val barHeight = (value.toDouble() / maxValue.toDouble() * (bottom - top)).toFloat()
                val x = left + index * (barWidth + gap)
                drawRect(barColor, Offset(x, bottom - barHeight), Size(barWidth, barHeight))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            months.forEach { month ->
                Text(month.month.getDisplayName(TextStyle.SHORT, Locale("th", "TH")), fontSize = 8.sp, color = axisColor, maxLines = 1)
            }
        }
        if (!hasData) {
            Text("ยังไม่มีข้อมูลยอดขายสำหรับแสดงกราฟ", modifier = Modifier.fillMaxWidth().padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = axisColor)
        }
    }
}
