package com.lotto.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lotto.app.ui.components.HomeMenuItem
import com.lotto.app.ui.components.HomeMenuOption

private val homeMenuOptions = listOf(
    HomeMenuOption.Sell,
    HomeMenuOption.Reports,
    HomeMenuOption.History,
    HomeMenuOption.CreditAccounts,
    HomeMenuOption.Calendar,
    HomeMenuOption.Settings
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSale: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCreditAccounts: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,
    saleOpen: Boolean,
    saleStatusMessage: String,
    pendingResultDate: String?,
    onOpenDrawResult: () -> Unit
) {
    if (pendingResultDate != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("กรอกผลรางวัล") },
            text = { Text("ถึงเวลาบันทึกผลรางวัล งวดวันที่ $pendingResultDate กรุณากรอกผลเพื่อให้ระบบค้นหาบิลที่ถูกรางวัล") },
            confirmButton = { Button(onClick = onOpenDrawResult) { Text("กรอกผลรางวัล") } }
        )
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("หน้าหลัก") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "LOTTO",
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium
            )
            Text(
                text = saleStatusMessage,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = if (saleOpen) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.error
            )
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(homeMenuOptions) { option ->
                    HomeMenuItem(
                        option = option,
                        enabled = option != HomeMenuOption.Sell || saleOpen,
                        subtitle = if (option == HomeMenuOption.Sell && !saleOpen) saleStatusMessage else null,
                        onClick = when (option) {
                            HomeMenuOption.Sell -> onOpenSale
                            HomeMenuOption.Reports -> onOpenReports
                            HomeMenuOption.History -> onOpenHistory
                            HomeMenuOption.CreditAccounts -> onOpenCreditAccounts
                            HomeMenuOption.Calendar -> onOpenCalendar
                            HomeMenuOption.Settings -> onOpenSettings
                            else -> ({})
                        }
                    )
                }
            }
        }
    }
}
