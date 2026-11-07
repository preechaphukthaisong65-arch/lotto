package com.lotto.app.ui.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lotto.app.data.repository.DrawResultRepository
import com.lotto.app.ui.components.buttons.BackButton
import java.text.NumberFormat
import java.math.BigInteger
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ProfitView(val label: String) { DRAW("รายงวด"), MONTH("รายเดือน"), YEAR("รายปี") }
private data class ProfitRow(val drawDate: LocalDate, val sales: Long, val prizes: Long, val needsReview: Boolean) {
    val net: BigInteger get() = BigInteger.valueOf(sales).subtract(BigInteger.valueOf(prizes))
}

@Composable
fun PerformanceReportScreen(
    drawResultRepository: DrawResultRepository,
    now: ZonedDateTime,
    onBack: () -> Unit
) {
    val summaries by drawResultRepository.observeProfitSummaries().collectAsState(initial = emptyList())
    var view by remember { mutableStateOf(ProfitView.DRAW) }
    var selectedMonth by remember { mutableIntStateOf(now.monthValue) }
    var selectedYear by remember { mutableIntStateOf(now.year) }
    var monthMenu by remember { mutableStateOf(false) }
    var yearMenu by remember { mutableStateOf(false) }

    val oneYearAgo = now.toLocalDate().minusYears(1)
    val rows = remember(summaries, oneYearAgo, now) {
        summaries.mapNotNull { item ->
            val date = runCatching { LocalDate.parse(item.drawDate) }.getOrNull() ?: return@mapNotNull null
            if (date.isBefore(oneYearAgo) || date.isAfter(now.toLocalDate())) return@mapNotNull null
            ProfitRow(date, item.salesTotal, item.prizeLiability, item.dataQuality != "COMPLETE")
        }.sortedByDescending { it.drawDate }
    }
    val availableYears = (rows.map { it.drawDate.year } + now.year).distinct().sortedDescending()
    val filtered = when (view) {
        ProfitView.DRAW -> rows
        ProfitView.MONTH -> rows.filter { it.drawDate.year == selectedYear && it.drawDate.monthValue == selectedMonth }
        ProfitView.YEAR -> rows.filter { it.drawDate.year == selectedYear }
    }
    val totalSales = filtered.fold(BigInteger.ZERO) { total, row -> total.add(BigInteger.valueOf(row.sales)) }
    val totalPrizes = filtered.fold(BigInteger.ZERO) { total, row -> total.add(BigInteger.valueOf(row.prizes)) }
    val totalNet = totalSales.subtract(totalPrizes)
    val money = remember { NumberFormat.getNumberInstance(Locale("th", "TH")) }
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("th", "TH")) }

    Scaffold(bottomBar = {
        Surface(shadowElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onBack = onBack)
                Spacer(Modifier.weight(1f))
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ยอดผลประกอบการ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("แสดงข้อมูลสะสมย้อนหลัง 1 ปี", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("คำนวณจากยอดขายหักเงินรางวัลที่ถูกรางวัลทั้งหมด แม้ยังไม่ได้จ่ายเงินจริง", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TabRow(selectedTabIndex = view.ordinal) {
                ProfitView.values().forEach { option ->
                    Tab(selected = view == option, onClick = { view = option }, text = { Text(option.label) })
                }
            }
            if (view != ProfitView.DRAW) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (view == ProfitView.MONTH) {
                        Column {
                            Text("เดือน", style = MaterialTheme.typography.labelSmall)
                            Text(java.time.Month.of(selectedMonth).getDisplayName(java.time.format.TextStyle.FULL, Locale("th", "TH")), modifier = Modifier.clickable { monthMenu = true }.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.primary)
                            DropdownMenu(expanded = monthMenu, onDismissRequest = { monthMenu = false }) {
                                (1..12).forEach { month ->
                                    DropdownMenuItem(text = { Text(java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, Locale("th", "TH"))) }, onClick = { selectedMonth = month; monthMenu = false })
                                }
                            }
                        }
                    }
                    Column {
                        Text("ปี พ.ศ.", style = MaterialTheme.typography.labelSmall)
                        Text((selectedYear + 543).toString(), modifier = Modifier.clickable { yearMenu = true }.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.primary)
                        DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false }) {
                            availableYears.forEach { year ->
                                DropdownMenuItem(text = { Text((year + 543).toString()) }, onClick = { selectedYear = year; yearMenu = false })
                            }
                        }
                    }
                }
            }
            if (filtered.any { it.needsReview }) {
                Text(
                    "มีข้อมูลงวดเก่าที่ต้องตรวจสอบ ยอดรางวัลบางงวดอาจไม่ครบจากการลบประวัติบิลที่หมดอายุแล้ว",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("สรุป" + view.label, fontWeight = FontWeight.SemiBold)
                    SummaryLine("ยอดขายรวม", totalSales, money)
                    SummaryLine("เงินรางวัลที่ต้องจ่าย", totalPrizes, money)
                    HorizontalDivider()
                    Text("กำไร / ขาดทุนสุทธิ", fontWeight = FontWeight.SemiBold)
                    Text((if (totalNet.signum() < 0) "ขาดทุน " else "กำไร ") + money.format(totalNet.abs()) + " บาท", style = MaterialTheme.typography.titleLarge, color = if (totalNet.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("จำนวนงวด: " + filtered.size, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(if (view == ProfitView.DRAW) "ผลประกอบการแยกตามงวด" else "รายการงวดในช่วงที่เลือก", fontWeight = FontWeight.SemiBold)
            if (filtered.isEmpty()) {
                Text("ยังไม่มีงวดที่มีผลรางวัลบันทึกไว้ในช่วงเวลานี้", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.drawDate.toString() }) { row ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("งวดวันที่ " + row.drawDate.format(dateFormat), fontWeight = FontWeight.SemiBold)
                                if (row.needsReview) Text("ข้อมูลย้อนหลัง · ต้องตรวจสอบ", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                SummaryLine("ยอดขาย", row.sales, money)
                                SummaryLine("เงินรางวัล", row.prizes, money)
                                Text((if (row.net.signum() < 0) "ขาดทุน " else "กำไร ") + money.format(row.net.abs()) + " บาท", color = if (row.net.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, amount: BigInteger, money: NumberFormat) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(money.format(amount) + " บาท", fontWeight = FontWeight.Medium)
    }
}
