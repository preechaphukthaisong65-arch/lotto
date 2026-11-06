package com.lotto.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lotto.app.data.local.dao.AppSettingDao
import com.lotto.app.data.local.dao.SaleDao
import com.lotto.app.data.local.entity.AppSettingEntity
import com.lotto.app.data.local.entity.CreditDocumentEntity
import com.lotto.app.data.local.entity.CreditDocumentItemEntity
import com.lotto.app.data.local.entity.CreditPaymentEntity
import com.lotto.app.data.local.entity.SaleEntity
import com.lotto.app.data.local.entity.SaleItemEntity

@Database(
    entities = [
        AppSettingEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        CreditDocumentEntity::class,
        CreditDocumentItemEntity::class,
        CreditPaymentEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class LottoDatabase : RoomDatabase() {
    abstract fun appSettingDao(): AppSettingDao
    abstract fun saleDao(): SaleDao

    companion object {
        const val DATABASE_NAME = "lotto_database"
        const val PAID_DOCUMENT_RETENTION_DAYS = 180L
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

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
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_items_saleId ON sale_items(saleId)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS credit_documents (
                        creditDocumentId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sourceSaleId INTEGER NOT NULL,
                        customerFirstName TEXT NOT NULL,
                        customerLastName TEXT NOT NULL,
                        saleTotalAmount INTEGER NOT NULL,
                        cashAmount INTEGER NOT NULL,
                        creditAmount INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        status TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS credit_document_items (
                        creditItemId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        creditDocumentId INTEGER NOT NULL,
                        number TEXT NOT NULL,
                        prizeType TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        payoutMultiplier INTEGER NOT NULL,
                        FOREIGN KEY(creditDocumentId) REFERENCES credit_documents(creditDocumentId) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_credit_document_items_creditDocumentId ON credit_document_items(creditDocumentId)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE credit_documents ADD COLUMN paidAt INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS credit_payments (
                        paymentId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        creditDocumentId INTEGER NOT NULL,
                        amount INTEGER NOT NULL,
                        paidAt INTEGER NOT NULL,
                        FOREIGN KEY(creditDocumentId) REFERENCES credit_documents(creditDocumentId) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_credit_payments_creditDocumentId ON credit_payments(creditDocumentId)")
                db.execSQL(
                    """
                    UPDATE credit_documents
                    SET paidAt = createdAt
                    WHERE creditAmount = 0 OR status = 'PAID'
                    """.trimIndent()
                )
                // Backfill old cash sales into the unified paid-bills category.
                db.execSQL(
                    """
                    INSERT INTO credit_documents (
                        sourceSaleId, customerFirstName, customerLastName,
                        saleTotalAmount, cashAmount, creditAmount, createdAt, status, paidAt
                    )
                    SELECT s.saleId, s.customerFirstName, s.customerLastName,
                           s.totalAmount, s.cashAmount, s.creditAmount,
                           s.createdAt, 'PAID', s.createdAt
                    FROM sales s
                    WHERE s.creditAmount = 0
                      AND NOT EXISTS (
                          SELECT 1 FROM credit_documents d
                          WHERE d.sourceSaleId = s.saleId
                      )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO credit_document_items (
                        creditDocumentId, number, prizeType, amount, payoutMultiplier
                    )
                    SELECT d.creditDocumentId, si.number, si.prizeType,
                           si.amount, si.payoutMultiplier
                    FROM credit_documents d
                    INNER JOIN sale_items si ON si.saleId = d.sourceSaleId
                    WHERE d.status = 'PAID'
                      AND d.creditAmount = 0
                      AND NOT EXISTS (
                          SELECT 1 FROM credit_document_items ci
                          WHERE ci.creditDocumentId = d.creditDocumentId
                      )
                    """.trimIndent()
                )
            }
        }
    }
}
