package com.lotto.app

import android.app.Application
import android.util.Log
import android.os.SystemClock
import com.lotto.app.domain.rules.RetentionClockPolicy
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
            val now = System.currentTimeMillis()
            val highWater = container.settingsRepository
                .get("clock_high_water_epoch_ms")
                ?.toLongOrNull() ?: 0L
            val uptime = SystemClock.elapsedRealtime()

            // Retention deletion is irreversible. Fail closed when the local wall
            // clock moved backwards, jumped ahead beyond device uptime, or has no
            // persisted checkpoint. This is a sanity gate, not a trusted time source.
            if (!RetentionClockPolicy.isSafeForAutomaticDeletion(
                    nowEpochMillis = now,
                    highWaterEpochMillis = highWater,
                    elapsedRealtimeMillis = uptime
                )
            ) {
                Log.w(
                    "LottoRetention",
                    "Skipping automatic cleanup because device time cannot be validated"
                )
                return@launch
            }

            // Remove expired paid winning rows first; then their source sale may become eligible.
            runCatching { container.drawResultRepository.cleanupExpiredPaidWinningBills() }
                .onFailure { Log.e("LottoRetention", "Winning bill cleanup failed", it) }
            runCatching { container.saleRepository.cleanupExpiredPaidDocuments(now) }
                .onFailure { Log.e("LottoRetention", "Credit and source sale cleanup failed", it) }
            runCatching { container.drawResultRepository.cleanupExpiredProfitSummaries() }
                .onFailure { Log.e("LottoRetention", "Profit summary cleanup failed", it) }
        }
    }
}
