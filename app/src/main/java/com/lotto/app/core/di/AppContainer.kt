package com.lotto.app.core.di

import android.content.Context
import androidx.room.Room
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
            LottoDatabase.MIGRATION_6_7
        )
        .build()

    val settingsRepository: SettingsRepository by lazy { RoomSettingsRepository(database.appSettingDao()) }
    val saleRepository: SaleRepository by lazy { RoomSaleRepository(database.saleDao()) }
    val creditAccountRepository: CreditAccountRepository by lazy { RoomCreditAccountRepository(database.saleDao()) }
    val drawCalendarRepository: DrawCalendarRepository by lazy { DrawCalendarRepository(database.drawCalendarDao()) }
    val drawResultRepository: DrawResultRepository by lazy { RoomDrawResultRepository(database.drawResultDao()) }
}
