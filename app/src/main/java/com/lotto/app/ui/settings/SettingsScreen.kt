package com.lotto.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import com.lotto.app.domain.rules.PrizeType
import com.lotto.app.domain.rules.NoAcceptNumberPolicy
import kotlinx.coroutines.launch
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
        SettingsSection.NO_ACCEPT -> NoAcceptNumberScreen(
            repository = settingsRepository,
            onBack = { section = SettingsSection.MENU }
        )
        SettingsSection.HALF_PAYOUT -> {
            val title = "ตั้งค่าหวยจ่ายครึ่งราคา"
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


@Composable
private fun NoAcceptNumberScreen(
    repository: SettingsRepository,
    onBack: () -> Unit
) {
    val settingRows by repository.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selectedType by remember { mutableStateOf(PrizeType.THREE_DIGIT_STRAIGHT) }
    var numberInput by remember { mutableStateOf("") }
    val key = NoAcceptNumberPolicy.settingKeys.getValue(selectedType.name)
    val blockedNumbers = NoAcceptNumberPolicy.parse(
        settingRows.firstOrNull { it.key == key }?.value.orEmpty(),
        selectedType
    )
    val digitCount = if (selectedType == PrizeType.THREE_DIGIT_STRAIGHT ||
        selectedType == PrizeType.THREE_DIGIT_TOD) 3 else 2

    Scaffold(
        topBar = { TopAppBar(title = { Text("ตั้งค่าหวยไม่รับ") }) },
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
            Text("เลือกประเภทหวย", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    PrizeType.THREE_DIGIT_STRAIGHT,
                    PrizeType.THREE_DIGIT_TOD,
                    PrizeType.TWO_DIGIT_TOP,
                    PrizeType.TWO_DIGIT_BOTTOM
                ).chunked(2).forEach { rowTypes ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowTypes.forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type; numberInput = "" },
                                label = { Text(NoAcceptNumberPolicy.labels.getValue(type.name)) }
                            )
                        }
                    }
                }
            }
            Text("กรอกเลขที่ไม่รับ", style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = numberInput,
                    onValueChange = { numberInput = it.filter(Char::isDigit).take(digitCount) },
                    modifier = Modifier.weight(1f),
                    label = { Text("$digitCount หลัก") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
                TextButton(
                    enabled = numberInput.length == digitCount && numberInput !in blockedNumbers,
                    onClick = {
                        val updated = (blockedNumbers + numberInput).sorted()
                        scope.launch {
                            repository.save(key, updated.joinToString(","))
                            numberInput = ""
                        }
                    }
                ) { Text("เพิ่ม") }
            }
            Text("รายการเลขไม่รับ (${blockedNumbers.size})", style = MaterialTheme.typography.titleMedium)
            if (blockedNumbers.isEmpty()) {
                Text("ยังไม่มีเลขที่ตั้งค่าไม่รับ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                blockedNumbers.forEach { number ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(number, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = {
                            scope.launch {
                                repository.save(key, blockedNumbers.filterNot { it == number }.joinToString(","))
                            }
                        }) { Text("ลบ") }
                    }
                }
            }
            Text(
                "เลขที่ตั้งค่าไว้จะไม่สามารถซื้อได้ในประเภทหวยนี้ ระบบจะแจ้งเตือนและล้างเลขเมื่อกด + เพิ่มรายการ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
