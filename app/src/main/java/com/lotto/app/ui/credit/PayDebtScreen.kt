package com.lotto.app.ui.credit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lotto.app.data.local.entity.CreditDocumentEntity
import com.lotto.app.data.repository.CreditAccountRepository
import com.lotto.app.ui.components.buttons.BackButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun PayDebtScreen(repository: CreditAccountRepository, onBack: () -> Unit) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf(false) }
    var openBills by remember { mutableStateOf<List<CreditDocumentEntity>>(emptyList()) }
    var paymentTotals by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    val paymentInputs = remember { mutableStateMapOf<Long, String>() }
    var refreshKey by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingPayment by remember { mutableStateOf<Triple<CreditDocumentEntity, Long, Long>?>(null) }
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("th", "TH")) }

    LaunchedEffect(refreshKey) {
        repository.cleanupExpiredPaidBills()
        openBills = repository.getOpenBills()
        paymentTotals = openBills.associate { bill ->
            bill.creditDocumentId to repository.getPayments(bill.creditDocumentId).sumOf { it.amount }
        }
    }

    val normalizedFirst = firstName.trim()
    val normalizedLast = lastName.trim()
    val matchingBills = if (!searched || normalizedFirst.isBlank() || normalizedLast.isBlank()) {
        emptyList()
    } else {
        openBills.filter { bill ->
            bill.customerFirstName.trim().equals(normalizedFirst, ignoreCase = true) &&
                bill.customerLastName.trim().equals(normalizedLast, ignoreCase = true) &&
                (bill.creditAmount - (paymentTotals[bill.creditDocumentId] ?: 0L)) > 0L
        }.sortedWith(compareBy<CreditDocumentEntity> { it.createdAt }.thenBy { it.creditDocumentId })
    }

    Scaffold(
        bottomBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                BackButton(onBack = onBack)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("ชำระหนี้", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("ค้นหาบิลค้างชำระด้วยชื่อและนามสกุลให้ตรงกับข้อมูลตอนขายเชื่อ")
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it; searched = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("ชื่อ") },
                singleLine = true
            )
            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it; searched = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("นามสกุล") },
                singleLine = true
            )
            Button(
                onClick = {
                    if (normalizedFirst.isBlank() || normalizedLast.isBlank()) {
                        errorMessage = "กรุณากรอกทั้งชื่อและนามสกุล"
                        searched = false
                    } else {
                        errorMessage = null
                        searched = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("ค้นหาบิลค้างชำระ") }
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (searched) {
                if (matchingBills.isEmpty()) {
                    Text("ไม่พบบิลค้างชำระของ $normalizedFirst $normalizedLast")
                } else {
                    Text("พบบิลค้างชำระ ${matchingBills.size} บิล")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(matchingBills, key = { it.creditDocumentId }) { bill ->
                            val alreadyPaid = paymentTotals[bill.creditDocumentId] ?: 0L
                            val outstanding = (bill.creditAmount - alreadyPaid).coerceAtLeast(0L)
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Text("บิล #${bill.creditDocumentId}", fontWeight = FontWeight.Bold)
                                    Text("วันที่ขาย: ${dateFormat.format(Date(bill.createdAt))}")
                                    Text("ยอดเงินเชื่อเริ่มต้น: ${bill.creditAmount} บาท")
                                    Text("รับชำระแล้ว: $alreadyPaid บาท")
                                    Text("ยอดค้างชำระ: $outstanding บาท", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                    OutlinedTextField(
                                        value = paymentInputs[bill.creditDocumentId].orEmpty(),
                                        onValueChange = { paymentInputs[bill.creditDocumentId] = it.filter(Char::isDigit) },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("ยอดที่รับชำระครั้งนี้ (บาท)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true
                                    )
                                    Button(
                                        onClick = {
                                            val amount = paymentInputs[bill.creditDocumentId]?.toLongOrNull()
                                            when {
                                                amount == null || amount <= 0L -> errorMessage = "กรุณากรอกยอดรับชำระให้ถูกต้อง"
                                                amount > outstanding -> errorMessage = "ยอดรับชำระเกินยอดค้าง $outstanding บาท"
                                                else -> {
                                                    errorMessage = null
                                                    pendingPayment = Triple(bill, amount, outstanding)
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("บันทึกรับชำระ") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingPayment?.let { (bill, amount, outstanding) ->
        AlertDialog(
            onDismissRequest = { pendingPayment = null },
            title = { Text("ยืนยันการชำระหนี้") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ลูกค้า: ${bill.customerFirstName} ${bill.customerLastName}")
                    Text("บิลเลขที่: #${bill.creditDocumentId}")
                    Text("ยอดค้างก่อนชำระ: $outstanding บาท")
                    Text("ยอดที่จะรับชำระ: $amount บาท", fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            repository.receivePayment(bill.creditDocumentId, amount)
                            paymentInputs[bill.creditDocumentId] = ""
                            errorMessage = null
                            pendingPayment = null
                            refreshKey += 1
                        } catch (error: Exception) {
                            errorMessage = error.message ?: "บันทึกรับชำระไม่สำเร็จ"
                            pendingPayment = null
                        }
                    }
                }) { Text("ยืนยัน") }
            },
            dismissButton = {
                TextButton(onClick = { pendingPayment = null }) { Text("กลับไปตรวจสอบ") }
            }
        )
    }
}
