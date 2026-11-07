package com.lotto.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lotto.app.data.repository.DrawCalendarRepository
import com.lotto.app.data.repository.SettingsRepository
import com.lotto.app.ui.components.buttons.BackButton

private enum class SettingsSection {
    MENU, FULL_LIMIT, NO_ACCEPT, HALF_PAYOUT, DRAW_CALENDAR
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: DrawCalendarRepository,
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    var section by remember { mutableStateOf(SettingsSection.MENU) }

    when (section) {
        SettingsSection.FULL_LIMIT -> FullNumberLimitScreen(
            repository = settingsRepository,
            onBack = { section = SettingsSection.MENU }
        )
        SettingsSection.DRAW_CALENDAR -> DrawCalendarSettingsScreen(
            repository = repository,
            onBack = { section = SettingsSection.MENU }
        )
        SettingsSection.NO_ACCEPT, SettingsSection.HALF_PAYOUT -> {
            val title = if (section == SettingsSection.NO_ACCEPT) "ตั้งค่าหวยไม่รับ" else "ตั้งค่าหวยจ่ายครึ่งราคา"
            Scaffold(
                topBar = { TopAppBar(title = { Text(title) }) },
                bottomBar = {
                    Row(Modifier.fillMaxWidth().padding(12.dp)) {
                        BackButton(onBack = { section = SettingsSection.MENU })
                    }
                }
            ) { padding ->
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("เมนูนี้เตรียมไว้แล้ว", style = MaterialTheme.typography.titleMedium)
                    Text("รายละเอียดกติกาของเมนูนี้ยังไม่ได้กำหนด จึงยังไม่มีการเปลี่ยนแปลงระบบขาย")
                }
            }
        }
        SettingsSection.MENU -> {
            Scaffold(
                topBar = { TopAppBar(title = { Text("ตั้งค่า") }) },
                bottomBar = {
                    Row(Modifier.fillMaxWidth().padding(12.dp)) {
                        BackButton(onBack = onBack)
                    }
                }
            ) { padding ->
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsMenuCard("ตั้งค่าหวยเต็ม", "กำหนดวงเงินรับซื้อสูงสุดต่อตัว แยกตามประเภทหวย") {
                        section = SettingsSection.FULL_LIMIT
                    }
                    SettingsMenuCard("ตั้งค่าหวยไม่รับ", "กำหนดเลขที่ไม่เปิดรับซื้อ") {
                        section = SettingsSection.NO_ACCEPT
                    }
                    SettingsMenuCard("ตั้งค่าหวยจ่ายครึ่งราคา", "กำหนดเลขที่จ่ายรางวัลครึ่งอัตรา") {
                        section = SettingsSection.HALF_PAYOUT
                    }
                    SettingsMenuCard("จัดการวันออกรางวัล", "เพิ่มหรือแก้ไขวันออกรางวัลและประวัติการเปลี่ยนแปลง") {
                        section = SettingsSection.DRAW_CALENDAR
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsMenuCard(title: String, description: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
