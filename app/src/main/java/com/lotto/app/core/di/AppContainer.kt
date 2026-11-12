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
            LottoDatabase.MIGRATION_10_11
        )
        .addCallback(object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // Seed only an empty calendar. Existing user/GLO dates are never overwritten.
                val isEmpty = db.query("SELECT COUNT(*) FROM draw_dates").use { cursor ->
                    cursor.moveToFirst() && cursor.getLong(0) == 0L
                }
                if (!isEmpty) return

                val dates = listOf(
                    "2026-11-16", "2026-12-01", "2026-12-17",
                    "2027-01-02", "2027-01-17",
                    "2027-02-01", "2027-02-16",
                    "2027-03-01", "2027-03-16",
                    "2027-04-01", "2027-04-16",
                    "2027-05-02", "2027-05-16",
                    "2027-06-01", "2027-06-16",
                    "2027-07-01", "2027-07-16",
                    "2027-08-01", "2027-08-16",
                    "2027-09-01", "2027-09-16",
                    "2027-10-01", "2027-10-16",
                    "2027-11-01", "2027-11-16",
                    "2027-12-01", "2027-12-16"
                )
                val now = System.currentTimeMillis()
                dates.forEach { date ->
                    val year = date.substring(0, 4)
                    db.execSQL(
                        "INSERT OR IGNORE INTO draw_dates (drawDate, referenceDate, source, note, updatedAt) VALUES (?, NULL, ?, ?, ?)",
                        arrayOf(
                            date,
                            "APP_DEFAULT_SCHEDULE",
                            "วันตั้งต้นตามปฏิทินหลักที่ผู้ใช้กำหนด ปี $year; ตรวจประกาศ GLO ก่อนวันออกรางวัล",
                            now
                        )
                    )
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
