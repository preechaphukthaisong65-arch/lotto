package com.lotto.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lotto.app.domain.rules.FullNumberLimitPolicy
import com.lotto.app.data.repository.SettingsRepository
import com.lotto.app.ui.components.buttons.BackButton
import com.lotto.app.ui.components.buttons.ConfirmButton
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FullNumberLimitScreen(
    repository: SettingsRepository,
    onBack: () -> Unit
) {
    val settings by repository.observeAll().collectAsState(initial = emptyList())
    val values = remember { mutableStateMapOf<String, String>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(settings) {
        FullNumberLimitPolicy.settingKeys.forEach { (type, key) ->
            values[type] = settings.firstOrNull { it.key == key }?.value ?: "0"
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("ตั้งค่าหวยเต็ม") }) },
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.fillMaxWidth()) {
                    BackButton(onBack = onBack)
                }
                ConfirmButton(
                    onConfirm = {
                        scope.launch {
                            FullNumberLimitPolicy.settingKeys.forEach { (type, key) ->
                                repository.save(key, values[type].orEmpty().toLongOrNull()?.coerceAtLeast(0L)?.toString() ?: "0")
                            }
                        }
                    },
                    label = "ยืนยัน / บันทึก",
                    confirmationTitle = "ยืนยันการตั้งค่าหวยเต็ม",
                    confirmationMessage = "ระบบจะใช้วงเงินใหม่นี้กับการขายในงวดปัจจุบันและงวดถัดไป",
                    enabled = FullNumberLimitPolicy.settingKeys.keys.all { type ->
                        values[type].orEmpty().isNotBlank() && values[type].orEmpty().toLongOrNull() != null
                    }
                )
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "กำหนดวงเงินรับซื้อสูงสุดต่อตัว โดยเลขทุกตัวในประเภทเดียวกันใช้วงเงินเท่ากัน",
                style = MaterialTheme.typography.bodyMedium
            )
            FullNumberLimitPolicy.settingKeys.forEach { (type, _) ->
                OutlinedTextField(
                    value = values[type].orEmpty(),
                    onValueChange = { input -> values[type] = input.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(FullNumberLimitPolicy.labels[type].orEmpty()) },
                    suffix = { Text("บาท / เลข") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
            Text(
                "ตัวอย่าง: ตั้ง 3 ตัวตรง 100 บาท หมายถึงเลข 000–999 ทุกเลขมีเพดาน 100 บาทต่อเลขในงวดนั้น หากตั้ง 0 บาท จะถือว่าไม่จำกัดวงเงิน",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "ยอดสะสมและสถานะเต็มแยกตามงวด เมื่อเริ่มงวดใหม่ระบบเริ่มนับใหม่โดยอัตโนมัติ ส่วนค่าวงเงินที่ตั้งไว้ยังคงเดิม",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
