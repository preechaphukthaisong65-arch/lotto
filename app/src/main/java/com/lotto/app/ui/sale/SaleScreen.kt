package com.lotto.app.ui.sale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lotto.app.domain.model.SaleDraft
import com.lotto.app.domain.model.SaleDraftItem
import com.lotto.app.domain.rules.LottoRules
import com.lotto.app.domain.rules.PayoutMode
import com.lotto.app.domain.rules.PrizeType
import com.lotto.app.ui.components.buttons.BackButton
import com.lotto.app.ui.components.buttons.ConfirmButton
import com.lotto.app.ui.sale.components.SaleNumberRow
import com.lotto.app.ui.sale.model.ThreeDigitEntry
import com.lotto.app.ui.sale.model.TwoDigitEntry
import kotlinx.coroutines.launch

@Composable
fun SaleScreen(
    onBack: () -> Unit,
    onSave: suspend (SaleDraft) -> Long
) {
    val threeDigitEntries = remember { mutableStateListOf(ThreeDigitEntry(id = 1L)) }
    val twoDigitEntries = remember { mutableStateListOf(TwoDigitEntry(id = 1L)) }
    var nextThreeId by remember { mutableStateOf(2L) }
    var nextTwoId by remember { mutableStateOf(2L) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var cashSelected by remember { mutableStateOf(true) }
    var creditSelected by remember { mutableStateOf(false) }
    var cashAmount by remember { mutableStateOf("") }
    var creditAmount by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val total = threeDigitEntries.sumOf {
        (it.straightAmount.toLongOrNull() ?: 0L) + (it.boxAmount.toLongOrNull() ?: 0L)
    } + twoDigitEntries.sumOf {
        (it.topAmount.toLongOrNull() ?: 0L) + (it.bottomAmount.toLongOrNull() ?: 0L)
    }

    val cashValue = when {
        cashSelected && creditSelected -> cashAmount.toLongOrNull() ?: 0L
        cashSelected -> total
        else -> 0L
    }
    val creditValue = when {
        cashSelected && creditSelected -> creditAmount.toLongOrNull() ?: 0L
        creditSelected -> total
        else -> 0L
    }

    val paymentSelected = cashSelected || creditSelected
    val splitValid = cashValue + creditValue == total
    val customerValid = !creditSelected || (firstName.isNotBlank() && lastName.isNotBlank())

    val activeEntriesValid = threeDigitEntries.all { entry ->
        val straightAmount = entry.straightAmount.toLongOrNull() ?: 0L
        val todAmount = entry.boxAmount.toLongOrNull() ?: 0L
        (straightAmount == 0L && todAmount == 0L) ||
            (LottoRules.isValidThreeDigitNumber(
                entry.firstDigit + entry.secondDigit + entry.thirdDigit
            ) && straightAmount >= 0L && todAmount >= 0L)
    } && twoDigitEntries.all { entry ->
        val topAmount = entry.topAmount.toLongOrNull() ?: 0L
        val bottomAmount = entry.bottomAmount.toLongOrNull() ?: 0L
        (topAmount == 0L && bottomAmount == 0L) ||
            (LottoRules.isValidTwoDigitNumber(entry.firstDigit + entry.secondDigit) &&
                topAmount >= 0L && bottomAmount >= 0L)
    }

    val canSave = total > 0L && paymentSelected && splitValid &&
        customerValid && activeEntriesValid && !isSaving

    fun createDraft(): SaleDraft {
        val items = buildList {
            threeDigitEntries.forEach { entry ->
                val number = entry.firstDigit + entry.secondDigit + entry.thirdDigit
                val straight = entry.straightAmount.toLongOrNull() ?: 0L
                val tod = entry.boxAmount.toLongOrNull() ?: 0L
                if (straight > 0L) add(
                    SaleDraftItem(
                        number = number,
                        prizeType = PrizeType.THREE_DIGIT_STRAIGHT.name,
                        amount = straight,
                        payoutMultiplier = LottoRules.payoutMultiplier(
                            PrizeType.THREE_DIGIT_STRAIGHT,
                            PayoutMode.FULL
                        )
                    )
                )
                if (tod > 0L) add(
                    SaleDraftItem(
                        number = number,
                        prizeType = PrizeType.THREE_DIGIT_TOD.name,
                        amount = tod,
                        payoutMultiplier = LottoRules.payoutMultiplier(
                            PrizeType.THREE_DIGIT_TOD,
                            PayoutMode.FULL
                        )
                    )
                )
            }
            twoDigitEntries.forEach { entry ->
                val number = entry.firstDigit + entry.secondDigit
                val top = entry.topAmount.toLongOrNull() ?: 0L
                val bottom = entry.bottomAmount.toLongOrNull() ?: 0L
                if (top > 0L) add(
                    SaleDraftItem(
                        number = number,
                        prizeType = PrizeType.TWO_DIGIT_TOP.name,
                        amount = top,
                        payoutMultiplier = LottoRules.payoutMultiplier(
                            PrizeType.TWO_DIGIT_TOP,
                            PayoutMode.FULL
                        )
                    )
                )
                if (bottom > 0L) add(
                    SaleDraftItem(
                        number = number,
                        prizeType = PrizeType.TWO_DIGIT_BOTTOM.name,
                        amount = bottom,
                        payoutMultiplier = LottoRules.payoutMultiplier(
                            PrizeType.TWO_DIGIT_BOTTOM,
                            PayoutMode.FULL
                        )
                    )
                )
            }
        }
        return SaleDraft(
            customerFirstName = firstName.trim(),
            customerLastName = lastName.trim(),
            totalAmount = total,
            cashAmount = cashValue,
            creditAmount = creditValue,
            items = items
        )
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                BackButton(onBack = onBack)
                Text(
                    text = "ขายหวย",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                )
            }
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("ยอดรวม", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${total} บาท",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (saveError != null) {
                        Text(
                            text = saveError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    ConfirmButton(
                        onConfirm = {
                            if (canSave) {
                                coroutineScope.launch {
                                    isSaving = true
                                    saveError = null
                                    try {
                                        onSave(createDraft())
                                    } catch (error: Exception) {
                                        saveError = error.message ?: "บันทึกข้อมูลไม่สำเร็จ"
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            }
                        },
                        label = if (isSaving) "กำลังบันทึก..." else "ยืนยัน / บันทึก",
                        confirmationTitle = "ยืนยันการบันทึกการขาย",
                        confirmationMessage = "ตรวจสอบรายการและยอดรวมก่อนยืนยัน",
                        enabled = canSave
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ข้อมูลลูกค้า", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("ชื่อ") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("นามสกุล") },
                        singleLine = true
                    )
                    if (creditSelected && !customerValid) {
                        Text(
                            "การขายเงินเชื่อต้องกรอกชื่อและนามสกุลลูกค้า",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            item { Divider() }
            item { Text("3 ตัว", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            itemsIndexed(threeDigitEntries, key = { _, entry -> "three-${entry.id}" }) { index, entry ->
                SaleNumberRow(
                    digitCount = 3,
                    firstDigit = entry.firstDigit,
                    onFirstDigitChange = { threeDigitEntries[index] = entry.copy(firstDigit = it) },
                    secondDigit = entry.secondDigit,
                    onSecondDigitChange = { threeDigitEntries[index] = entry.copy(secondDigit = it) },
                    thirdDigit = entry.thirdDigit,
                    onThirdDigitChange = { threeDigitEntries[index] = entry.copy(thirdDigit = it) },
                    firstAmountLabel = "ตรง",
                    firstAmount = entry.straightAmount,
                    onFirstAmountChange = { threeDigitEntries[index] = entry.copy(straightAmount = it) },
                    secondAmountLabel = "โต๊ด",
                    secondAmount = entry.boxAmount,
                    onSecondAmountChange = { threeDigitEntries[index] = entry.copy(boxAmount = it) }
                )
            }
            item {
                TextButton(onClick = {
                    threeDigitEntries.add(ThreeDigitEntry(id = nextThreeId))
                    nextThreeId += 1L
                }) { Text("+ เพิ่มรายการ 3 ตัว") }
            }
            item { Divider() }
            item { Text("2 ตัว", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            itemsIndexed(twoDigitEntries, key = { _, entry -> "two-${entry.id}" }) { index, entry ->
                SaleNumberRow(
                    digitCount = 2,
                    firstDigit = entry.firstDigit,
                    onFirstDigitChange = { twoDigitEntries[index] = entry.copy(firstDigit = it) },
                    secondDigit = entry.secondDigit,
                    onSecondDigitChange = { twoDigitEntries[index] = entry.copy(secondDigit = it) },
                    firstAmountLabel = "บน",
                    firstAmount = entry.topAmount,
                    onFirstAmountChange = { twoDigitEntries[index] = entry.copy(topAmount = it) },
                    secondAmountLabel = "ล่าง",
                    secondAmount = entry.bottomAmount,
                    onSecondAmountChange = { twoDigitEntries[index] = entry.copy(bottomAmount = it) }
                )
            }
            item {
                TextButton(onClick = {
                    twoDigitEntries.add(TwoDigitEntry(id = nextTwoId))
                    nextTwoId += 1L
                }) { Text("+ เพิ่มรายการ 2 ตัว") }
            }
            item { Divider() }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("การชำระเงิน", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = cashSelected, onCheckedChange = { cashSelected = it })
                        Text("เงินสด")
                        Checkbox(checked = creditSelected, onCheckedChange = { creditSelected = it })
                        Text("เงินเชื่อ")
                    }
                    if (cashSelected && creditSelected) {
                        Text("แบ่งชำระ", style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = cashAmount,
                            onValueChange = { cashAmount = it.filter(Char::isDigit) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("จำนวนเงินสด (บาท)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = creditAmount,
                            onValueChange = { creditAmount = it.filter(Char::isDigit) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("จำนวนเงินเชื่อ (บาท)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        if (!splitValid) {
                            Text(
                                "ยอดเงินสดและเงินเชื่อต้องรวมกันเท่ากับยอดซื้อ",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (!activeEntriesValid) {
                        Text(
                            "กรุณากรอกเลขให้ครบทุกหลักในรายการที่มียอดซื้อ",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
