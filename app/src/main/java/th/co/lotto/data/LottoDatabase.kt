package th.co.lotto.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DrawPeriodEntity::class,
        CustomerEntity::class,
        SaleDocumentEntity::class,
        SaleLineEntity::class,
        PaymentEntryEntity::class,
        NumberLimitRuleEntity::class,
        DrawResultEntity::class,
        AuditEventEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class LottoDatabase : RoomDatabase() {
    abstract fun lottoDao(): LottoDao

    companion object {
        @Volatile private var INSTANCE: LottoDatabase? = null

        fun getInstance(context: Context): LottoDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                LottoDatabase::class.java,
                "lotto-local.db"
            ).addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Posted source documents are append-only. Corrections must be new documents.
                    db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_sale_document_update BEFORE UPDATE ON sale_documents BEGIN SELECT RAISE(ABORT, 'Sale documents are immutable'); END")
                    db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_sale_document_delete BEFORE DELETE ON sale_documents BEGIN SELECT RAISE(ABORT, 'Sale documents are immutable'); END")
                    db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_sale_line_update BEFORE UPDATE ON sale_lines BEGIN SELECT RAISE(ABORT, 'Sale lines are immutable'); END")
                    db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_sale_line_delete BEFORE DELETE ON sale_lines BEGIN SELECT RAISE(ABORT, 'Sale lines are immutable'); END")
                }
            }).build().also { INSTANCE = it }
        }
    }
}
