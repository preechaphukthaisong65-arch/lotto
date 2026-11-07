package com.lotto.app.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lotto.app.ui.components.buttons.BackButton
import com.lotto.app.ui.components.buttons.ConfirmButton
import kotlinx.coroutines.launch

@Composable
fun DrawResultScreen(
    drawDate: String,
    onBack: () -> Unit,
    onSave: suspend (String, String, String) -> Int
) {
    var threeDigit by remember(drawDate) { mutableStateOf("") }
    var twoDigitTop by remember(drawDate) { mutableStateOf("") }
    var twoDigitBottom by remember(drawDate) { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val valid = threeDigit.length == 3 && twoDigitTop.length == 2 && twoDigitBottom.length == 2

    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BackButton(onBack = onBack)
                ConfirmButton(
                    label = if (saving) "กำลังบันทึก..." else "ยืนยัน / บันทึกผล",
                    confirmationTitle = "ยืนยันผลรางวัล",
                    confirmationMessage = "ตรวจสอบตัวเลขให้ถูกต้องก่อนบันทึก ผลที่บันทึกแล้วจะแก้ไขไม่ได้",
                    enabled = valid && !saving,
                    onConfirm = {
                        scope.launch {
                            saving = true
                            error = null
                            try {
                                onSave(threeDigit, twoDigitTop, twoDigitBottom)
                            } catch (e: Exception) {
                                error = e.message ?: "บันทึกผลรางวัลไม่สำเร็จ"
                            } finally {
                                saving = false
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("บันทึกผลรางวัล", style = MaterialTheme.typography.headlineSmall)
            Text("งวดวันที่ $drawDate", style = MaterialTheme.typography.titleMedium)
            Text("กรอกผลรางวัลตามประกาศ โดยระบบจะค้นหาบิลที่ถูกรางวัลให้อัตโนมัติ")
            OutlinedTextField(
                value = threeDigit,
                onValueChange = { threeDigit = it.filter(Char::isDigit).take(3) },
                label = { Text("เลข 3 ตัว") },
                supportingText = { Text("ใช้ตรวจ 3 ตัวตรง และ 3 ตัวโต๊ด") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = twoDigitTop,
                onValueChange = { twoDigitTop = it.filter(Char::isDigit).take(2) },
                label = { Text("เลข 2 ตัวบน") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = twoDigitBottom,
                onValueChange = { twoDigitBottom = it.filter(Char::isDigit).take(2) },
                label = { Text("เลข 2 ตัวล่าง") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
        }
    }
}
