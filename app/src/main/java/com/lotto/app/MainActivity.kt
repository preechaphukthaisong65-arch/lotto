package com.lotto.app

import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.content.Intent
import android.app.KeyguardManager
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.lotto.app.domain.rules.SaleWindowPolicy
import com.lotto.app.domain.rules.PendingDrawResultPolicy
import com.lotto.app.domain.rules.FullNumberLimitPolicy
import com.lotto.app.domain.rules.NoAcceptNumberPolicy
import com.lotto.app.domain.rules.HalfPayoutNumberPolicy
import com.lotto.app.data.repository.DrawCalendarRepository
import androidx.compose.ui.Modifier
import com.lotto.app.ui.HomeScreen
import com.lotto.app.ui.credit.CreditAccountsScreen
import com.lotto.app.ui.credit.PayDebtScreen
import com.lotto.app.ui.calendar.DrawCalendarScreen
import com.lotto.app.ui.history.HistoryScreen
import com.lotto.app.ui.reports.ReportDetailScreen
import com.lotto.app.ui.reports.ReportsScreen
import com.lotto.app.ui.reports.CurrentSalesReportScreen
import com.lotto.app.ui.reports.PerformanceReportScreen
import com.lotto.app.ui.sale.SaleScreen
import com.lotto.app.ui.settings.SettingsScreen
import com.lotto.app.ui.results.DrawResultScreen
import com.lotto.app.ui.results.WinningResultsScreen
import com.lotto.app.ui.results.PayoutScreen
import java.time.LocalDate

private enum class AppScreen {
    Home, Sale, History, CreditAccounts, PayDebt, Calendar, Settings, Reports, TotalSales, CurrentSales, SalesHistory, Performance, DrawResult, Winners, Payout
}

class MainActivity : FragmentActivity() {
    private val isAuthenticated = mutableStateOf(false)
    private val authenticationError = mutableStateOf<String?>(null)

    private fun requestDeviceAuthentication() {
        val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguard.isDeviceSecure) {
            authenticationError.value = "กรุณาตั้ง PIN, รหัสผ่าน หรือรูปแบบล็อกหน้าจอของเครื่องก่อนใช้งาน Lotto"
            return
        }
        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("ยืนยันตัวตนเพื่อเข้าใช้งาน Lotto")
            .setSubtitle("ใช้ลายนิ้วมือ ใบหน้า หรือ PIN/รหัสล็อกหน้าจอของอุปกรณ์")

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+ supports the combined strong biometric + device credential mode.
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            val availability = BiometricManager.from(this).canAuthenticate(authenticators)
            if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
                authenticationError.value =
                    "อุปกรณ์ยังไม่พร้อมสำหรับการยืนยันตัวตน กรุณาตรวจสอบการล็อกหน้าจอ"
                return
            }
            promptInfoBuilder.setAllowedAuthenticators(authenticators)
        } else {
            // Android 8–10 cannot use the combined authenticator bitmask.
            // The legacy flag lets the system offer the device credential fallback.
            promptInfoBuilder.setDeviceCredentialAllowed(true)
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authenticationError.value = null
                    isAuthenticated.value = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    isAuthenticated.value = false
                    authenticationError.value = errString.toString()
                }

                override fun onAuthenticationFailed() {
                    isAuthenticated.value = false
                    authenticationError.value = "ยืนยันตัวตนไม่สำเร็จ กรุณาลองอีกครั้ง"
                }
            }
        )
        prompt.authenticate(promptInfoBuilder.build())
    }

    override fun onStart() {
        super.onStart()
        if (!isAuthenticated.value) requestDeviceAuthentication()
    }

    override fun onPause() {
        val settings = (application as LottoApplication).container.settingsRepository
        val observedNow = System.currentTimeMillis()
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val stored = settings.get("clock_high_water_epoch_ms")?.toLongOrNull() ?: 0L
                if (observedNow > stored) {
                    settings.save("clock_high_water_epoch_ms", observedNow.toString())
                }
            }
        }
        super.onPause()
    }

    override fun onStop() {
        isAuthenticated.value = false
        super.onStop()
    }
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
        // TEMPORARY QA: FLAG_SECURE is disabled so screenshots and screen recordings
        // can be captured while debugging. Re-enable protection before production use.
        hideSystemBars()
        val container = (application as LottoApplication).container
        val saleRepository = container.saleRepository
        val creditAccountRepository = container.creditAccountRepository
        val customerRepository = container.customerRepository
        val drawCalendarRepository = container.drawCalendarRepository
        val drawResultRepository = container.drawResultRepository
        val settingsRepository = container.settingsRepository

        setContent {
            MaterialTheme {
            if (!isAuthenticated.value) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Lotto ถูกล็อกเพื่อป้องกันการเข้าถึงข้อมูลลูกค้าและรายการเงิน")
                    authenticationError.value?.let { Text(it, modifier = Modifier.padding(top = 12.dp)) }
                    Button(
                        onClick = {
                            val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
                            if (!keyguard.isDeviceSecure) {
                                startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                            } else {
                                requestDeviceAuthentication()
                            }
                        },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text(if ((getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure) "ยืนยันตัวตนอีกครั้ง" else "ตั้งค่าล็อกหน้าจอ")
                    }
                }
            } else {
                MaterialTheme {
                var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Home.name) }
                val drawDates by drawCalendarRepository.observeAllDrawDates().collectAsState(initial = emptyList())
                val drawResults by drawResultRepository.observeAllResults().collectAsState(initial = emptyList())
                var currentThailandTime by remember { mutableStateOf(DrawCalendarRepository.nowInThailand()) }
                val settingRows by settingsRepository.observeAll().collectAsState(initial = emptyList())
                val currentFullLimits = FullNumberLimitPolicy.settingKeys.mapValues { (_, key) ->
                    settingRows.firstOrNull { it.key == key }?.value?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                }
                val currentNoAcceptNumbers = NoAcceptNumberPolicy.settingKeys.mapValues { (_, key) ->
                    NoAcceptNumberPolicy.parse(
                        settingRows.firstOrNull { it.key == key }?.value.orEmpty(),
                        NoAcceptNumberPolicy.typeForKey(key)
                    )
                }
                val currentHalfPayoutNumbers = HalfPayoutNumberPolicy.settingKeys.mapValues { (type, key) ->
                    HalfPayoutNumberPolicy.parse(settingRows.firstOrNull { it.key == key }?.value.orEmpty(), com.lotto.app.domain.rules.PrizeType.valueOf(type))
                }
                val activeSaleDrawDate = SaleWindowPolicy.activeDrawDate(drawDates, currentThailandTime).orEmpty()
                var selectedResultDrawDate by rememberSaveable { mutableStateOf("") }
                LaunchedEffect(Unit) {
                    var lastClockPersistAt = 0L
                    while (true) {
                        currentThailandTime = DrawCalendarRepository.nowInThailand()
                        val wallClock = System.currentTimeMillis()
                        if (wallClock - lastClockPersistAt >= 30_000L) {
                            val lastSeenRaw = settingsRepository.get("clock_high_water_epoch_ms")
                            val lastSeen = lastSeenRaw?.toLongOrNull()
                            if (lastSeenRaw == null || (lastSeen != null && wallClock >= lastSeen)) {
                                settingsRepository.save("clock_high_water_epoch_ms", wallClock.toString())
                                lastClockPersistAt = wallClock
                            }
                        }
                        delay(1_000L)
                    }
                }
                val saleWindow = SaleWindowPolicy.evaluate(drawDates, currentThailandTime)
                val pendingResultDate = PendingDrawResultPolicy.nextPendingDrawDate(
                    drawDates = drawDates,
                    completedDrawDates = drawResults.mapTo(mutableSetOf()) { it.drawDate },
                    nowThailand = currentThailandTime
                )
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
                            drawDate = activeSaleDrawDate,
                            customerRepository = customerRepository,
                            onGetLegacyOutstandingCredit = { firstName, lastName ->
                                creditAccountRepository.getOutstandingCreditByCustomer(firstName, lastName)
                            },
                            fullLimitsByPrizeType = currentFullLimits,
                            noAcceptNumbersByPrizeType = currentNoAcceptNumbers,
                            halfPayoutNumbersByPrizeType = currentHalfPayoutNumbers,
                            onGetPrizeTypeStake = { drawDate, prizeType ->
                                saleRepository.getPrizeTypeStakeForDraw(drawDate, prizeType)
                            },
                            onSave = { draft ->
                                val latestStatus = SaleWindowPolicy.evaluate(
                                    drawDates,
                                    DrawCalendarRepository.nowInThailand()
                                )
                                check(latestStatus.isOpen) { "หมดเวลารับขายแล้ว ไม่สามารถบันทึกบิลได้" }
                                val targetDrawDate = SaleWindowPolicy.activeDrawDate(drawDates, DrawCalendarRepository.nowInThailand())
                                    ?: error("ไม่พบงวดหวยที่เปิดรับขาย")
                                val fullLimits = FullNumberLimitPolicy.load(settingsRepository)
                                val noAcceptNumbers = NoAcceptNumberPolicy.load(settingsRepository)
                                check(draft.items.none { item -> item.number in (noAcceptNumbers[item.prizeType] ?: emptySet()) }) { "มีเลขที่ตั้งค่าไม่รับ ไม่สามารถบันทึกบิลได้" }
                                val halfNumbers = HalfPayoutNumberPolicy.load(settingsRepository)
                                check(draft.items.all { item ->
                                    val type = com.lotto.app.domain.rules.PrizeType.valueOf(item.prizeType)
                                    val isHalf = item.number in (halfNumbers[item.prizeType] ?: emptySet())
                                    val expected = if (isHalf) type.fullPayoutMultiplier / 2L else type.fullPayoutMultiplier
                                    item.payoutMultiplier == expected
                                }) { "สถานะจ่ายครึ่งราคาเปลี่ยนแปลง กรุณาตรวจสอบรายการอีกครั้ง" }
                                val saleId = saleRepository.saveConfirmedSale(draft, targetDrawDate, fullLimits)
                                Toast.makeText(this, "บันทึกการขายเรียบร้อย เลขที่รายการ $saleId", Toast.LENGTH_LONG).show()
                                saleId
                            }
                        )
                        AppScreen.Payout.name -> PayoutScreen(
                            repository = drawResultRepository,
                            creditRepository = creditAccountRepository,
                            onBack = { currentScreen = AppScreen.Home.name }
                        )
                        AppScreen.Winners.name -> WinningResultsScreen(
                            drawDate = selectedResultDrawDate,
                            repository = drawResultRepository,
                            onBack = { currentScreen = AppScreen.Home.name },
                            onEditResult = { currentScreen = AppScreen.DrawResult.name }
                        )
                        AppScreen.DrawResult.name -> DrawResultScreen(
                            drawDate = selectedResultDrawDate,
                            onBack = {
                                currentScreen = if (drawResults.any { it.drawDate == selectedResultDrawDate }) {
                                    AppScreen.Winners.name
                                } else {
                                    AppScreen.Home.name
                                }
                            },
                            existingResult = drawResults.firstOrNull { it.drawDate == selectedResultDrawDate },
                            onSave = { three, top, bottom, reason ->
                                val existing = drawResults.firstOrNull { it.drawDate == selectedResultDrawDate }
                                val count = if (existing == null) {
                                    drawResultRepository.saveResult(selectedResultDrawDate, three, top, bottom)
                                } else {
                                    drawResultRepository.correctResult(selectedResultDrawDate, three, top, bottom, reason)
                                }
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
                        AppScreen.PayDebt.name -> PayDebtScreen(
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
                            backupManager = container.encryptedBackupManager,
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
                            drawResultRepository = drawResultRepository,
                            now = currentThailandTime,
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
                            onBack = { currentScreen = AppScreen.TotalSales.name },
                            drawResultRepository = drawResultRepository,
                            now = currentThailandTime
                        )
                        AppScreen.Performance.name -> PerformanceReportScreen(
                            drawResultRepository = drawResultRepository,
                            now = currentThailandTime,
                            onBack = { currentScreen = AppScreen.Reports.name }
                        )
                        else -> HomeScreen(
                            onOpenSale = { currentScreen = AppScreen.Sale.name },
                            onOpenReports = { currentScreen = AppScreen.Reports.name },
                            onOpenHistory = { currentScreen = AppScreen.History.name },
                            onOpenCreditAccounts = { currentScreen = AppScreen.CreditAccounts.name },
                            onOpenPayDebt = { currentScreen = AppScreen.PayDebt.name },
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
    }
}
