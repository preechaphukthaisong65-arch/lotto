package com.lotto.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lotto.app.data.local.dao.AppSettingDao
import com.lotto.app.data.local.dao.SaleDao
import com.lotto.app.data.local.entity.AppSettingEntity
import com.lotto.app.data.local.entity.SaleEntity
import com.lotto.app.data.local.entity.SaleItemEntity

@Database(
    entities = [
        AppSettingEntity::class,
        SaleEntity::class,
        SaleItemEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class LottoDatabase : RoomDatabase() {
    abstract fun appSettingDao(): AppSettingDao
    abstract fun saleDao(): SaleDao

    companion object {
        const val DATABASE_NAME = "lotto_database"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sales (
                        saleId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        customerFirstName TEXT NOT NULL,
                        customerLastName TEXT NOT NULL,
                        totalAmount INTEGER NOT NULL,
                        cashAmount INTEGER NOT NULL,
                        creditAmount INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        status TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sale_items (
                        itemId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        saleId INTEGER NOT NULL,
                        number TEXT NOT NULL,
                        prizeType TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        payoutMultiplier INTEGER NOT NULL,
                        FOREIGN KEY(saleId) REFERENCES sales(saleId) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sale_items_saleId ON sale_items(saleId)"
                )
            }
        }
    }
}
