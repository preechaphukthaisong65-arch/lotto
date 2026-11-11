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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.lotto.app.data.local.entity.CreditPaymentEntity
import com.lotto.app.data.repository.CreditAccountRepository
import com.lotto.app.ui.components.buttons.BackButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private enum class CreditSort(val label: String) {
    OLDEST("ค้างเก่าที่สุด"),
    HIGHEST_BALANCE("ยอดค้างรวมต่อคนมากที่สุด"),
    MOST_BILLS("จำนวนบิลค้างต่อคนมากที่สุด"),
    CUSTOMER_NAME("ชื่อลูกค้า ก–ฮ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditAccountsScreen(repository: CreditAccountRepository, onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    var openBills by remember { mutableStateOf<List<CreditDocumentEntity>>(emptyList()) }
    var paidBills by remember { mutableStateOf<List<CreditDocumentEntity>>(emptyList()) }
    var paymentHistory by remember { mutableStateOf<Map<Long, List<CreditPaymentEntity>>>(emptyMap()) }
    val paymentInputs = remember { mutableStateMapOf<Long, String>() }
    var refreshKey by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingPayment by remember { mutableStateOf<Triple<CreditDocumentEntity, Long, Long>?>(null) }
    var isProcessingPayment by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSort by remember { mutableStateOf(CreditSort.OLDEST) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("th", "TH")) }

    LaunchedEffect(refreshKey) {
        repository.cleanupExpiredPaidBills()
        openBills = repository.getOpenBills()
        paidBills = repository.getPaidBills()
        paymentHistory = (openBills + paidBills).associate { bill ->
            bill.creditDocumentId to repository.getPayments(bill.creditDocumentId)
        }
    }

    val now = remember(refreshKey) { System.currentTimeMillis() }
    val thirtyDaysMillis = 30L * 24L * 60L * 60L * 1000L
    fun remainingFor(bill: CreditDocumentEntity): Long {
        val paid = paymentHistory[bill.creditDocumentId].orEmpty().sumOf { it.amount }
        return (bill.creditAmount - paid).coerceAtLeast(0L)
    }
    val trackingBills = openBills.filter { bill ->
        now - bill.createdAt >= thirtyDaysMillis && remainingFor(bill) > 0L
    }.sortedWith(compareBy<CreditDocumentEntity> { it.createdAt }.thenBy { it.creditDocumentId })
    val sourceBills = when (selectedTab) {
        0 -> openBills
        1 -> paidBills
        else -> trackingBills
    }
    fun customerKey(bill: CreditDocumentEntity): String =
        bill.customerId?.let { "customer-$it" } ?: "legacy-bill-${bill.creditDocumentId}"

    val customerTotals = sourceBills.groupBy(::customerKey).mapValues { (_, customerBills) ->
        val balance = customerBills.sumOf { remainingFor(it) }
        val oldest = customerBills.minOfOrNull { it.createdAt } ?: Long.MAX_VALUE
        Triple(balance, customerBills.size, oldest)
    }
    val filteredBills = sourceBills
        .filter { bill ->
            val fullName = "${bill.customerFirstName} ${bill.customerLastName}"
            fullName.contains(searchQuery.trim(), ignoreCase = true)
        }
        .sortedWith { first, second ->
            val firstStats = customerTotals[customerKey(first)] ?: Triple(0L, 0, Long.MAX_VALUE)
            val secondStats = customerTotals[customerKey(second)] ?: Triple(0L, 0, Long.MAX_VALUE)
            val customerComparison = when (selectedSort) {
                CreditSort.OLDEST -> firstStats.third.compareTo(secondStats.third)
                CreditSort.HIGHEST_BALANCE -> secondStats.first.compareTo(firstStats.first)
                CreditSort.MOST_BILLS -> secondStats.second.compareTo(firstStats.second)
                CreditSort.CUSTOMER_NAME -> customerKey(first).compareTo(customerKey(second))
            }
            if (customerComparison != 0) customerComparison
            else first.createdAt.compareTo(second.createdAt)
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
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("ติดตามสินเชื่อ (${trackingBills.size})") })
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("ค้นหาชื่อหรือนามสกุลลูกค้า") },
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("เรียงตาม:", modifier = Modifier.padding(top = 12.dp))
                Column {
                    TextButton(onClick = { sortMenuExpanded = true }) {
                        Text(selectedSort.label)
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        CreditSort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    selectedSort = option
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            if (selectedTab == 2) {
                Text(
                    "แจ้งเตือน: มีบิลที่ยังมียอดค้างและเปิดบิลมาแล้วตั้งแต่ 30 วันขึ้นไป",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "โปรดตรวจเลขที่บิลทุกครั้ง ชื่อและนามสกุลที่ซ้ำกันอาจเป็นคนละลูกค้า ระบบรับชำระแยกตามเลขที่บิล",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (filteredBills.isEmpty()) {
                Text(
                    when {
                        sourceBills.isEmpty() && selectedTab == 0 -> "ไม่มีบิลค้างชำระ"
                        sourceBills.isEmpty() && selectedTab == 1 -> "ไม่มีบิลจ่ายแล้ว"
                        sourceBills.isEmpty() -> "ยังไม่มีบิลค้างชำระที่ครบ 30 วัน"
                        else -> "ไม่พบชื่อลูกค้าที่ค้นหา"
                    }
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filteredBills, key = { it.creditDocumentId }) { bill ->
                        val payments = paymentHistory[bill.creditDocumentId].orEmpty()
                        val creditPaid = payments.sumOf { it.amount }
                        val paidTotal = bill.cashAmount + creditPaid
                        val remaining = remainingFor(bill)
                        val stats = customerTotals[customerKey(bill)]
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("เลขที่บิล #${bill.creditDocumentId}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text("รหัสลูกค้า: ${bill.customerId?.toString() ?: "ข้อมูลเดิมไม่มีรหัส · แยกบัญชีตามบิลเพื่อป้องกันชื่อซ้ำ"}", style = MaterialTheme.typography.bodySmall)
                                bill.customerPhone?.let { Text("โทรศัพท์: $it", style = MaterialTheme.typography.bodySmall) }
                                Text(
                                    "${bill.customerFirstName} ${bill.customerLastName}".trim().ifBlank { "ไม่ระบุชื่อลูกค้า" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (selectedTab == 0 && stats != null) {
                                    Text("ยอดค้างรวมของลูกค้ารายนี้: ${stats.first} บาท")
                                    Text("จำนวนบิลค้างของลูกค้ารายนี้: ${stats.second} บิล")
                                }
                                Text("วันที่ขาย: ${dateFormat.format(Date(bill.createdAt))}")
                                if (selectedTab == 2) {
                                    val ageDays = ((now - bill.createdAt).coerceAtLeast(0L) / (24L * 60L * 60L * 1000L))
                                    Text(
                                        "ค้างมาแล้ว $ageDays วัน · ควรติดตามทวงถาม",
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text("ยอดขายรวม: ${bill.saleTotalAmount} บาท")
                                Text("เงินสด: ${bill.cashAmount} บาท")
                                Text("ยอดเงินเชื่อ: ${bill.creditAmount} บาท")
                                Text("รับชำระแล้วรวม: $paidTotal บาท")
                                Text(if (bill.status == CreditDocumentEntity.STATUS_OPEN) "ยอดค้างบิลนี้: $remaining บาท" else "สถานะ: จ่ายแล้ว")
                                if (bill.status == CreditDocumentEntity.STATUS_PAID) {
                                    Text("เริ่มนับอายุ 180 วัน: ${bill.paidAt?.let { dateFormat.format(Date(it)) } ?: "ไม่พบวันที่"}")
                                }
                                payments.forEach { payment ->
                                    Text(
                                        "รับชำระ ${payment.amount} บาท · ${dateFormat.format(Date(payment.paidAt))}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (selectedTab != 1) {
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
                                            } else if (amount > remaining) {
                                                errorMessage = "ยอดรับชำระเกินยอดค้าง $remaining บาท"
                                            } else {
                                                pendingPayment = Triple(bill, amount, remaining)
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
            onDismissRequest = { if (!isProcessingPayment) pendingPayment = null },
            title = { Text("ยืนยันการรับชำระ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ลูกค้า: ${bill.customerFirstName} ${bill.customerLastName}")
                    Text("บิลเลขที่: #${bill.creditDocumentId}")
                    Text("ยอดค้างก่อนชำระ: $outstanding บาท")
                    Text("ยอดที่จะรับชำระ: $amount บาท", fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                TextButton(enabled = !isProcessingPayment, onClick = {
                    if (!isProcessingPayment) {
                        isProcessingPayment = true
                        scope.launch {
                            try {
                                repository.receivePayment(bill.creditDocumentId, amount)
                                paymentInputs[bill.creditDocumentId] = ""
                                errorMessage = null
                                pendingPayment = null
                                refreshKey += 1
                            } catch (error: Exception) {
                                errorMessage = error.message ?: "บันทึกการรับชำระไม่สำเร็จ"
                                pendingPayment = null
                            } finally {
                                isProcessingPayment = false
                            }
                        }
                    }
                }) { Text(if (isProcessingPayment) "กำลังบันทึก..." else "ยืนยัน") }
            },
            dismissButton = {
                TextButton(enabled = !isProcessingPayment, onClick = { pendingPayment = null }) { Text("ยกเลิก") }
            }
        )
    }
}
