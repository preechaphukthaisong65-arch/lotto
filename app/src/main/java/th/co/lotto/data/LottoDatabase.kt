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
        SaleCancellationEntity::class,
        SaleLineEntity::class,
        PaymentEntryEntity::class,
        NumberLimitRuleEntity::class,
        NumberBlockRuleEntity::class,
        DrawResultEntity::class,
        AuditEventEntity::class,
        PayoutRateSettingsEntity::class,
        SettlementSnapshotEntity::class,
        DrawSourceCheckEntity::class
    ],
    version = 10,
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE draw_periods ADD COLUMN scheduledOpenEpochMillis INTEGER")
                installIntegrityGuards(db)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS number_block_rules (
                        ruleId TEXT NOT NULL PRIMARY KEY,
                        periodId TEXT NOT NULL,
                        blockCategory TEXT NOT NULL,
                        numberText TEXT NOT NULL,
                        blocked INTEGER NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL,
                        FOREIGN KEY(periodId) REFERENCES draw_periods(periodId)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_number_block_rules_periodId_blockCategory_numberText ON number_block_rules(periodId, blockCategory, numberText)")
                installIntegrityGuards(db)
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Existing rules default to full payout; historical sale rows are unchanged.
                db.execSQL("ALTER TABLE number_limit_rules ADD COLUMN halfPayout INTEGER NOT NULL DEFAULT 0")
                installIntegrityGuards(db)
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Preserve the first known open time for legacy periods and fence future sales.
                db.execSQL("ALTER TABLE draw_periods ADD COLUMN openedAtEpochMillis INTEGER")
                db.execSQL("UPDATE draw_periods SET openedAtEpochMillis = createdAtEpochMillis WHERE status = 'OPEN'")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS payout_rate_settings (
                        settingsId INTEGER NOT NULL PRIMARY KEY,
                        twoDigitRateHundredths INTEGER NOT NULL,
                        threeTopRateHundredths INTEGER NOT NULL,
                        threeBottomRateHundredths INTEGER NOT NULL,
                        threeToadRateHundredths INTEGER NOT NULL,
                        configVersion INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL
                    )
                """.trimIndent())
                installIntegrityGuards(db)
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS settlement_snapshots (
                        settlementId TEXT NOT NULL PRIMARY KEY,
                        periodId TEXT NOT NULL,
                        twoDigitRateHundredths INTEGER NOT NULL,
                        threeTopRateHundredths INTEGER NOT NULL,
                        threeBottomRateHundredths INTEGER NOT NULL,
                        threeToadRateHundredths INTEGER NOT NULL,
                        payoutConfigVersion INTEGER NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL,
                        FOREIGN KEY(periodId) REFERENCES draw_periods(periodId)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_settlement_snapshots_periodId ON settlement_snapshots(periodId)")
                installIntegrityGuards(db)
                db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_settlement_snapshot_update BEFORE UPDATE ON settlement_snapshots BEGIN SELECT RAISE(ABORT, 'Settlement snapshots are immutable'); END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_settlement_snapshot_delete BEFORE DELETE ON settlement_snapshots BEGIN SELECT RAISE(ABORT, 'Settlement snapshots are immutable'); END")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE payout_rate_settings ADD COLUMN twoTopRateHundredths INTEGER NOT NULL DEFAULT 7000")
                db.execSQL("ALTER TABLE payout_rate_settings ADD COLUMN twoBottomRateHundredths INTEGER NOT NULL DEFAULT 7000")
                db.execSQL("UPDATE payout_rate_settings SET twoTopRateHundredths = twoDigitRateHundredths, twoBottomRateHundredths = twoDigitRateHundredths")
                db.execSQL("ALTER TABLE settlement_snapshots ADD COLUMN twoTopRateHundredths INTEGER NOT NULL DEFAULT 7000")
                db.execSQL("ALTER TABLE settlement_snapshots ADD COLUMN twoBottomRateHundredths INTEGER NOT NULL DEFAULT 7000")
                db.execSQL("UPDATE settlement_snapshots SET twoTopRateHundredths = twoDigitRateHundredths, twoBottomRateHundredths = twoDigitRateHundredths")
                // Replace pre-v9 category guards so existing installations can accept the two new categories.
                listOf(
                    "validate_sale_line_category_insert",
                    "validate_limit_rule_category_insert",
                    "validate_limit_rule_category_update",
                    "validate_number_block_category_insert",
                    "validate_number_block_category_update",
                    "validate_draw_result_category_insert"
                ).forEach { trigger -> db.execSQL("DROP TRIGGER IF EXISTS $trigger") }
                installIntegrityGuards(db)
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sale_cancellations (
                        cancellationId TEXT NOT NULL PRIMARY KEY,
                        documentId TEXT NOT NULL,
                        originalAmountSatang INTEGER NOT NULL,
                        reason TEXT NOT NULL,
                        cancelledAtEpochMillis INTEGER NOT NULL,
                        FOREIGN KEY(documentId) REFERENCES sale_documents(documentId)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sale_cancellations_documentId ON sale_cancellations(documentId)")
                db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS validate_sale_cancellation_insert
                BEFORE INSERT ON sale_cancellations
                WHEN NEW.originalAmountSatang <= 0
                  OR length(trim(NEW.reason)) = 0
                  OR NOT EXISTS (
                    SELECT 1 FROM sale_documents
                    WHERE documentId = NEW.documentId
                      AND totalAmountSatang = NEW.originalAmountSatang
                  )
                  OR EXISTS (SELECT 1 FROM payment_entries WHERE documentId = NEW.documentId)
                  OR EXISTS (
                    SELECT 1 FROM sale_documents sd
                    JOIN draw_results dr ON dr.periodId = sd.periodId
                    WHERE sd.documentId = NEW.documentId
                  )
                  OR EXISTS (
                    SELECT 1 FROM sale_documents sd
                    JOIN settlement_snapshots ss ON ss.periodId = sd.periodId
                    WHERE sd.documentId = NEW.documentId
                  )
                BEGIN
                    SELECT RAISE(ABORT, 'Sale cancellation is invalid or blocked by payment/result/settlement');
                END
            """.trimIndent())
            db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_sale_cancellation_update BEFORE UPDATE ON sale_cancellations BEGIN SELECT RAISE(ABORT, 'Sale cancellation records are immutable'); END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_sale_cancellation_delete BEFORE DELETE ON sale_cancellations BEGIN SELECT RAISE(ABORT, 'Sale cancellation records are immutable'); END")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS draw_source_checks (checkId TEXT NOT NULL PRIMARY KEY, periodId TEXT NOT NULL, sourceId TEXT NOT NULL, drawDateIso TEXT NOT NULL, firstPrizeNumber TEXT NOT NULL, threeTopNumber TEXT NOT NULL, twoBottomNumber TEXT NOT NULL, sourceUrl TEXT NOT NULL, fetchedAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(periodId) REFERENCES draw_periods(periodId) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_draw_source_checks_periodId_sourceId ON draw_source_checks(periodId, sourceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_draw_source_checks_periodId_fetchedAtEpochMillis ON draw_source_checks(periodId, fetchedAtEpochMillis)")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_draw_source_check_update BEFORE UPDATE ON draw_source_checks BEGIN SELECT RAISE(ABORT, 'source check immutable'); END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_draw_source_check_delete BEFORE DELETE ON draw_source_checks BEGIN SELECT RAISE(ABORT, 'source check immutable'); END")
            }
        }

        @Volatile private var INSTANCE: LottoDatabase? = null

        internal fun installIntegrityGuards(db: SupportSQLiteDatabase) {
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

            // Enforce period/document relationships even if a DAO insert is called outside postSale().
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS validate_sale_line_category_insert
                BEFORE INSERT ON sale_lines
                WHEN NEW.betCategory NOT IN ('TWO_TOP', 'TWO_BOTTOM', 'THREE_TOP', 'THREE_TOAD')
                  OR length(NEW.numberText) != CASE WHEN NEW.betCategory IN ('TWO_TOP', 'TWO_BOTTOM') THEN 2 ELSE 3 END
                  OR NEW.numberText GLOB '*[^0-9]*'
                BEGIN
                    SELECT RAISE(ABORT, 'Only two-digit top/bottom and three-digit top/toad sale lines are supported');
                END
            """.trimIndent())
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS validate_limit_rule_category_insert
                BEFORE INSERT ON number_limit_rules
                WHEN NEW.numberLength != CASE WHEN NEW.betCategory IN ('TWO_TOP', 'TWO_BOTTOM') THEN 2 ELSE 3 END
                  OR length(NEW.numberText) != NEW.numberLength
                  OR NEW.betCategory NOT IN ('TWO_TOP', 'TWO_BOTTOM', 'THREE_TOP', 'THREE_TOAD')
                  OR NEW.numberText GLOB '*[^0-9]*'
                BEGIN
                    SELECT RAISE(ABORT, 'Only supported two-digit and three-digit limit categories are allowed');
                END
            """.trimIndent())
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS validate_limit_rule_category_update
                BEFORE UPDATE ON number_limit_rules
                WHEN NEW.numberLength != CASE WHEN NEW.betCategory IN ('TWO_TOP', 'TWO_BOTTOM') THEN 2 ELSE 3 END
                  OR length(NEW.numberText) != NEW.numberLength
                  OR NEW.betCategory NOT IN ('TWO_TOP', 'TWO_BOTTOM', 'THREE_TOP', 'THREE_TOAD')
                  OR NEW.numberText GLOB '*[^0-9]*'
                BEGIN
                    SELECT RAISE(ABORT, 'Only three-digit top and toad limits are supported');
                END
            """.trimIndent())
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS validate_number_block_category_insert
                BEFORE INSERT ON number_block_rules
                WHEN length(NEW.numberText) != CASE WHEN NEW.blockCategory IN ('TWO_TOP', 'TWO_BOTTOM') THEN 2 ELSE 3 END
                  OR NEW.blockCategory NOT IN ('TWO_TOP', 'TWO_BOTTOM', 'THREE_TOP', 'THREE_TOAD')
                  OR NEW.numberText GLOB '*[^0-9]*'
                BEGIN
                    SELECT RAISE(ABORT, 'Only supported two-digit and three-digit blocked-number categories are allowed');
                END
            """.trimIndent())
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS validate_number_block_category_update
                BEFORE UPDATE ON number_block_rules
                WHEN length(NEW.numberText) != CASE WHEN NEW.blockCategory IN ('TWO_TOP', 'TWO_BOTTOM') THEN 2 ELSE 3 END
                  OR NEW.blockCategory NOT IN ('TWO_TOP', 'TWO_BOTTOM', 'THREE_TOP', 'THREE_TOAD')
                  OR NEW.numberText GLOB '*[^0-9]*'
                BEGIN
                    SELECT RAISE(ABORT, 'Only three-digit top and toad blocked numbers are supported');
                END
            """.trimIndent())
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS prevent_duplicate_draw_result_insert
                BEFORE INSERT ON draw_results
                WHEN EXISTS (
                    SELECT 1 FROM draw_results
                    WHERE periodId = NEW.periodId AND resultType = NEW.resultType
                )
                BEGIN
                    SELECT RAISE(ABORT, 'A draw result for this period and category already exists');
                END
            """.trimIndent())
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS validate_draw_result_category_insert
                BEFORE INSERT ON draw_results
                WHEN NEW.resultType NOT IN ('THREE_TOP', '3 ตัวบน', 'TWO_BOTTOM', '2 ตัวล่าง')
                  OR length(NEW.winningNumber) != CASE WHEN NEW.resultType IN ('TWO_BOTTOM', '2 ตัวล่าง') THEN 2 ELSE 3 END
                  OR NEW.winningNumber GLOB '*[^0-9]*'
                BEGIN
                    SELECT RAISE(ABORT, 'Only official first-prize top and last-two results are supported');
                END
            """.trimIndent())
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS validate_limit_rule_period_insert
                BEFORE INSERT ON number_limit_rules
                WHEN NOT EXISTS (SELECT 1 FROM draw_periods WHERE periodId = NEW.periodId)
                BEGIN SELECT RAISE(ABORT, 'Number limit rule references an unknown period'); END
            """.trimIndent())
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS validate_limit_rule_period_update
                BEFORE UPDATE OF periodId ON number_limit_rules
                WHEN NOT EXISTS (SELECT 1 FROM draw_periods WHERE periodId = NEW.periodId)
                BEGIN SELECT RAISE(ABORT, 'Number limit rule references an unknown period'); END
            """.trimIndent())
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS validate_draw_result_period_insert
                BEFORE INSERT ON draw_results
                WHEN NOT EXISTS (SELECT 1 FROM draw_periods WHERE periodId = NEW.periodId)
                BEGIN SELECT RAISE(ABORT, 'Draw result references an unknown period'); END
            """.trimIndent())
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS validate_draw_result_period_update
                BEFORE UPDATE OF periodId ON draw_results
                WHEN NOT EXISTS (SELECT 1 FROM draw_periods WHERE periodId = NEW.periodId)
                BEGIN SELECT RAISE(ABORT, 'Draw result references an unknown period'); END
            """.trimIndent())
            db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_draw_result_update BEFORE UPDATE ON draw_results BEGIN SELECT RAISE(ABORT, 'Draw results are immutable; record a new result event'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_draw_result_delete BEFORE DELETE ON draw_results BEGIN SELECT RAISE(ABORT, 'Draw results are immutable'); END")
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS validate_sale_correction_reference_insert
                BEFORE INSERT ON sale_documents
                WHEN NEW.correctionOfDocumentId IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM sale_documents
                    WHERE documentId = NEW.correctionOfDocumentId
                      AND periodId = NEW.periodId
                      AND correctionOfDocumentId IS NULL
                  )
                BEGIN
                    SELECT RAISE(ABORT, 'Correction must reference an original document in the same period');
                END
            """.trimIndent())
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS prevent_sale_correction_insert
                BEFORE INSERT ON sale_documents
                WHEN NEW.correctionOfDocumentId IS NOT NULL
                BEGIN
                    SELECT RAISE(ABORT, 'Correction documents are disabled; cancel the original bill and create a new bill');
                END
            """.trimIndent())
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS validate_sale_line_parent_insert
                BEFORE INSERT ON sale_lines
                WHEN NOT EXISTS (
                    SELECT 1 FROM sale_documents
                    WHERE documentId = NEW.documentId AND periodId = NEW.periodId
                ) OR EXISTS (SELECT 1 FROM sale_cancellations WHERE documentId = NEW.documentId)
                BEGIN SELECT RAISE(ABORT, 'Sale line does not match an active source document'); END
            """.trimIndent())
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS validate_payment_parent_insert
                BEFORE INSERT ON payment_entries
                WHEN NOT EXISTS (
                    SELECT 1 FROM sale_documents
                    WHERE documentId = NEW.documentId
                      AND customerId = NEW.customerId
                      AND customerType = 'CREDIT'
                ) OR EXISTS (SELECT 1 FROM sale_cancellations WHERE documentId = NEW.documentId)
                BEGIN SELECT RAISE(ABORT, 'Payment does not match an active credit document'); END
            """.trimIndent())

            db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_settlement_snapshot_update BEFORE UPDATE ON settlement_snapshots BEGIN SELECT RAISE(ABORT, 'Settlement snapshots are immutable'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_settlement_snapshot_delete BEFORE DELETE ON settlement_snapshots BEGIN SELECT RAISE(ABORT, 'Settlement snapshots are immutable'); END")

            // Posted financial source records and their audit trail are append-only.
            db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_sale_cancellation_update BEFORE UPDATE ON sale_cancellations BEGIN SELECT RAISE(ABORT, 'Sale cancellation records are immutable'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_sale_cancellation_delete BEFORE DELETE ON sale_cancellations BEGIN SELECT RAISE(ABORT, 'Sale cancellation records are immutable'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_sale_document_update BEFORE UPDATE ON sale_documents BEGIN SELECT RAISE(ABORT, 'Sale documents are immutable'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_sale_document_delete BEFORE DELETE ON sale_documents BEGIN SELECT RAISE(ABORT, 'Sale documents are immutable'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_sale_line_update BEFORE UPDATE ON sale_lines BEGIN SELECT RAISE(ABORT, 'Sale lines are immutable'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_sale_line_delete BEFORE DELETE ON sale_lines BEGIN SELECT RAISE(ABORT, 'Sale lines are immutable'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_payment_update BEFORE UPDATE ON payment_entries BEGIN SELECT RAISE(ABORT, 'Payment entries are immutable'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_payment_delete BEFORE DELETE ON payment_entries BEGIN SELECT RAISE(ABORT, 'Payment entries are immutable'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_audit_update BEFORE UPDATE ON audit_events BEGIN SELECT RAISE(ABORT, 'Audit events are immutable'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_audit_delete BEFORE DELETE ON audit_events BEGIN SELECT RAISE(ABORT, 'Audit events are immutable'); END")

            db.execSQL("CREATE INDEX IF NOT EXISTS index_draw_periods_status_createdAtEpochMillis ON draw_periods(status, createdAtEpochMillis)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_draw_periods_single_prepared ON draw_periods(status) WHERE status = 'PREPARED'")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_lines_period_number_category ON sale_lines(periodId, numberText, betCategory)")
            val sourceChecksExist = db.query("SELECT 1 FROM sqlite_master WHERE type='table' AND name='draw_source_checks'").use { it.moveToFirst() }
            if (sourceChecksExist) {
                db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_draw_source_check_update BEFORE UPDATE ON draw_source_checks BEGIN SELECT RAISE(ABORT, 'source check immutable'); END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS prevent_draw_source_check_delete BEFORE DELETE ON draw_source_checks BEGIN SELECT RAISE(ABORT, 'source check immutable'); END")
            }
        }

        fun closeForRestore() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        fun getInstance(context: Context): LottoDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                LottoDatabase::class.java,
                "lotto-local.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
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
