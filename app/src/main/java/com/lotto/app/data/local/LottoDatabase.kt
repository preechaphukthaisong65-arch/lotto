package com.lotto.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lotto.app.data.local.dao.AppSettingDao
import com.lotto.app.data.local.dao.DrawCalendarDao
import com.lotto.app.data.local.dao.DrawResultDao
import com.lotto.app.data.local.dao.SaleDao
import com.lotto.app.data.local.entity.AppSettingEntity
import com.lotto.app.data.local.entity.CreditDocumentEntity
import com.lotto.app.data.local.entity.CreditDocumentItemEntity
import com.lotto.app.data.local.entity.CreditPaymentEntity
import com.lotto.app.data.local.entity.DrawDateEntity
import com.lotto.app.data.local.entity.DrawDateChangeEntity
import com.lotto.app.data.local.entity.DrawResultEntity
import com.lotto.app.data.local.entity.WinningSaleItemEntity
import com.lotto.app.data.local.entity.DrawProfitSummaryEntity
import com.lotto.app.data.local.entity.SaleEntity
import com.lotto.app.data.local.entity.SaleItemEntity

@Database(
    entities = [
        AppSettingEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        CreditDocumentEntity::class,
        CreditDocumentItemEntity::class,
        CreditPaymentEntity::class,
        DrawDateEntity::class,
        DrawDateChangeEntity::class,
        DrawResultEntity::class,
        WinningSaleItemEntity::class,
        DrawProfitSummaryEntity::class
    ],
    version = 8,
    exportSchema = true
)
abstract class LottoDatabase : RoomDatabase() {
    abstract fun appSettingDao(): AppSettingDao
    abstract fun saleDao(): SaleDao
    abstract fun drawCalendarDao(): DrawCalendarDao
    abstract fun drawResultDao(): DrawResultDao

    companion object {
        const val DATABASE_NAME = "lotto_database"
        const val PAID_DOCUMENT_RETENTION_DAYS = 180L
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS sales (saleId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, customerFirstName TEXT NOT NULL, customerLastName TEXT NOT NULL, totalAmount INTEGER NOT NULL, cashAmount INTEGER NOT NULL, creditAmount INTEGER NOT NULL, createdAt INTEGER NOT NULL, status TEXT NOT NULL)""")
                db.execSQL("""CREATE TABLE IF NOT EXISTS sale_items (itemId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, saleId INTEGER NOT NULL, number TEXT NOT NULL, prizeType TEXT NOT NULL, amount INTEGER NOT NULL, payoutMultiplier INTEGER NOT NULL, FOREIGN KEY(saleId) REFERENCES sales(saleId) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_items_saleId ON sale_items(saleId)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS credit_documents (creditDocumentId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sourceSaleId INTEGER NOT NULL, customerFirstName TEXT NOT NULL, customerLastName TEXT NOT NULL, saleTotalAmount INTEGER NOT NULL, cashAmount INTEGER NOT NULL, creditAmount INTEGER NOT NULL, createdAt INTEGER NOT NULL, status TEXT NOT NULL)""")
                db.execSQL("""CREATE TABLE IF NOT EXISTS credit_document_items (creditItemId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, creditDocumentId INTEGER NOT NULL, number TEXT NOT NULL, prizeType TEXT NOT NULL, amount INTEGER NOT NULL, payoutMultiplier INTEGER NOT NULL, FOREIGN KEY(creditDocumentId) REFERENCES credit_documents(creditDocumentId) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_credit_document_items_creditDocumentId ON credit_document_items(creditDocumentId)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE credit_documents ADD COLUMN paidAt INTEGER")
                db.execSQL("""CREATE TABLE IF NOT EXISTS credit_payments (paymentId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, creditDocumentId INTEGER NOT NULL, amount INTEGER NOT NULL, paidAt INTEGER NOT NULL, FOREIGN KEY(creditDocumentId) REFERENCES credit_documents(creditDocumentId) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_credit_payments_creditDocumentId ON credit_payments(creditDocumentId)")
                db.execSQL("UPDATE credit_documents SET paidAt = createdAt WHERE creditAmount = 0 OR status = 'PAID'")
                db.execSQL("""INSERT INTO credit_documents (sourceSaleId, customerFirstName, customerLastName, saleTotalAmount, cashAmount, creditAmount, createdAt, status, paidAt) SELECT s.saleId, s.customerFirstName, s.customerLastName, s.totalAmount, s.cashAmount, s.creditAmount, s.createdAt, 'PAID', s.createdAt FROM sales s WHERE s.creditAmount = 0 AND NOT EXISTS (SELECT 1 FROM credit_documents d WHERE d.sourceSaleId = s.saleId)""")
                db.execSQL("""INSERT INTO credit_document_items (creditDocumentId, number, prizeType, amount, payoutMultiplier) SELECT d.creditDocumentId, si.number, si.prizeType, si.amount, si.payoutMultiplier FROM credit_documents d INNER JOIN sale_items si ON si.saleId = d.sourceSaleId WHERE d.status = 'PAID' AND d.creditAmount = 0 AND NOT EXISTS (SELECT 1 FROM credit_document_items ci WHERE ci.creditDocumentId = d.creditDocumentId)""")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS draw_dates (drawDate TEXT NOT NULL PRIMARY KEY, referenceDate TEXT, source TEXT NOT NULL, note TEXT, updatedAt INTEGER NOT NULL)""")
                db.execSQL("""CREATE TABLE IF NOT EXISTS draw_date_changes (changeId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, drawDate TEXT NOT NULL, previousDate TEXT, newDate TEXT NOT NULL, changedAt INTEGER NOT NULL, source TEXT NOT NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_draw_date_changes_changedAt ON draw_date_changes(changedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_draw_date_changes_drawDate ON draw_date_changes(drawDate)")
            }
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS draw_profit_summaries (drawDate TEXT NOT NULL PRIMARY KEY, salesTotal INTEGER NOT NULL, prizeLiability INTEGER NOT NULL, netProfit INTEGER NOT NULL, recordedAt INTEGER NOT NULL)""")
                db.execSQL("""INSERT OR REPLACE INTO draw_profit_summaries (drawDate, salesTotal, prizeLiability, netProfit, recordedAt)
                    SELECT dr.drawDate,
                           COALESCE((SELECT SUM(s.totalAmount) FROM sales s WHERE s.drawDate = dr.drawDate AND s.status = 'CONFIRMED'), 0),
                           COALESCE((SELECT SUM(w.prizeAmount) FROM winning_sale_items w WHERE w.drawDate = dr.drawDate), 0),
                           COALESCE((SELECT SUM(s.totalAmount) FROM sales s WHERE s.drawDate = dr.drawDate AND s.status = 'CONFIRMED'), 0) -
                           COALESCE((SELECT SUM(w.prizeAmount) FROM winning_sale_items w WHERE w.drawDate = dr.drawDate), 0),
                           dr.recordedAt
                    FROM draw_results dr""")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE winning_sale_items ADD COLUMN payoutStatus TEXT NOT NULL DEFAULT 'UNPAID'")
                db.execSQL("ALTER TABLE winning_sale_items ADD COLUMN recipientName TEXT")
                db.execSQL("ALTER TABLE winning_sale_items ADD COLUMN paidAt INTEGER")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sales ADD COLUMN drawDate TEXT")
                db.execSQL("""CREATE TABLE IF NOT EXISTS draw_results (drawDate TEXT NOT NULL PRIMARY KEY, threeDigit TEXT NOT NULL, twoDigitTop TEXT NOT NULL, twoDigitBottom TEXT NOT NULL, recordedAt INTEGER NOT NULL)""")
                db.execSQL("""CREATE TABLE IF NOT EXISTS winning_sale_items (winId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, drawDate TEXT NOT NULL, saleId INTEGER NOT NULL, saleItemId INTEGER NOT NULL, prizeType TEXT NOT NULL, number TEXT NOT NULL, stakeAmount INTEGER NOT NULL, payoutMultiplier INTEGER NOT NULL, prizeAmount INTEGER NOT NULL)""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_winning_sale_items_drawDate_saleItemId ON winning_sale_items(drawDate, saleItemId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_winning_sale_items_saleId ON winning_sale_items(saleId)")
            }
        }
    }
}
