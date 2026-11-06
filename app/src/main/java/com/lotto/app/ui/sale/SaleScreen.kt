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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lotto.app.ui.components.buttons.BackButton
import com.lotto.app.ui.components.buttons.ConfirmButton
import com.lotto.app.ui.sale.components.SaleNumberRow
import com.lotto.app.ui.sale.model.ThreeDigitEntry
import com.lotto.app.ui.sale.model.TwoDigitEntry

@Composable
fun SaleScreen(
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val threeDigitEntries = remember { mutableStateListOf(ThreeDigitEntry(id = 1L)) }
    val twoDigitEntries = remember { mutableStateListOf(TwoDigitEntry(id = 1L)) }
    var nextThreeId by remember { mutableStateOf(2L) }
    var nextTwoId by remember { mutableStateOf(2L) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var cashSelected by remember { mutableStateOf(true) }
    var creditSelected by remember { mutableStateOf(false) }
    var cashAmount by remember { mutableStateOf("0") }
    var creditAmount by remember { mutableStateOf("0") }

    val total = threeDigitEntries.sumOf {
        (it.straightAmount.toLongOrNull() ?: 0L) + (it.boxAmount.toLongOrNull() ?: 0L)
    } + twoDigitEntries.sumOf {
        (it.topAmount.toLongOrNull() ?: 0L) + (it.bottomAmount.toLongOrNull() ?: 0L)
    }
    val splitValid = !(cashSelected && creditSelected) ||
        ((cashAmount.toLongOrNull() ?: 0L) + (creditAmount.toLongOrNull() ?: 0L) == total)

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
                    ConfirmButton(
                        onConfirm = onSave,
                        label = "ยืนยัน / บันทึก",
                        confirmationTitle = "ยืนยันการบันทึกการขาย",
                        confirmationMessage = "ตรวจสอบรายการและยอดรวมก่อนยืนยัน",
                        enabled = total > 0L && splitValid
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
                }
            }
            item { Divider() }
            item { Text("3 ตัว", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            itemsIndexed(threeDigitEntries, key = { _, entry -> entry.id }) { index, entry ->
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
            itemsIndexed(twoDigitEntries, key = { _, entry -> entry.id }) { index, entry ->
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
                }
            }
        }
    }
}
