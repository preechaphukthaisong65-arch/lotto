package com.lotto.app

import android.app.Application
import android.util.Log
import com.lotto.app.core.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LottoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Local retention cleanup at startup. OPEN credit bills are never eligible.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // Remove expired paid winning rows first; then their source sale may become eligible.
            runCatching { container.drawResultRepository.cleanupExpiredPaidWinningBills() }
                .onFailure { Log.e("LottoRetention", "Winning bill cleanup failed", it) }
            runCatching { container.saleRepository.cleanupExpiredPaidDocuments() }
                .onFailure { Log.e("LottoRetention", "Credit and source sale cleanup failed", it) }
            runCatching { container.drawResultRepository.cleanupExpiredProfitSummaries() }
                .onFailure { Log.e("LottoRetention", "Profit summary cleanup failed", it) }
        }
    }
}
