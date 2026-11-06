package th.co.lotto

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import th.co.lotto.data.LottoDao
import th.co.lotto.data.LottoDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Local draw-period schedule. Foreground checks and a periodic WorkManager
 * worker evaluate it; missed transitions are caught up on the next run.
 */
object PeriodSchedule {
    private val bangkokZone: ZoneId = ZoneId.of("Asia/Bangkok")

    fun closeAt(drawDateEpochMillis: Long, zoneId: ZoneId = bangkokZone): Long =
        scheduledTime(drawDateEpochMillis, LocalTime.of(14, 0), zoneId)

    fun openNextAt(drawDateEpochMillis: Long, zoneId: ZoneId = bangkokZone): Long =
        scheduledTime(drawDateEpochMillis, LocalTime.of(18, 0), zoneId)

    private fun scheduledTime(
        drawDateEpochMillis: Long,
        localTime: LocalTime,
        zoneId: ZoneId
    ): Long {
        val drawDate = Instant.ofEpochMilli(drawDateEpochMillis).atZone(zoneId).toLocalDate()
        return drawDate.atTime(localTime).atZone(zoneId).toInstant().toEpochMilli()
    }

    fun advance(dao: LottoDao, nowEpochMillis: Long, zoneId: ZoneId = bangkokZone) {
        val open = dao.getOpenPeriod()
        val drawDate = open?.drawDateEpochMillis
        if (open != null && drawDate != null && nowEpochMillis >= closeAt(drawDate, zoneId)) {
            dao.closePeriod(open.periodId, closeAt(drawDate, zoneId))
        }
        dao.openDuePeriod(nowEpochMillis)
    }
}


/**
 * WorkManager catch-up for period transitions while the app is not in the foreground.
 * Android may defer execution; the next run catches up using the saved draw schedule.
 */
object PeriodScheduleReminder {
    private const val WORK_NAME = "lotto_period_schedule_refresh"

    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<PeriodScheduleWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

class PeriodScheduleWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = try {
        PeriodSchedule.advance(
            LottoDatabase.getInstance(applicationContext).lottoDao(),
            System.currentTimeMillis(),
            ZoneId.of("Asia/Bangkok")
        )
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}
