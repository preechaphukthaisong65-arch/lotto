package com.lotto.app.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object DrawDateReminderScheduler {
    private const val UNIQUE_WORK_NAME = "draw-date-confirmation-reminder"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<DrawDateReminderWorker>(24, TimeUnit.HOURS)
            .addTag(UNIQUE_WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
