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
import com.lotto.app.data.local.entity.DrawResultEntity
import com.lotto.app.ui.components.buttons.BackButton
import com.lotto.app.ui.components.buttons.ConfirmButton
import kotlinx.coroutines.launch

@Composable
fun DrawResultScreen(
    drawDate: String,
    onBack: () -> Unit,
    onSave: suspend (String, String, String) -> Int,
    existingResult: DrawResultEntity? = null
) {
    var threeDigit by remember(drawDate, existingResult) { mutableStateOf(existingResult?.threeDigit.orEmpty()) }
    var twoDigitBottom by remember(drawDate, existingResult) { mutableStateOf(existingResult?.twoDigitBottom.orEmpty()) }
    var correctionReason by remember(drawDate, existingResult) { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val isCorrection = existingResult != null
    val valid = threeDigit.length == 3 && twoDigitBottom.length == 2 &&
        (!isCorrection || correctionReason.isNotBlank())

    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BackButton(onBack = onBack)
                ConfirmButton(
                    label = if (saving) "กำลังบันทึก..." else "ยืนยัน / บันทึกผล",
                    confirmationTitle = if (isCorrection) "ยืนยันการแก้ไขผลรางวัล" else "ยืนยันผลรางวัล",
                    confirmationMessage = if (isCorrection) "ตรวจสอบผลใหม่และเหตุผลการแก้ไขให้ถูกต้อง ระบบจะบันทึกประวัติการแก้ไข และจะไม่อนุญาตหากงวดนี้จ่ายรางวัลไปแล้ว" else "ตรวจสอบตัวเลขให้ถูกต้องก่อนบันทึกผลรางวัล",
                    enabled = valid && !saving,
                    onConfirm = {
                        scope.launch {
                            saving = true
                            error = null
                            try {
                                onSave(threeDigit, twoDigitBottom, correctionReason)
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
            Text(if (isCorrection) "แก้ไขผลรางวัล" else "บันทึกผลรางวัล", style = MaterialTheme.typography.headlineSmall)
            Text("งวดวันที่ $drawDate", style = MaterialTheme.typography.titleMedium)
            Text("กรอกผลรางวัลตามประกาศ โดยระบบจะค้นหาบิลที่ถูกรางวัลให้อัตโนมัติ")
            OutlinedTextField(
                value = threeDigit,
                onValueChange = { threeDigit = it.filter { digit -> digit in '0'..'9' }.take(3) },
                label = { Text("เลข 3 ตัว") },
                supportingText = { Text("ใช้ตรวจ 3 ตัวตรง และ 3 ตัวโต๊ด") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "เลข 2 ตัวบน (คำนวณจาก 2 หลักท้าย): ${if (threeDigit.length == 3) threeDigit.takeLast(2) else "--"}",
                style = MaterialTheme.typography.bodyLarge
            )
            OutlinedTextField(
                value = twoDigitBottom,
                onValueChange = { twoDigitBottom = it.filter { digit -> digit in '0'..'9' }.take(2) },
                label = { Text("เลข 2 ตัวล่าง") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (isCorrection) {
                OutlinedTextField(
                    value = correctionReason,
                    onValueChange = { correctionReason = it },
                    label = { Text("เหตุผลที่แก้ไขผลรางวัล *") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Text("ระบบจะบันทึกผลเดิม ผลใหม่ และเหตุผลไว้ในประวัติการแก้ไข หากมีการจ่ายรางวัลแล้วจะไม่สามารถแก้ไขได้", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
        }
    }
}
