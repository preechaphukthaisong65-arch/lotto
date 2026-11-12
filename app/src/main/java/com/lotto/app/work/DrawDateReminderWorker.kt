package com.lotto.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lotto.app.MainActivity
import com.lotto.app.R
import com.lotto.app.data.local.LottoDatabase
import java.time.LocalDate
import java.time.ZoneId

class DrawDateReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val database = Room.databaseBuilder(
            applicationContext,
            LottoDatabase::class.java,
            LottoDatabase.DATABASE_NAME
        ).build()
        try {
            val today = LocalDate.now(ZoneId.of("Asia/Bangkok"))
            val dueDates = database.drawCalendarDao().getUnconfirmedDrawDates(
                today.toString(),
                today.plusDays(7).toString()
            )
            if (dueDates.isNotEmpty()) {
                postReminder(dueDates.first())
            }
        } finally {
            database.close()
        }
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }

    private fun postReminder(drawDate: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "ยืนยันวันออกรางวัล", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_CONFIRM_DRAW_DATE, drawDate)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            drawDate.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("ยืนยันวันออกรางวัล")
            .setContentText("ถึงกำหนดยืนยันวันออกรางวัล งวดวันที่ $drawDate กรุณาตรวจสอบและยืนยันวันที่ในแอป Lotto")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "ถึงกำหนดยืนยันวันออกรางวัล งวดวันที่ $drawDate กรุณาตรวจสอบและยืนยันวันที่ในแอป Lotto"
            ))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(drawDate.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "draw_date_confirmation"
    }
}
