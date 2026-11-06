package com.lotto.app.core.di

import android.content.Context
import androidx.room.Room
import com.lotto.app.data.local.LottoDatabase
import com.lotto.app.data.repository.RoomSettingsRepository
import com.lotto.app.data.repository.SettingsRepository

class AppContainer(context: Context) {
    private val database: LottoDatabase = Room.databaseBuilder(
        context.applicationContext,
        LottoDatabase::class.java,
        LottoDatabase.DATABASE_NAME
    ).build()

    val settingsRepository: SettingsRepository by lazy {
        RoomSettingsRepository(database.appSettingDao())
    }
}
