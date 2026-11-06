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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import com.lotto.app.data.local.entity.CreditPaymentEntity
import com.lotto.app.data.repository.CreditAccountRepository
import com.lotto.app.ui.components.buttons.BackButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun CreditAccountsScreen(repository: CreditAccountRepository, onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    var openBills by remember { mutableStateOf<List<CreditDocumentEntity>>(emptyList()) }
    var paidBills by remember { mutableStateOf<List<CreditDocumentEntity>>(emptyList()) }
    var paymentHistory by remember { mutableStateOf<Map<Long, List<CreditPaymentEntity>>>(emptyMap()) }
    val paymentInputs = remember { mutableStateMapOf<Long, String>() }
    var refreshKey by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("th", "TH")) }

    LaunchedEffect(refreshKey) {
        openBills = repository.getOpenBills()
        paidBills = repository.getPaidBills()
        paymentHistory = (openBills + paidBills).associate { bill ->
            bill.creditDocumentId to repository.getPayments(bill.creditDocumentId)
        }
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
            Text("บัญชีเชื่อ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("บิลค้างชำระ (${openBills.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("บิลจ่ายแล้ว (${paidBills.size})") })
            }
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val bills = if (selectedTab == 0) openBills else paidBills
            if (bills.isEmpty()) {
                Text(if (selectedTab == 0) "ไม่มีบิลค้างชำระ" else "ไม่มีบิลจ่ายแล้ว")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(bills, key = { it.creditDocumentId }) { bill ->
                        val payments = paymentHistory[bill.creditDocumentId].orEmpty()
                        val paid = payments.sumOf { it.amount }
                        val remaining = (bill.creditAmount - paid).coerceAtLeast(0L)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "${bill.customerFirstName} ${bill.customerLastName}".trim().ifBlank { "ไม่ระบุชื่อลูกค้า" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("วันที่ขาย: ${dateFormat.format(Date(bill.createdAt))}")
                                Text("ยอดขายรวม: ${bill.saleTotalAmount} บาท")
                                Text("เงินสด: ${bill.cashAmount} บาท")
                                Text("ยอดเงินเชื่อ: ${bill.creditAmount} บาท")
                                Text("รับชำระแล้ว: $paid บาท")
                                Text(if (bill.status == CreditDocumentEntity.STATUS_OPEN) "ยอดค้าง: $remaining บาท" else "สถานะ: จ่ายแล้ว")
                                payments.forEach { payment ->
                                    Text(
                                        "รับชำระ ${payment.amount} บาท · ${dateFormat.format(Date(payment.paidAt))}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (selectedTab == 0) {
                                    OutlinedTextField(
                                        value = paymentInputs[bill.creditDocumentId].orEmpty(),
                                        onValueChange = { paymentInputs[bill.creditDocumentId] = it.filter(Char::isDigit) },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("รับชำระครั้งนี้ (บาท)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true
                                    )
                                    Button(
                                        onClick = {
                                            val amount = paymentInputs[bill.creditDocumentId]?.toLongOrNull()
                                            if (amount == null || amount <= 0L) {
                                                errorMessage = "กรุณากรอกยอดรับชำระให้ถูกต้อง"
                                            } else {
                                                scope.launch {
                                                    try {
                                                        repository.receivePayment(bill.creditDocumentId, amount)
                                                        paymentInputs[bill.creditDocumentId] = ""
                                                        errorMessage = null
                                                        refreshKey += 1
                                                    } catch (error: Exception) {
                                                        errorMessage = error.message ?: "บันทึกการรับชำระไม่สำเร็จ"
                                                    }
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
}
