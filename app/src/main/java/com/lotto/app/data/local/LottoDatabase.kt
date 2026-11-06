package com.lotto.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lotto.app.data.local.dao.AppSettingDao
import com.lotto.app.data.local.entity.AppSettingEntity

@Database(
    entities = [AppSettingEntity::class],
    version = 1,
    exportSchema = true
)
abstract class LottoDatabase : RoomDatabase() {
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        const val DATABASE_NAME = "lotto_database"
    }
}
