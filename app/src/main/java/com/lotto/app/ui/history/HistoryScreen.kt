package com.lotto.app.ui.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lotto.app.data.local.entity.CreditDocumentEntity
import com.lotto.app.data.local.entity.CreditPaymentEntity
import com.lotto.app.data.local.entity.DrawDateChangeEntity
import com.lotto.app.data.local.entity.DrawResultEntity
import com.lotto.app.data.local.entity.SaleEntity
import com.lotto.app.data.local.model.WinningBillSummary
import com.lotto.app.data.repository.CreditAccountRepository
import com.lotto.app.data.repository.DrawCalendarRepository
import com.lotto.app.data.repository.DrawResultRepository
import com.lotto.app.data.repository.SaleRepository
import com.lotto.app.ui.components.buttons.BackButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class HistoryRecord(
    val id: String,
    val category: String,
    val timestamp: Long,
    val title: String,
    val detail: String,
    val searchText: String
)

private val historyCategories = listOf(
    "ทั้งหมด", "ขายหวย", "บัญชีเชื่อ", "รับชำระ", "ผลรางวัล",
    "บิลถูกรางวัล", "จ่ายรางวัล", "แก้ไขวันออกรางวัล"
)

@Composable
fun HistoryScreen(
    saleRepository: SaleRepository,
    creditAccountRepository: CreditAccountRepository,
    drawCalendarRepository: DrawCalendarRepository,
    drawResultRepository: DrawResultRepository,
    onBack: () -> Unit
) {
    var sales by remember { mutableStateOf<List<SaleEntity>>(emptyList()) }
    var openCreditBills by remember { mutableStateOf<List<CreditDocumentEntity>>(emptyList()) }
    var paidCreditBills by remember { mutableStateOf<List<CreditDocumentEntity>>(emptyList()) }
    var creditPayments by remember { mutableStateOf<List<Pair<Long, CreditPaymentEntity>>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf("ทั้งหมด") }
    var searchQuery by remember { mutableStateOf("") }

    val drawResults by drawResultRepository.observeAllResults().collectAsState(initial = emptyList())
    val drawDateChanges by drawCalendarRepository.observeAllChanges().collectAsState(initial = emptyList())
    val unpaidWinningBills by drawResultRepository.observeWinningBills("UNPAID").collectAsState(initial = emptyList())
    val paidWinningBills by drawResultRepository.observeWinningBills("PAID").collectAsState(initial = emptyList())

    LaunchedEffect(saleRepository, creditAccountRepository) {
        try {
            sales = saleRepository.getAllSales()
            openCreditBills = creditAccountRepository.getOpenBills()
            paidCreditBills = creditAccountRepository.getPaidBills()
            val documents = openCreditBills + paidCreditBills
            creditPayments = documents.flatMap { document ->
                creditAccountRepository.getPayments(document.creditDocumentId).map { document.creditDocumentId to it }
            }
            loadError = null
        } catch (error: Exception) {
            loadError = error.message ?: "ไม่สามารถโหลดประวัติได้"
        }
    }

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("th", "TH")) }
    val records = remember(
        sales, openCreditBills, paidCreditBills, creditPayments, drawResults,
        drawDateChanges, unpaidWinningBills, paidWinningBills
    ) {
        buildList {
            sales.forEach { sale ->
                val paymentType = when {
                    sale.cashAmount > 0L && sale.creditAmount > 0L -> "แบ่งจ่าย"
                    sale.creditAmount > 0L -> "เงินเชื่อ"
                    else -> "เงินสด"
                }
                add(HistoryRecord(
                    id = "sale-${sale.saleId}",
                    category = "ขายหวย",
                    timestamp = sale.createdAt,
                    title = "บิลขายเลขที่ ${sale.saleId}",
                    detail = "${sale.customerFirstName} ${sale.customerLastName} · $paymentType · ยอด ${sale.totalAmount} บาท · งวด ${sale.drawDate ?: "-"}",
                    searchText = "${sale.saleId} ${sale.customerFirstName} ${sale.customerLastName} $paymentType ${sale.drawDate} ${sale.totalAmount}"
                ))
            }
            (openCreditBills + paidCreditBills).filter { it.creditAmount > 0L }.forEach { bill ->
                val status = if (bill.status == CreditDocumentEntity.STATUS_PAID) "ชำระครบแล้ว" else "ค้างชำระ"
                add(HistoryRecord(
                    id = "credit-${bill.creditDocumentId}",
                    category = "บัญชีเชื่อ",
                    timestamp = bill.paidAt ?: bill.createdAt,
                    title = "เอกสารเชื่อเลขที่ ${bill.creditDocumentId} · $status",
                    detail = "${bill.customerFirstName} ${bill.customerLastName} · ยอดเชื่อ ${bill.creditAmount} บาท · บิลขาย ${bill.sourceSaleId}",
                    searchText = "${bill.creditDocumentId} ${bill.sourceSaleId} ${bill.customerFirstName} ${bill.customerLastName} $status ${bill.creditAmount}"
                ))
            }
            creditPayments.forEach { (documentId, payment) ->
                add(HistoryRecord(
                    id = "payment-${payment.paymentId}",
                    category = "รับชำระ",
                    timestamp = payment.paidAt,
                    title = "รับชำระเอกสารเชื่อเลขที่ $documentId",
                    detail = "รับชำระ ${payment.amount} บาท",
                    searchText = "$documentId ${payment.paymentId} ${payment.amount}"
                ))
            }
            drawResults.forEach { result ->
                add(HistoryRecord(
                    id = "result-${result.drawDate}",
                    category = "ผลรางวัล",
                    timestamp = result.recordedAt,
                    title = "ผลรางวัล งวด ${result.drawDate}",
                    detail = "3 ตัว ${result.threeDigit} · 2 ตัวบน ${result.twoDigitTop} · 2 ตัวล่าง ${result.twoDigitBottom}",
                    searchText = "${result.drawDate} ${result.threeDigit} ${result.twoDigitTop} ${result.twoDigitBottom}"
                ))
            }
            (unpaidWinningBills + paidWinningBills).forEach { bill ->
                val isPaid = bill.payoutStatus == "PAID"
                add(HistoryRecord(
                    id = "winning-${bill.drawDate}-${bill.saleId}",
                    category = if (isPaid) "จ่ายรางวัล" else "บิลถูกรางวัล",
                    timestamp = bill.paidAt ?: bill.saleCreatedAt,
                    title = "บิลรางวัลจากบิลขายเลขที่ ${bill.saleId}",
                    detail = "${bill.customerFirstName} ${bill.customerLastName} · งวด ${bill.drawDate} · ${bill.totalPrize} บาท" +
                        if (isPaid) " · ผู้รับ ${bill.recipientName.orEmpty()}" else " · ยังไม่จ่าย",
                    searchText = "${bill.saleId} ${bill.customerFirstName} ${bill.customerLastName} ${bill.drawDate} ${bill.totalPrize} ${bill.recipientName.orEmpty()}"
                ))
            }
            drawDateChanges.forEach { change ->
                add(HistoryRecord(
                    id = "draw-change-${change.changeId}",
                    category = "แก้ไขวันออกรางวัล",
                    timestamp = change.changedAt,
                    title = "เปลี่ยนวันออกรางวัล",
                    detail = "${change.previousDate ?: "-"} → ${change.newDate}",
                    searchText = "${change.previousDate} ${change.newDate} ${change.drawDate}"
                ))
            }
        }.sortedByDescending { it.timestamp }
    }

    val visibleRecords = records.filter { record ->
        (selectedCategory == "ทั้งหมด" || record.category == selectedCategory) &&
            (searchQuery.isBlank() ||
                record.searchText.contains(searchQuery.trim(), ignoreCase = true) ||
                record.title.contains(searchQuery.trim(), ignoreCase = true) ||
                record.detail.contains(searchQuery.trim(), ignoreCase = true))
    }

    Scaffold(
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    BackButton(onBack = onBack)
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("ประวัติทั้งหมด", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("ค้นหาประวัติ") },
                placeholder = { Text("เลขที่บิล ชื่อลูกค้า เลขหวย หรือวันที่") },
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                historyCategories.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) }
                    )
                }
            }
            Text("พบ ${visibleRecords.size} รายการ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (loadError != null) {
                Text(loadError.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
            if (visibleRecords.isEmpty()) {
                Text(if (searchQuery.isBlank()) "ยังไม่มีข้อมูลในหมวดหมู่นี้" else "ไม่พบข้อมูลที่ค้นหา")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visibleRecords, key = { it.id }) { record ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(record.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                    Text(record.category, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                }
                                Text(record.detail, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    dateFormat.format(Date(record.timestamp)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
