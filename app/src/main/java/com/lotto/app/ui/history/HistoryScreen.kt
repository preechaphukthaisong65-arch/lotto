package com.lotto.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lotto.app.data.local.entity.SaleEntity
import com.lotto.app.data.repository.SaleRepository
import com.lotto.app.ui.components.buttons.BackButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    saleRepository: SaleRepository,
    onBack: () -> Unit
) {
    var sales by remember { mutableStateOf<List<SaleEntity>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(saleRepository) {
        try {
            sales = saleRepository.getAllSales()
            loadError = null
        } catch (error: Exception) {
            loadError = error.message ?: "ไม่สามารถโหลดประวัติการขายได้"
        }
    }

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("th", "TH")) }

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
            Text(
                text = "ประวัติการขาย",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "รวมบิลเงินสด เงินเชื่อ และบิลแบ่งชำระจากฐานข้อมูลกลาง",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (loadError != null) {
                Text(loadError.orEmpty(), color = MaterialTheme.colorScheme.error)
            } else if (sales.isEmpty()) {
                Text("ยังไม่มีประวัติการขาย")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sales, key = { it.saleId }) { sale ->
                        val paymentLabel = when {
                            sale.cashAmount > 0L && sale.creditAmount > 0L -> "แบ่งจ่าย"
                            sale.creditAmount > 0L -> "เงินเชื่อ"
                            else -> "เงินสด"
                        }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        "บิลเลขที่ ${sale.saleId}",
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(paymentLabel, color = MaterialTheme.colorScheme.primary)
                                }
                                Text(
                                    listOf(sale.customerFirstName, sale.customerLastName)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" ")
                                        .ifBlank { "ไม่ระบุชื่อลูกค้า" }
                                )
                                Text(dateFormat.format(Date(sale.createdAt)))
                                Text(
                                    "ยอดขาย ${sale.totalAmount} บาท",
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (sale.cashAmount > 0L && sale.creditAmount > 0L) {
                                    Text("เงินสด ${sale.cashAmount} บาท · เงินเชื่อ ${sale.creditAmount} บาท")
                                } else if (sale.creditAmount > 0L) {
                                    Text("ยอดเงินเชื่อ ${sale.creditAmount} บาท")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
