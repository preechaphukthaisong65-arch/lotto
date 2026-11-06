package th.co.lotto.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
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
    version = 2,
    exportSchema = true
)
abstract class LottoDatabase : RoomDatabase() {
    abstract fun lottoDao(): LottoDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                installIntegrityGuards(db)
            }
        }

        @Volatile private var INSTANCE: LottoDatabase? = null

        private fun installIntegrityGuards(db: SupportSQLiteDatabase) {
            // A second OPEN period is rejected without rewriting or deleting existing records.
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS prevent_multiple_open_periods_insert
                BEFORE INSERT ON draw_periods
                WHEN NEW.status = 'OPEN'
                  AND EXISTS (SELECT 1 FROM draw_periods WHERE status = 'OPEN')
                BEGIN
                    SELECT RAISE(ABORT, 'An open draw period already exists');
                END
            """.trimIndent())
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS prevent_multiple_open_periods_update
                BEFORE UPDATE OF status ON draw_periods
                WHEN NEW.status = 'OPEN' AND OLD.status != 'OPEN'
                  AND EXISTS (SELECT 1 FROM draw_periods WHERE status = 'OPEN' AND periodId != OLD.periodId)
                BEGIN
                    SELECT RAISE(ABORT, 'An open draw period already exists');
                END
            """.trimIndent())

            // Posted financial source records and their audit trail are append-only.
            db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_sale_document_update BEFORE UPDATE ON sale_documents BEGIN SELECT RAISE(ABORT, 'Sale documents are immutable'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_sale_document_delete BEFORE DELETE ON sale_documents BEGIN SELECT RAISE(ABORT, 'Sale documents are immutable'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_sale_line_update BEFORE UPDATE ON sale_lines BEGIN SELECT RAISE(ABORT, 'Sale lines are immutable'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_sale_line_delete BEFORE DELETE ON sale_lines BEGIN SELECT RAISE(ABORT, 'Sale lines are immutable'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_payment_update BEFORE UPDATE ON payment_entries BEGIN SELECT RAISE(ABORT, 'Payment entries are immutable'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_payment_delete BEFORE DELETE ON payment_entries BEGIN SELECT RAISE(ABORT, 'Payment entries are immutable'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_audit_update BEFORE UPDATE ON audit_events BEGIN SELECT RAISE(ABORT, 'Audit events are immutable'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_audit_delete BEFORE DELETE ON audit_events BEGIN SELECT RAISE(ABORT, 'Audit events are immutable'); END")

            db.execSQL("CREATE INDEX IF NOT EXISTS index_draw_periods_status_createdAtEpochMillis ON draw_periods(status, createdAtEpochMillis)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_lines_period_number_category ON sale_lines(periodId, numberText, betCategory)")
        }

        fun getInstance(context: Context): LottoDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                LottoDatabase::class.java,
                "lotto-local.db"
            )
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        installIntegrityGuards(db)
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        installIntegrityGuards(db)
                    }
                })
                .build().also { INSTANCE = it }
        }
    }
}
