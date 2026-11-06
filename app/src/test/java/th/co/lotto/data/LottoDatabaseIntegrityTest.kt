package th.co.lotto.data

import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
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

    private fun period(id: String) = DrawPeriodEntity(
        id, id, null, "OPEN", null, System.currentTimeMillis()
    )
}
