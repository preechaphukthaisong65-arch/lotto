package com.lotto.app.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lotto.app.data.local.LottoDatabase
import com.lotto.app.data.repository.CreditAccountRepository
import com.lotto.app.data.repository.DrawCalendarRepository
import com.lotto.app.data.repository.DrawResultRepository
import com.lotto.app.data.repository.RoomCreditAccountRepository
import com.lotto.app.data.repository.RoomDrawResultRepository
import com.lotto.app.data.repository.RoomSaleRepository
import com.lotto.app.data.repository.RoomSettingsRepository
import com.lotto.app.data.repository.SaleRepository
import com.lotto.app.data.repository.SettingsRepository
import java.time.LocalDate
import java.time.ZoneId

class AppContainer(context: Context) {
    private val database: LottoDatabase = Room.databaseBuilder(
        context.applicationContext,
        LottoDatabase::class.java,
        LottoDatabase.DATABASE_NAME
    )
        .addMigrations(
            LottoDatabase.MIGRATION_1_2,
            LottoDatabase.MIGRATION_2_3,
            LottoDatabase.MIGRATION_3_4,
            LottoDatabase.MIGRATION_4_5,
            LottoDatabase.MIGRATION_5_6,
            LottoDatabase.MIGRATION_6_7,
            LottoDatabase.MIGRATION_7_8,
            LottoDatabase.MIGRATION_8_9,
            LottoDatabase.MIGRATION_9_10,
            LottoDatabase.MIGRATION_10_11,
            LottoDatabase.MIGRATION_11_12,
            LottoDatabase.MIGRATION_12_13
        )
        .addCallback(object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // Seed the 100-year baseline only when the calendar is empty.
                // Existing official and user-edited dates are never overwritten.
                val isEmpty = db.query("SELECT COUNT(*) FROM draw_dates").use { cursor ->
                    cursor.moveToFirst() && cursor.getLong(0) == 0L
                }
                if (!isEmpty) return

                val today = LocalDate.now(ZoneId.of("Asia/Bangkok"))
                val endDate = today.plusYears(100)
                val specialDates = mapOf(
                    LocalDate.of(2026, 12, 17) to "วันเลื่อนงวดตามประกาศที่มีอยู่",
                    LocalDate.of(2027, 1, 2) to "วันพิเศษตามชุดวันที่ผู้ใช้กำหนด",
                    LocalDate.of(2027, 1, 17) to "วันพิเศษตามชุดวันที่ผู้ใช้กำหนด",
                    LocalDate.of(2027, 5, 2) to "วันพิเศษตามชุดวันที่ผู้ใช้กำหนด"
                )
                val replacedRegularDates = setOf(
                    LocalDate.of(2026, 12, 16),
                    LocalDate.of(2027, 1, 1),
                    LocalDate.of(2027, 1, 16),
                    LocalDate.of(2027, 5, 1)
                )

                val dates = sortedSetOf<LocalDate>()
                for (year in today.year..endDate.year) {
                    for (month in 1..12) {
                        val first = LocalDate.of(year, month, 1)
                        val sixteenth = LocalDate.of(year, month, 16)
                        if (!first.isBefore(today) && !first.isAfter(endDate) && first !in replacedRegularDates) {
                            dates.add(first)
                        }
                        if (!sixteenth.isBefore(today) && !sixteenth.isAfter(endDate) && sixteenth !in replacedRegularDates) {
                            dates.add(sixteenth)
                        }
                    }
                }
                specialDates.keys
                    .filter { !it.isBefore(today) && !it.isAfter(endDate) }
                    .forEach(dates::add)

                val now = System.currentTimeMillis()
                db.beginTransaction()
                try {
                    dates.forEach { date ->
                        val note = specialDates[date]
                            ?: "วันตั้งต้นตามรอบปกติวันที่ 1/16; ต้องตรวจประกาศ GLO ก่อนยืนยันวันจริง"
                        db.execSQL(
                            "INSERT OR IGNORE INTO draw_dates (drawDate, referenceDate, source, note, updatedAt) VALUES (?, NULL, ?, ?, ?)",
                            arrayOf(date.toString(), "APP_DEFAULT_SCHEDULE", note, now)
                        )
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        })
        .build()

    val settingsRepository: SettingsRepository by lazy { RoomSettingsRepository(database.appSettingDao()) }
    val saleRepository: SaleRepository by lazy { RoomSaleRepository(database.saleDao()) }
    val creditAccountRepository: CreditAccountRepository by lazy { RoomCreditAccountRepository(database.saleDao()) }
    val drawCalendarRepository: DrawCalendarRepository by lazy { DrawCalendarRepository(database.drawCalendarDao()) }
    val drawResultRepository: DrawResultRepository by lazy { RoomDrawResultRepository(database.drawResultDao()) }
}
