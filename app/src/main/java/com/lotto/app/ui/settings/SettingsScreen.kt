package com.lotto.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: DrawCalendarRepository,
    onBack: () -> Unit
) {
    val drawDates by repository.observeAllDrawDates().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<DrawDateEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var dateText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("ตั้งค่า") }) },
        bottomBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                BackButton(onBack = onBack)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("จัดการวันออกรางวัล", style = MaterialTheme.typography.titleLarge)
            Text(
                "เพิ่มหรือเปลี่ยนวันออกรางวัลได้ที่หน้านี้ ระบบจะเก็บประวัติวันเดิมทุกครั้งที่มีการเปลี่ยนแปลง",
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = {
                adding = true
                dateText = DrawCalendarRepository.todayInThailand().toString()
                errorText = null
            }) {
                Text("เพิ่มวันออกรางวัล")
            }
            if (drawDates.isEmpty()) {
                Text("ยังไม่มีวันออกรางวัลในฐานข้อมูล")
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(drawDates, key = { it.drawDate }) { draw ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                editing = draw
                                dateText = draw.drawDate
                                errorText = null
                            }
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
                }
            }
            Text("เขตเวลาหลัก: Asia/Bangkok (UTC+7)", style = MaterialTheme.typography.bodySmall)
        }
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("เพิ่มวันออกรางวัล") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it; errorText = null },
                        label = { Text("วันออกรางวัล (YYYY-MM-DD)") },
                        singleLine = true
                    )
                    errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val parsed = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
                    if (parsed == null) {
                        errorText = "กรุณากรอกวันที่รูปแบบ YYYY-MM-DD"
                    } else {
                        scope.launch {
                            runCatching { repository.addDrawDate(parsed) }
                                .onSuccess { adding = false }
                                .onFailure { errorText = it.message ?: "ไม่สามารถเพิ่มวันได้" }
                        }
                    }
                }) { Text("บันทึก") }
            },
            dismissButton = {
                TextButton(onClick = { adding = false }) { Text("ยกเลิก") }
            }
        )
    }

    editing?.let { draw ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("เปลี่ยนวันออกรางวัล") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("วันเดิม: " + draw.drawDate)
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it; errorText = null },
                        label = { Text("วันใหม่ (YYYY-MM-DD)") },
                        singleLine = true
                    )
                    errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val parsed = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
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
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("ยกเลิก") }
            }
        )
    }
}
