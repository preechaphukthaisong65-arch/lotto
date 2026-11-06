package th.co.lotto.data

import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LottoDatabaseIntegrityTest {
    private lateinit var database: LottoDatabase
    private lateinit var dao: LottoDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            LottoDatabase::class.java
        )
            .allowMainThreadQueries()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    LottoDatabase.installIntegrityGuards(db)
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    LottoDatabase.installIntegrityGuards(db)
                }
            })
            .build()
        dao = database.lottoDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun onlyOneOpenPeriodCanExist() {
        dao.insertPeriod(period("period-1"))
        assertThrows(SQLiteException::class.java) {
            dao.insertPeriod(period("period-2"))
        }
        assertEquals(1, dao.getPeriods().size)
    }

    @Test
    fun saleLimitFailureRollsBackDocumentLinesAndAuditTogether() {
        dao.insertPeriod(period("period-1"))
        dao.insertLimitRule(
            NumberLimitRuleEntity(
                "rule-1", "period-1", 2, "TWO_TOP", "12", 1_000L, true
            )
        )

        val document = SaleDocumentEntity(
            "sale-1", "period-1", null, "CASH", 1_200L, 100L, "Asia/Bangkok"
        )
        val lines = listOf(
            SaleLineEntity("line-1", "sale-1", "period-1", "12", "TWO_TOP", 600L, false),
            SaleLineEntity("line-2", "sale-1", "period-1", "12", "TWO_TOP", 600L, false)
        )
        val audit = AuditEventEntity(
            "event-1", "SALE_DOCUMENT", "sale-1", "POSTED", "test", 100L
        )

        assertThrows(IllegalArgumentException::class.java) {
            dao.postSale(document, lines, audit)
        }
        assertEquals(0, dao.documentExists("sale-1"))
        assertEquals(0L, dao.getSoldAmountForNumber("period-1", "12", "TWO_TOP"))
        assertEquals(0, dao.getAuditEvents("sale-1").size)
    }

    @Test
    fun postedSaleAndPaymentRowsAreImmutableAtSqliteLevel() {
        dao.insertPeriod(period("period-1"))
        dao.insertCustomer(
            CustomerEntity("customer-1", "Credit customer", "0800000000", "verified", "CREDIT", 1L)
        )
        dao.insertSaleDocument(
            SaleDocumentEntity("sale-1", "period-1", "customer-1", "CREDIT", 2_000L, 2L, "Asia/Bangkok")
        )

        val sql = database.openHelper.writableDatabase
        assertThrows(SQLiteException::class.java) {
            sql.execSQL("UPDATE sale_documents SET totalAmountSatang = 1 WHERE documentId = 'sale-1'")
        }
        assertThrows(SQLiteException::class.java) {
            sql.execSQL("DELETE FROM sale_documents WHERE documentId = 'sale-1'")
        }

        dao.insertPayment(
            PaymentEntryEntity("payment-1", "sale-1", "customer-1", 500L, 3L, "CASH", null)
        )
        assertThrows(SQLiteException::class.java) {
            sql.execSQL("UPDATE payment_entries SET amountSatang = 1 WHERE paymentId = 'payment-1'")
        }
        assertThrows(SQLiteException::class.java) {
            sql.execSQL("DELETE FROM payment_entries WHERE paymentId = 'payment-1'")
        }
    }

    @Test
    fun saleHistoryCanSearchByDocumentOrNumberAndFilterByPeriod() {
        dao.insertPeriod(period("period-1"))
        val first = SaleDocumentEntity("document-alpha", "period-1", null, "CASH", 2_000L, 10L, "Asia/Bangkok")
        dao.postSale(first, listOf(SaleLineEntity("line-1", "document-alpha", "period-1", "12", "TWO_TOP", 2_000L, false)),
            AuditEventEntity("event-1", "SALE_DOCUMENT", "document-alpha", "POSTED", "test", 10L))
        database.openHelper.writableDatabase.execSQL("UPDATE draw_periods SET status = 'CLOSED' WHERE periodId = 'period-1'")
        dao.insertPeriod(period("period-2"))
        val second = SaleDocumentEntity("document-beta", "period-2", null, "CASH", 3_000L, 20L, "Asia/Bangkok")
        dao.postSale(second, listOf(SaleLineEntity("line-2", "document-beta", "period-2", "123", "THREE_TOAD", 3_000L, true)),
            AuditEventEntity("event-2", "SALE_DOCUMENT", "document-beta", "POSTED", "test", 20L))

        assertEquals(listOf("document-alpha"), dao.searchSales("period-1", "12").map { it.documentId })
        assertEquals(listOf("document-beta"), dao.searchSales(null, "123").map { it.documentId })
        assertEquals(listOf("document-alpha"), dao.searchSales(null, "alpha").map { it.documentId })
        assertEquals(2, dao.searchSales(null, "").size)
    }

    @Test
    fun linkedCorrectionCreatesNewDocumentAndPreservesOriginal() {
        dao.insertPeriod(period("period-1"))
        val original = SaleDocumentEntity("sale-original", "period-1", null, "CASH", 1_000L, 10L, "Asia/Bangkok")
        dao.postSale(original, listOf(SaleLineEntity("line-original", "sale-original", "period-1", "12", "TWO_TOP", 1_000L, false)),
            AuditEventEntity("event-original", "SALE_DOCUMENT", "sale-original", "POSTED", "test", 10L))

        val correction = SaleDocumentEntity("sale-correction", "period-1", null, "CASH", 2_000L, 20L,
            "Asia/Bangkok", "sale-original")
        dao.postSale(correction, listOf(SaleLineEntity("line-correction", "sale-correction", "period-1", "21", "TWO_BOTTOM", 2_000L, true)),
            AuditEventEntity("event-correction", "SALE_DOCUMENT", "sale-correction", "POSTED", "linked correction", 20L))

        assertEquals(1_000L, dao.getSaleDocument("sale-original")?.totalAmountSatang)
        assertEquals("sale-original", dao.getSaleDocument("sale-correction")?.correctionOfDocumentId)
        assertEquals(2, dao.searchSales("period-1", "").size)
    }

    @Test
    fun sqliteRejectsCorrectionWithoutOriginalDocument() {
        dao.insertPeriod(period("period-1"))
        val sql = database.openHelper.writableDatabase
        assertThrows(SQLiteException::class.java) {
            sql.execSQL("""
                INSERT INTO sale_documents
                (documentId, periodId, customerId, customerType, totalAmountSatang,
                 createdAtEpochMillis, deviceTimeZone, correctionOfDocumentId)
                VALUES ('sale-invalid', 'period-1', NULL, 'CASH', 100, 1, 'Asia/Bangkok', 'missing-original')
            """.trimIndent())
        }
        assertEquals(0, dao.documentExists("sale-invalid"))
    }

    @Test
    fun linkedCorrectionCannotReferenceAnotherPeriod() {
        dao.insertPeriod(period("period-1"))
        val original = SaleDocumentEntity("sale-original", "period-1", null, "CASH", 1_000L, 10L, "Asia/Bangkok")
        dao.postSale(original, listOf(SaleLineEntity("line-original", "sale-original", "period-1", "12", "TWO_TOP", 1_000L, false)),
            AuditEventEntity("event-original", "SALE_DOCUMENT", "sale-original", "POSTED", "test", 10L))
        database.openHelper.writableDatabase.execSQL("UPDATE draw_periods SET status = 'CLOSED' WHERE periodId = 'period-1'")
        dao.insertPeriod(period("period-2"))
        val correction = SaleDocumentEntity("sale-correction", "period-2", null, "CASH", 2_000L, 20L,
            "Asia/Bangkok", "sale-original")
        assertThrows(IllegalArgumentException::class.java) {
            dao.postSale(correction, listOf(SaleLineEntity("line-correction", "sale-correction", "period-2", "21", "TWO_BOTTOM", 2_000L, true)),
                AuditEventEntity("event-correction", "SALE_DOCUMENT", "sale-correction", "POSTED", "linked correction", 20L))
        }
        assertEquals(0, dao.documentExists("sale-correction"))
    }

    @Test
    fun savingSameLimitRuleUpdatesItInsteadOfCreatingDuplicates() {
        dao.insertPeriod(period("period-1"))
        dao.saveLimitRule(NumberLimitRuleEntity(
            "rule-original", "period-1", 2, "TWO_TOP", "12", 2_000L, true
        ))

        dao.saveLimitRule(NumberLimitRuleEntity(
            "rule-new-id", "period-1", 2, "TWO_TOP", "12", 3_000L, false
        ))

        val rules = dao.getLimitRules("period-1")
        assertEquals(1, rules.size)
        assertEquals("rule-original", rules.single().ruleId)
        assertEquals(3_000L, rules.single().maximumAmountSatang)
        assertFalse(rules.single().enabled)
    }

    @Test
    fun limitCannotBeReducedBelowAlreadySoldAmount() {
        dao.insertPeriod(period("period-1"))
        val rule = NumberLimitRuleEntity(
            "rule-1", "period-1", 2, "TWO_TOP", "12", 2_000L, true
        )
        dao.saveLimitRule(rule)
        val document = SaleDocumentEntity(
            "sale-1", "period-1", null, "CASH", 1_500L, 10L, "Asia/Bangkok"
        )
        dao.postSale(
            document,
            listOf(SaleLineEntity("line-1", "sale-1", "period-1", "12", "TWO_TOP", 1_500L, false)),
            AuditEventEntity("event-1", "SALE_DOCUMENT", "sale-1", "POSTED", "test", 10L)
        )

        assertThrows(IllegalArgumentException::class.java) {
            dao.saveLimitRule(rule.copy(maximumAmountSatang = 1_000L))
        }
        assertEquals(2_000L, dao.getLimitRules("period-1").single().maximumAmountSatang)
    }

    @Test
    fun disabledLimitDoesNotBlockSales() {
        dao.insertPeriod(period("period-1"))
        dao.saveLimitRule(NumberLimitRuleEntity(
            "rule-1", "period-1", 2, "TWO_TOP", "12", 1_000L, false
        ))
        val document = SaleDocumentEntity(
            "sale-1", "period-1", null, "CASH", 1_500L, 10L, "Asia/Bangkok"
        )

        dao.postSale(
            document,
            listOf(SaleLineEntity("line-1", "sale-1", "period-1", "12", "TWO_TOP", 1_500L, false)),
            AuditEventEntity("event-1", "SALE_DOCUMENT", "sale-1", "POSTED", "test", 10L)
        )

        assertEquals(1, dao.documentExists("sale-1"))
        assertEquals(1_500L, dao.getSoldAmountForNumber("period-1", "12", "TWO_TOP"))
    }

    private fun period(id: String) = DrawPeriodEntity(
        id, id, null, "OPEN", null, System.currentTimeMillis()
    )
}
