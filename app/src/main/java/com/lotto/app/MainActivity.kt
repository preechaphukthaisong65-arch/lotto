package com.lotto.app

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import com.lotto.app.domain.rules.SaleWindowPolicy
import com.lotto.app.domain.rules.FullNumberLimitPolicy
import com.lotto.app.data.repository.DrawCalendarRepository
import androidx.compose.ui.Modifier
import com.lotto.app.ui.HomeScreen
import com.lotto.app.ui.credit.CreditAccountsScreen
import com.lotto.app.ui.calendar.DrawCalendarScreen
import com.lotto.app.ui.history.HistoryScreen
import com.lotto.app.ui.reports.ReportDetailScreen
import com.lotto.app.ui.reports.ReportsScreen
import com.lotto.app.ui.reports.CurrentSalesReportScreen
import com.lotto.app.ui.sale.SaleScreen
import com.lotto.app.ui.settings.SettingsScreen
import com.lotto.app.ui.results.DrawResultScreen
import com.lotto.app.ui.results.WinningResultsScreen
import com.lotto.app.ui.results.PayoutScreen
import java.time.LocalDate

private enum class AppScreen {
    Home, Sale, History, CreditAccounts, Calendar, Settings, Reports, TotalSales, CurrentSales, SalesHistory, Performance, DrawResult, Winners, Payout
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
        val container = (application as LottoApplication).container
        val saleRepository = container.saleRepository
        val creditAccountRepository = container.creditAccountRepository
        val drawCalendarRepository = container.drawCalendarRepository
        val drawResultRepository = container.drawResultRepository
        val settingsRepository = container.settingsRepository

        setContent {
            MaterialTheme {
                var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Home.name) }
                val drawDates by drawCalendarRepository.observeAllDrawDates().collectAsState(initial = emptyList())
                val drawResults by drawResultRepository.observeAllResults().collectAsState(initial = emptyList())
                var selectedResultDrawDate by rememberSaveable { mutableStateOf("") }
                var currentThailandTime by remember { mutableStateOf(DrawCalendarRepository.nowInThailand()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        currentThailandTime = DrawCalendarRepository.nowInThailand()
                        delay(1000L)
                    }
                }
                val saleWindow = SaleWindowPolicy.evaluate(drawDates, currentThailandTime)
                val latestDrawDate = drawDates.mapNotNull { runCatching { LocalDate.parse(it.drawDate) }.getOrNull() }
                    .filter { !it.isAfter(currentThailandTime.toLocalDate()) }
                    .maxOrNull()
                val pendingResultDate = latestDrawDate?.takeIf { date ->
                    !currentThailandTime.toLocalDateTime().isBefore(date.atTime(16, 0)) &&
                        drawResults.none { it.drawDate == date.toString() }
                }?.toString()
                LaunchedEffect(currentScreen, saleWindow.isOpen) {
                    if (currentScreen == AppScreen.Sale.name && !saleWindow.isOpen) {
                        Toast.makeText(this@MainActivity, "ปิดรับขายแล้ว", Toast.LENGTH_SHORT).show()
                        currentScreen = AppScreen.Home.name
                    }
                }
                Surface(modifier = Modifier) {
                    when (currentScreen) {
                        AppScreen.Sale.name -> SaleScreen(
                            onBack = { currentScreen = AppScreen.Home.name },
                            onSave = { draft ->
                                val latestStatus = SaleWindowPolicy.evaluate(
                                    drawDates,
                                    DrawCalendarRepository.nowInThailand()
                                )
                                check(latestStatus.isOpen) { "หมดเวลารับขายแล้ว ไม่สามารถบันทึกบิลได้" }
                                val targetDrawDate = SaleWindowPolicy.activeDrawDate(drawDates, DrawCalendarRepository.nowInThailand())
                                    ?: error("ไม่พบงวดหวยที่เปิดรับขาย")
                                val fullLimits = FullNumberLimitPolicy.load(settingsRepository)
                                val saleId = saleRepository.saveConfirmedSale(draft, targetDrawDate, fullLimits)
                                Toast.makeText(this, "บันทึกการขายเรียบร้อย เลขที่รายการ $saleId", Toast.LENGTH_LONG).show()
                                currentScreen = AppScreen.Home.name
                                saleId
                            }
                        )
                        AppScreen.Payout.name -> PayoutScreen(
                            repository = drawResultRepository,
                            onBack = { currentScreen = AppScreen.Home.name }
                        )
                        AppScreen.Winners.name -> WinningResultsScreen(
                            drawDate = selectedResultDrawDate,
                            repository = drawResultRepository,
                            onBack = { currentScreen = AppScreen.Home.name }
                        )
                        AppScreen.DrawResult.name -> DrawResultScreen(
                            drawDate = selectedResultDrawDate,
                            onBack = { currentScreen = AppScreen.Home.name },
                            onSave = { three, top, bottom ->
                                val count = drawResultRepository.saveResult(selectedResultDrawDate, three, top, bottom)
                                currentScreen = AppScreen.Winners.name
                                count
                            }
                        )
                        AppScreen.History.name -> HistoryScreen(
                            saleRepository = saleRepository,
                            creditAccountRepository = creditAccountRepository,
                            drawCalendarRepository = drawCalendarRepository,
                            drawResultRepository = drawResultRepository,
                            onBack = { currentScreen = AppScreen.Home.name }
                        )
                        AppScreen.CreditAccounts.name -> CreditAccountsScreen(
                            repository = creditAccountRepository,
                            onBack = { currentScreen = AppScreen.Home.name }
                        )
                        AppScreen.Calendar.name -> DrawCalendarScreen(
                            repository = drawCalendarRepository,
                            onBack = { currentScreen = AppScreen.Home.name }
                        )
                        AppScreen.Settings.name -> SettingsScreen(
                            repository = drawCalendarRepository,
                            settingsRepository = settingsRepository,
                            onBack = { currentScreen = AppScreen.Home.name }
                        )
                        AppScreen.Reports.name -> ReportsScreen(
                            onBack = { currentScreen = AppScreen.Home.name },
                            onOpenTotalSales = { currentScreen = AppScreen.TotalSales.name },
                            onOpenPerformance = { currentScreen = AppScreen.Performance.name }
                        )
                        AppScreen.TotalSales.name -> ReportDetailScreen(
                            title = "ยอดขายทั้งหมด",
                            onBack = { currentScreen = AppScreen.Reports.name },
                            onOpenCurrentSales = { currentScreen = AppScreen.CurrentSales.name },
                            onOpenSalesHistory = { currentScreen = AppScreen.SalesHistory.name }
                        )
                        AppScreen.CurrentSales.name -> CurrentSalesReportScreen(
                            saleRepository = saleRepository,
                            drawResultRepository = drawResultRepository,
                            drawDates = drawDates,
                            now = currentThailandTime,
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
                            onOpenReports = { currentScreen = AppScreen.Reports.name },
                            onOpenHistory = { currentScreen = AppScreen.History.name },
                            onOpenCreditAccounts = { currentScreen = AppScreen.CreditAccounts.name },
                            onOpenPayout = { currentScreen = AppScreen.Payout.name },
                            onOpenCalendar = { currentScreen = AppScreen.Calendar.name },
                            onOpenSettings = { currentScreen = AppScreen.Settings.name },
                            saleOpen = saleWindow.isOpen,
                            saleStatusMessage = saleWindow.message,
                            pendingResultDate = pendingResultDate,
                            onOpenDrawResult = {
                                selectedResultDrawDate = pendingResultDate.orEmpty()
                                currentScreen = AppScreen.DrawResult.name
                            }
                        )
                    }
                }
            }
        }
    }
}
