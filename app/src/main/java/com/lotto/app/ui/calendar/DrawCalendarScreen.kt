package com.lotto.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lotto.app.data.repository.DrawCalendarRepository
import com.lotto.app.ui.components.buttons.BackButton
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DrawCalendarScreen(
    repository: DrawCalendarRepository,
    onBack: () -> Unit
) {
    val today = remember { DrawCalendarRepository.todayInThailand() }
    var visibleMonth by remember { mutableStateOf(YearMonth.from(today)) }
    val drawDates by repository.observeDrawDates(
        visibleMonth.atDay(1),
        visibleMonth.atEndOfMonth()
    ).collectAsState(initial = emptyList())
    val locale = remember { Locale("th", "TH") }
    val monthLabel = visibleMonth.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    val drawDateSet = remember(drawDates) { drawDates.map { it.drawDate }.toSet() }
    val cells = remember(visibleMonth) {
        val blanks = visibleMonth.atDay(1).dayOfWeek.value % 7
        List<LocalDate?>(blanks) { null } +
            (1..visibleMonth.lengthOfMonth()).map { visibleMonth.atDay(it) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("ปฏิทินหวย") }) },
        bottomBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                BackButton(onBack = onBack)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { visibleMonth = visibleMonth.minusMonths(1) }) { Text("‹ ก่อน") }
                Text(monthLabel, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }) { Text("ถัดไป ›") }
            }
            Text(
                "วันนี้: " + today.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale)) + " · เวลาไทย",
                style = MaterialTheme.typography.bodySmall
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("อา", "จ", "อ", "พ", "พฤ", "ศ", "ส").forEach { day ->
                    Text(
                        day,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxWidth().height(250.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(cells.size) { index ->
                    val date = cells[index]
                    if (date == null) {
                        Spacer(Modifier.height(38.dp))
                    } else {
                        val isDrawDate = date.toString() in drawDateSet
                        Card(modifier = Modifier.fillMaxWidth().height(38.dp)) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    date.dayOfMonth.toString(),
                                    color = if (isDrawDate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                if (isDrawDate) Text("งวด", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            Text("งวดในเดือนนี้", style = MaterialTheme.typography.titleMedium)
            if (drawDates.isEmpty()) {
                Text("บอทยังไม่มีวันงวดที่ผ่านการตรวจสอบในเดือนนี้", style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(drawDates, key = { it.drawDate }) { draw ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(draw.drawDate, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    when (draw.source) {
                                        "BOT_VERIFIED" -> "บอทตรวจสอบแล้ว"
                                        else -> "ไม่ใช่ข้อมูลที่ผ่านการยืนยัน"
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
            Text(
                "ปฏิทินนี้แสดงเฉพาะวันงวดที่บอทตรวจสอบและอนุมัติแล้วเท่านั้น",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Text("เขตเวลาหลัก: Asia/Bangkok (UTC+7)", style = MaterialTheme.typography.bodySmall)
        }
    }
}
