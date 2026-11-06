package com.lotto.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    HomeMenuOption.Settings
)

@Composable
fun HomeScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("หน้าหลัก") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "LOTTO",
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium
            )
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(homeMenuOptions) { option ->
                    HomeMenuItem(option = option)
                }
            }
        }
    }
}
