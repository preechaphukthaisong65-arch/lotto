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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lotto.app.data.repository.SettingsRepository
import com.lotto.app.domain.rules.RiskLimitPolicy
import com.lotto.app.ui.components.buttons.BackButton
import com.lotto.app.ui.components.buttons.ConfirmButton
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RiskLimitsScreen(repository: SettingsRepository, onBack: () -> Unit) {
    val settings by repository.observeAll().collectAsState(initial = emptyList())
    val values = remember { mutableStateMapOf<String, String>() }
    val scope = rememberCoroutineScope()
    val keys = listOf(
        RiskLimitPolicy.MAX_PRIZE_LIABILITY_KEY,
        RiskLimitPolicy.MAX_CREDIT_PER_CUSTOMER_KEY
    )

    LaunchedEffect(settings) {
        keys.forEach { key ->
            values[key] = settings.firstOrNull { it.key == key }?.value.orEmpty()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("ตั้งค่าเพดานความเสี่ยง") }) },
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.fillMaxWidth()) { BackButton(onBack = onBack) }
                ConfirmButton(
                    label = "ยืนยัน / บันทึก",
                    confirmationTitle = "ยืนยันเพดานความเสี่ยง",
                    confirmationMessage = "ระบบจะปฏิเสธรายการขายที่ทำให้ยอดรางวัลสูงสุดหรือยอดเครดิตเกินวงเงินที่กำหนด",
                    enabled = keys.all { (values[it]?.toLongOrNull() ?: 0L) > 0L },
                    onConfirm = {
                        scope.launch {
                            val settingsToSave = keys.associateWith { key ->
                                values[key]?.toLongOrNull()
                                    ?.takeIf { it > 0L }
                                    ?.toString()
                                    ?: error("ค่าความเสี่ยงไม่ถูกต้อง")
                            }
                            repository.saveMany(settingsToSave)
                        }
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
                "กำหนดเพดานความเสี่ยงก่อนเปิดรับขาย ระบบจะปิดการขายไว้หากยังไม่ได้กำหนดค่าทั้งสองรายการ",
                color = MaterialTheme.colorScheme.error
            )
            OutlinedTextField(
                value = values[RiskLimitPolicy.MAX_PRIZE_LIABILITY_KEY].orEmpty(),
                onValueChange = { values[RiskLimitPolicy.MAX_PRIZE_LIABILITY_KEY] = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("เพดานเงินรางวัลสูงสุดต่องวด") },
                supportingText = { Text("ระบบคำนวณยอดจ่ายสูงสุดจากผลเลข 3 ตัวและ 2 ตัวทุกกรณี รวมอัตราจ่ายเต็ม/ครึ่ง") },
                suffix = { Text("บาท") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            OutlinedTextField(
                value = values[RiskLimitPolicy.MAX_CREDIT_PER_CUSTOMER_KEY].orEmpty(),
                onValueChange = { values[RiskLimitPolicy.MAX_CREDIT_PER_CUSTOMER_KEY] = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("เพดานยอดเงินเชื่อต่อลูกค้า") },
                supportingText = { Text("รวมยอดค้างของรหัสลูกค้าที่เลือกกับยอดเชื่อใหม่ ส่วนบิลเก่าที่ไม่มีรหัสลูกค้าจะให้ตรวจสอบแยกต่างหาก") },
                suffix = { Text("บาท") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            Text(
                "หมายเหตุ: การตรวจเครดิตอาศัยชื่อและนามสกุล จึงอาจรวมลูกค้าคนละคนที่มีชื่อซ้ำ ควรตรวจสอบตัวตนก่อนอนุมัติเงินเชื่อ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
