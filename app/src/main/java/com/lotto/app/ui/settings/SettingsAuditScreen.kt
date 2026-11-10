package com.lotto.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lotto.app.data.repository.SettingsRepository
import com.lotto.app.ui.components.buttons.BackButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsAuditScreen(repository: SettingsRepository, onBack: () -> Unit) {
    val entries by repository.observeAuditLog().collectAsState(initial = emptyList())
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("th", "TH"))
    Scaffold(
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(12.dp)) { BackButton(onBack = onBack) }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("ประวัติการเปลี่ยนการตั้งค่า", style = MaterialTheme.typography.headlineSmall)
            Text("แสดง 500 รายการล่าสุด โดยไม่รวมข้อมูลเวลาระบบที่เปลี่ยนอัตโนมัติ")
            if (entries.isEmpty()) {
                Text("ยังไม่มีประวัติการเปลี่ยนแปลง")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries, key = { it.auditId }) { entry ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(entry.settingKey, style = MaterialTheme.typography.titleSmall)
                                Text("เดิม: ${entry.previousValue ?: "ไม่มีค่า"}")
                                Text("ใหม่: ${entry.newValue ?: "ลบค่า"}")
                                Text(
                                    formatter.format(Date(entry.changedAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
