package com.lotto.app.ui.settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lotto.app.data.repository.SettingsRepository
import com.lotto.app.domain.rules.HalfPayoutNumberPolicy
import com.lotto.app.domain.rules.PrizeType
import com.lotto.app.ui.components.buttons.BackButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HalfPayoutNumberScreen(repository: SettingsRepository, onBack: () -> Unit) {
    val settings by repository.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selectedType by remember { mutableStateOf(PrizeType.THREE_DIGIT_STRAIGHT) }
    var numberInput by remember { mutableStateOf("") }
    val key = HalfPayoutNumberPolicy.settingKeys.getValue(selectedType.name)
    val digits = if (selectedType == PrizeType.THREE_DIGIT_STRAIGHT || selectedType == PrizeType.THREE_DIGIT_TOD) 3 else 2
    val numbers = HalfPayoutNumberPolicy.parse(settings.firstOrNull { it.key == key }?.value.orEmpty(), selectedType)
    val noAcceptKey = com.lotto.app.domain.rules.NoAcceptNumberPolicy.settingKeys.getValue(selectedType.name)
    val noAcceptNumbers = com.lotto.app.domain.rules.NoAcceptNumberPolicy.parse(
        settings.firstOrNull { it.key == noAcceptKey }?.value.orEmpty(), selectedType
    )
    val conflictingNumbers = numbers intersect noAcceptNumbers
    Scaffold(
        topBar = { TopAppBar(title = { Text("ตั้งค่าหวยจ่ายครึ่งราคา") }) },
        bottomBar = { Row(Modifier.fillMaxWidth().padding(12.dp)) { BackButton(onBack = onBack) } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("เลือกประเภทหวย", style = MaterialTheme.typography.titleMedium)
            listOf(PrizeType.THREE_DIGIT_STRAIGHT, PrizeType.THREE_DIGIT_TOD, PrizeType.TWO_DIGIT_TOP, PrizeType.TWO_DIGIT_BOTTOM).chunked(2).forEach { types ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEach { type ->
                        FilterChip(selected = selectedType == type, onClick = { selectedType = type; numberInput = "" }, label = { Text(HalfPayoutNumberPolicy.labels.getValue(type.name)) })
                    }
                }
            }
            Text("กรอกเลขที่ต้องการตั้งจ่ายครึ่งราคา")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = numberInput, onValueChange = { numberInput = it.filter { c -> c in '0'..'9' }.take(digits) }, modifier = Modifier.weight(1f), label = { Text("$digits หลัก") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                TextButton(enabled = numberInput.length == digits && numberInput !in numbers && numberInput !in noAcceptNumbers, onClick = {
                    val updated = (numbers + numberInput).sorted()
                    scope.launch { repository.save(key, updated.joinToString(",")); numberInput = "" }
                }) { Text("เพิ่ม") }
            }
            if (numberInput in noAcceptNumbers && numberInput.isNotBlank()) {
                Text("เลขนี้ถูกตั้งค่าไม่รับอยู่แล้ว จึงไม่สามารถตั้งจ่ายครึ่งได้", color = MaterialTheme.colorScheme.error)
            }
            if (conflictingNumbers.isNotEmpty()) {
                Text("พบเลขซ้ำกับรายการไม่รับ: ${conflictingNumbers.sorted().joinToString(", ")} · ระบบจะถือว่าไม่รับซื้อ", color = MaterialTheme.colorScheme.error)
            }
            Text("รายการเลขจ่ายครึ่ง (${numbers.size})", style = MaterialTheme.typography.titleMedium)
            if (numbers.isEmpty()) Text("ยังไม่มีเลขที่ตั้งจ่ายครึ่ง", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else numbers.forEach { number ->
                Row(Modifier.fillMaxWidth()) {
                    Text(number, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { scope.launch { repository.save(key, numbers.filterNot { it == number }.sorted().joinToString(",")) } }) { Text("ลบ") }
                }
            }
            Text("เมื่อขายเลขที่ตั้งไว้ ระบบจะแจ้งเตือนให้ยอมรับหรือเปลี่ยนเลข หากยอมรับและถูกรางวัลจะจ่ายครึ่งหนึ่งของอัตราปกติ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
