package com.lotto.app.ui.calendar

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lotto.app.data.local.entity.DrawDateEntity
import com.lotto.app.data.repository.DrawCalendarRepository
import com.lotto.app.ui.components.buttons.BackButton
import kotlinx.coroutines.launch
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
    val start = visibleMonth.atDay(1)
    val end = visibleMonth.atEndOfMonth()
    val drawDates by repository.observeDrawDates(start, end).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<DrawDateEntity?>(null) }
    var newDateText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    val thaiLocale = remember { Locale("th", "TH") }
    val monthLabel = remember(visibleMonth) {
        visibleMonth.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", thaiLocale))
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("ปฏิทินหวย") }) },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.Start
            ) { BackButton(onBack = onBack) }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { visibleMonth = visibleMonth.minusMonths(1) }) { Text("‹ เดือนก่อน") }
                Text(monthLabel, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }) { Text("เดือนถัดไป ›") }
            }
            Text(
                "วันที่วันนี้: ${today.format(DateTimeFormatter.ofPattern("d MMMM yyyy", thaiLocale))} (เวลาไทย)",
                style = MaterialTheme.typography.bodySmall
            )
            if (drawDates.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("ยังไม่มีข้อมูลงวดในเดือนนี้")
                        Text(
                            "เมื่อเพิ่มข้อมูลอ้างอิงจากกองสลากแล้ว วันออกรางวัลจะแสดงที่นี่",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(drawDates, key = { it.drawDate }) { draw ->
                        DrawDateCard(draw = draw, onEdit = {
                            editing = draw
                            newDateText = draw.drawDate
                            errorText = null
                        })
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "เขตเวลาหลักของแอป: Asia/Bangkok (UTC+7)",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    editing?.let { draw ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("เปลี่ยนวันออกรางวัล") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("วันเดิม: ${draw.drawDate}")
                    OutlinedTextField(
                        value = newDateText,
                        onValueChange = { newDateText = it; errorText = null },
                        label = { Text("วันใหม่ (YYYY-MM-DD)") },
                        singleLine = true
                    )
                    errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val parsed = runCatching { LocalDate.parse(newDateText.trim()) }.getOrNull()
                    if (parsed == null) {
                        errorText = "กรุณากรอกวันที่รูปแบบ YYYY-MM-DD"
                    } else {
                        scope.launch {
                            runCatching { repository.moveDrawDate(LocalDate.parse(draw.drawDate), parsed) }
                                .onSuccess { editing = null }
                                .onFailure { errorText = it.message ?: "ไม่สามารถเปลี่ยนวันได้" }
                        }
                    }
                }) { Text("บันทึกวันใหม่") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("ยกเลิก") } }
        )
    }
}

@Composable
private fun DrawDateCard(
    draw: DrawDateEntity,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(draw.drawDate, style = MaterialTheme.typography.titleMedium)
                Text(
                    when (draw.source) {
                        "GLO_10_YEAR_REFERENCE" -> "ข้อมูลอ้างอิงกองสลาก"
                        "USER_EDITED" -> "ผู้ใช้แก้ไขวันแล้ว"
                        else -> "วันที่ผู้ใช้เพิ่ม"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text("แก้ไข", color = MaterialTheme.colorScheme.primary)
        }
    }
}
