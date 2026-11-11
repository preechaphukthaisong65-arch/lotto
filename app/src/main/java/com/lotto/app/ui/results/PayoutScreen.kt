package com.lotto.app.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lotto.app.data.repository.DrawResultRepository
import com.lotto.app.data.repository.CreditAccountRepository
import com.lotto.app.data.local.model.WinningBillSummary
import com.lotto.app.ui.components.buttons.BackButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun PayoutScreen(repository: DrawResultRepository, creditRepository: CreditAccountRepository, onBack: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<WinningBillSummary?>(null) }
    var recipient by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isProcessingPayout by remember { mutableStateOf(false) }
    var outstandingDebt by remember { mutableStateOf<Long?>(null) }
    var debtLookupError by remember { mutableStateOf<String?>(null) }
    var debtWarningAcknowledged by remember { mutableStateOf(false) }
    var legacyIdentityMissing by remember { mutableStateOf(false) }
    var legacyDebtAcknowledged by remember { mutableStateOf(false) }
    var legacyOutstandingDebt by remember { mutableStateOf<Long?>(null) }
    var legacyDebtWarningAcknowledged by remember { mutableStateOf(false) }
    var legacyDebtLookupComplete by remember { mutableStateOf(false) }
    val unpaid by repository.observeWinningBills("UNPAID").collectAsState(initial = emptyList())
    val paid by repository.observeWinningBills("PAID").collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("th", "TH")) }

    androidx.compose.runtime.LaunchedEffect(Unit) { repository.cleanupExpiredPaidWinningBills() }

    androidx.compose.runtime.LaunchedEffect(selected?.saleId, selected?.customerFirstName, selected?.customerLastName) {
        val bill = selected
        if (bill == null) {
            outstandingDebt = null
            debtLookupError = null
            debtWarningAcknowledged = false
            legacyIdentityMissing = false
            legacyDebtAcknowledged = false
            legacyOutstandingDebt = null
            legacyDebtWarningAcknowledged = false
            legacyDebtLookupComplete = true
        } else {
            outstandingDebt = null
            debtLookupError = null
            debtWarningAcknowledged = false
            legacyIdentityMissing = bill.customerId == null
            legacyDebtAcknowledged = false
            legacyOutstandingDebt = null
            legacyDebtWarningAcknowledged = false
            legacyDebtLookupComplete = bill.customerId == null
            if (bill.customerId != null) {
                try {
                    outstandingDebt = creditRepository.getOutstandingCreditByCustomerId(bill.customerId)
                    legacyOutstandingDebt = creditRepository.getOutstandingCreditByCustomer(
                        bill.customerFirstName,
                        bill.customerLastName
                    )
                    legacyDebtLookupComplete = true
                } catch (e: Exception) {
                    debtLookupError = e.message ?: "ตรวจสอบยอดหนี้ลูกค้าไม่สำเร็จ"
                    legacyDebtLookupComplete = true
                }
            }
        }
    }

    Scaffold(bottomBar = {
        Row(Modifier.fillMaxWidth().padding(16.dp)) { BackButton(onBack = onBack) }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("จ่ายรางวัล", style = MaterialTheme.typography.headlineSmall)
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("รอจ่าย (${unpaid.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("จ่ายแล้ว (${paid.size})") })
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val bills = if (tab == 0) unpaid else paid
            if (bills.isEmpty()) Text(if (tab == 0) "ไม่มีบิลรอจ่ายรางวัล" else "ไม่มีประวัติจ่ายรางวัล")
            val billsByDraw = bills.groupBy { it.drawDate }.toSortedMap(compareByDescending { it })
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                billsByDraw.forEach { (drawDate, drawBills) ->
                    item(key = "draw-$drawDate") {
                        Text(
                            "งวดวันที่ $drawDate",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    items(drawBills, key = { "${it.drawDate}-${it.saleId}" }) { bill ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("บิลขายเลขที่ ${bill.saleId}", style = MaterialTheme.typography.titleMedium)
                                Text("ลูกค้า: ${bill.customerFirstName} ${bill.customerLastName}")
                                Text("เงินรางวัลรวม: ${bill.totalPrize} บาท", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                bill.itemDetails.lines().forEach { detail ->
                                    Text(detail, style = MaterialTheme.typography.bodySmall)
                                }
                                if (bill.payoutStatus == "PAID") {
                                    Text("ผู้รับรางวัล: ${bill.recipientName.orEmpty()}")
                                    Text("วันที่จ่าย: ${bill.paidAt?.let { dateFormat.format(Date(it)) } ?: "-"}")
                                } else {
                                    Button(onClick = { selected = bill; recipient = ""; error = null; isProcessingPayout = false; outstandingDebt = null; debtWarningAcknowledged = false }, modifier = Modifier.fillMaxWidth()) {
                                        Text("บันทึกการจ่ายรางวัล")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { bill ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("ยืนยันจ่ายรางวัล") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("งวดวันที่ ${bill.drawDate}")
                    Text("บิลขายเลขที่ ${bill.saleId}")
                    Text("ลูกค้า: ${bill.customerFirstName} ${bill.customerLastName}")
                    bill.itemDetails.lines().forEach { detail -> Text(detail, style = MaterialTheme.typography.bodySmall) }
                    Text("ยอดจ่ายรวม ${bill.totalPrize} บาท", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    when {
                        debtLookupError != null -> Text(
                            debtLookupError.orEmpty(),
                            color = MaterialTheme.colorScheme.error
                        )
                        legacyIdentityMissing -> {
                            Text(
                                "บิลนี้เป็นข้อมูลเดิมที่ยังไม่มีรหัสลูกค้า ระบบจึงไม่สามารถจับคู่หนี้ด้วยชื่อเพียงอย่างเดียวได้ กรุณาตรวจสอบบัญชีเชื่อด้วยตนเอง",
                                color = MaterialTheme.colorScheme.error
                            )
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Checkbox(
                                    checked = legacyDebtAcknowledged,
                                    onCheckedChange = { legacyDebtAcknowledged = it }
                                )
                                Text("ตรวจสอบตัวตนและยอดหนี้ด้วยตนเองแล้ว")
                            }
                        }
                        outstandingDebt == null -> Text("กำลังตรวจสอบยอดหนี้ค้างชำระ...")
                        outstandingDebt!! > 0L -> {
                            Text(
                                "⚠ พบยอดหนี้ค้างชำระชื่อตรงกัน ${outstandingDebt} บาท กรุณาตรวจสอบว่าเป็นลูกค้าคนเดียวกันก่อนจ่ายรางวัล ระบบจะยังไม่หักกลบหนี้ให้อัตโนมัติ",
                                color = MaterialTheme.colorScheme.error
                            )
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Checkbox(
                                    checked = debtWarningAcknowledged,
                                    onCheckedChange = { debtWarningAcknowledged = it }
                                )
                                Text("ตรวจสอบยอดหนี้และยืนยันการจ่ายรางวัลเต็มจำนวนแล้ว")
                            }
                        }
                        else -> Text("ไม่พบยอดหนี้ค้างชำระที่ตรงกับชื่อ-นามสกุลนี้")
                    }
                    if (!legacyIdentityMissing && !legacyDebtLookupComplete && debtLookupError == null) {
                        Text("กำลังตรวจสอบหนี้จากบิลเดิมที่ยังไม่มีรหัสลูกค้า...")
                    }
                    if (!legacyIdentityMissing && (legacyOutstandingDebt ?: 0L) > 0L) {
                        Text(
                            "พบยอดเชื่อข้อมูลเดิมที่ยังไม่มีรหัสลูกค้าและใช้ชื่อเดียวกัน ${legacyOutstandingDebt} บาท กรุณาตรวจสอบว่าเป็นบุคคลเดียวกันหรือไม่ ระบบไม่รวมยอดให้อัตโนมัติ",
                            color = MaterialTheme.colorScheme.error
                        )
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(
                                checked = legacyDebtWarningAcknowledged,
                                onCheckedChange = { legacyDebtWarningAcknowledged = it }
                            )
                            Text("ตรวจสอบหนี้จากข้อมูลเดิมด้วยตนเองแล้ว")
                        }
                    }
                    OutlinedTextField(
                        value = recipient, onValueChange = { recipient = it },
                        label = { Text("ชื่อผู้รับรางวัล *") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = recipient.isNotBlank() && !isProcessingPayout && debtLookupError == null && legacyDebtLookupComplete &&
                    (
                        if (legacyIdentityMissing) legacyDebtAcknowledged
                        else outstandingDebt != null &&
                            (outstandingDebt == 0L || debtWarningAcknowledged) &&
                            ((legacyOutstandingDebt ?: 0L) == 0L || legacyDebtWarningAcknowledged)
                    ), onClick = {
                    if (isProcessingPayout) return@TextButton
                    scope.launch {
                        isProcessingPayout = true
                        error = null
                        try {
                            repository.markWinningBillPaid(bill.drawDate, bill.saleId, recipient)
                            selected = null
                        } catch (e: Exception) {
                            error = e.message ?: "บันทึกการจ่ายรางวัลไม่สำเร็จ"
                        } finally {
                            isProcessingPayout = false
                        }
                    }
                }) { Text(if (isProcessingPayout) "กำลังบันทึก..." else "ยืนยันจ่ายแล้ว") }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("ยกเลิก") } }
        )
    }
}
