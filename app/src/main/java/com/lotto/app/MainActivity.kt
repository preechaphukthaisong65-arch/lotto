package com.lotto.app

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lotto.app.ui.HomeScreen
import com.lotto.app.ui.reports.ReportDetailScreen
import com.lotto.app.ui.reports.ReportsScreen
import com.lotto.app.ui.sale.SaleScreen

private enum class AppScreen {
    Home,
    Sale,
    Reports,
    TotalSales,
    CurrentSales,
    SalesHistory,
    Performance
}

class MainActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        val saleRepository = (application as LottoApplication).container.saleRepository

        setContent {
            MaterialTheme {
                var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Home.name) }
                Surface(modifier = Modifier) {
                    when (currentScreen) {
                        AppScreen.Sale.name -> SaleScreen(
                            onBack = { currentScreen = AppScreen.Home.name },
                            onSave = { draft ->
                                val saleId = saleRepository.saveConfirmedSale(draft)
                                Toast.makeText(
                                    this,
                                    "บันทึกการขายเรียบร้อย เลขที่รายการ $saleId",
                                    Toast.LENGTH_LONG
                                ).show()
                                currentScreen = AppScreen.Home.name
                                saleId
                            }
                        )
                        AppScreen.Reports.name -> ReportsScreen(
                            onBack = { currentScreen = AppScreen.Home.name },
                            onOpenTotalSales = { currentScreen = AppScreen.TotalSales.name },
                            onOpenPerformance = { currentScreen = AppScreen.Performance.name }
                        )
                        AppScreen.TotalSales.name -> ReportDetailScreen(
                            title = "ยอดขายทั้งหมด",
                            onBack = { currentScreen = AppScreen.Reports.name }
                        )
                        AppScreen.CurrentSales.name -> ReportDetailScreen(
                            title = "ยอดขายงวดปัจจุบัน",
                            onBack = { currentScreen = AppScreen.TotalSales.name }
                        )
                        AppScreen.SalesHistory.name -> ReportDetailScreen(
                            title = "ยอดขายทั้งหมด (ย้อนหลัง 24 งวด)",
                            onBack = { currentScreen = AppScreen.TotalSales.name }
                        )
                        AppScreen.Performance.name -> ReportDetailScreen(
                            title = "ยอดผลประกอบการ",
                            onBack = { currentScreen = AppScreen.Reports.name }
                        )
                        else -> HomeScreen(
                            onOpenSale = { currentScreen = AppScreen.Sale.name },
                            onOpenReports = { currentScreen = AppScreen.Reports.name }
                        )
                    }
                }
            }
        }
    }
}
