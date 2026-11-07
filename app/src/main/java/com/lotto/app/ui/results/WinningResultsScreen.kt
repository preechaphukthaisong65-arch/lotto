package com.lotto.app.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lotto.app.data.repository.DrawResultRepository
import com.lotto.app.ui.components.buttons.BackButton

@Composable
fun WinningResultsScreen(
    drawDate: String,
    repository: DrawResultRepository,
    onBack: () -> Unit
) {
    val winners by repository.observeWinners(drawDate).collectAsState(initial = emptyList())
    Scaffold(
        bottomBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                BackButton(onBack = onBack)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("ผลตรวจรางวัล", style = MaterialTheme.typography.headlineSmall)
            Text("งวดวันที่ $drawDate", style = MaterialTheme.typography.titleMedium)
            Text("พบบิลถูกรางวัล ${winners.size} รายการ", style = MaterialTheme.typography.bodyLarge)
            if (winners.isEmpty()) {
                Text("ไม่พบบิลที่ถูกรางวัลในงวดนี้")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(winners) { item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                Text("บิลเลขที่ ${item.saleId}  •  ${item.customerFirstName} ${item.customerLastName}", style = MaterialTheme.typography.titleSmall)
                                Text("เลข ${item.number}  •  ${item.prizeType}")
                                Text("ยอดซื้อ ${item.stakeAmount} บาท")
                                Text("เงินรางวัล ${item.prizeAmount} บาท", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
