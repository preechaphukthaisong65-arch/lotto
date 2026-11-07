package com.lotto.app

import android.app.Application
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
            container.saleRepository.cleanupExpiredPaidDocuments()
            container.drawResultRepository.cleanupExpiredPaidWinningBills()
        }
    }
}
