package th.co.lotto.data

import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SaleCancellationDaoTest {
    private var database: LottoDatabase? = null

    private fun createDatabase(): LottoDao {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, LottoDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    LottoDatabase.installIntegrityGuards(db)
                }
            })
            .build()
        return database!!.lottoDao()
    }

    @After
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun cancellationIsAuditedAndExcludedFromActiveSalesAndLimits() {
        val dao = createDatabase()
        val now = System.currentTimeMillis()
        val periodId = "period-1"
        dao.insertPeriod(DrawPeriodEntity(periodId, "งวดทดสอบ", now + 86_400_000L, "OPEN", null, now, null, now))

        val documentId = "sale-1"
        val document = SaleDocumentEntity(documentId, periodId, null, "CASH", 10_000L, now, "Asia/Bangkok")
        val line = SaleLineEntity("line-1", documentId, periodId, "123", "THREE_TOP", 10_000L, false)
        dao.postSale(
            document,
            listOf(line),
            AuditEventEntity("audit-sale", "SALE_DOCUMENT", documentId, "POSTED", "test sale", now)
        )

        assertEquals(10_000L, dao.getSoldAmountForNumber(periodId, "123", "THREE_TOP"))
        val cancellation = SaleCancellationEntity("cancel-1", documentId, 10_000L, "บันทึกเลขผิด", now + 1_000L)
        dao.cancelSaleDocument(
            cancellation,
            AuditEventEntity("audit-cancel", "SALE_CANCELLATION", "cancel-1", "CANCELLED", "test cancellation", now + 1_000L)
        )

        assertNotNull(dao.getSaleCancellation(documentId))
        assertEquals(1, dao.getSaleCancellationsForPeriod(periodId).size)
        assertEquals(0L, dao.getSoldAmountForNumber(periodId, "123", "THREE_TOP"))
        assertEquals(0, dao.getSalesForPeriod(periodId).size)
        assertEquals(1, dao.searchSales(periodId, "").size)
        assertEquals("บันทึกเลขผิด", dao.getSaleCancellation(documentId)?.reason)
        assertNotNull(dao.getAuditEvents("cancel-1").firstOrNull())
        assertThrows(SQLiteException::class.java) {
            database!!.openHelper.writableDatabase.execSQL(
                "UPDATE sale_cancellations SET reason = 'แก้ไข' WHERE cancellationId = 'cancel-1'"
            )
        }
        assertThrows(SQLiteException::class.java) {
            database!!.openHelper.writableDatabase.execSQL(
                "DELETE FROM sale_cancellations WHERE cancellationId = 'cancel-1'"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            dao.cancelSaleDocument(
                cancellation.copy(cancellationId = "cancel-2"),
                AuditEventEntity("audit-cancel-2", "SALE_CANCELLATION", "cancel-2", "CANCELLED", "duplicate", now + 2_000L)
            )
        }
    }

    @Test
    fun cancelledUnpaidCreditBillIsRemovedFromCustomerBalance() {
        val dao = createDatabase()
        val now = System.currentTimeMillis()
        val periodId = "period-credit-cancel"
        dao.insertPeriod(DrawPeriodEntity(periodId, "งวดยกเลิกเครดิต", now + 86_400_000L, "OPEN", null, now, null, now))
        val customerId = "customer-cancel"
        dao.postCustomer(
            CustomerEntity(customerId, "ลูกค้าเครดิต", "0891234567", "ข้อมูลยืนยัน", "CREDIT", now),
            AuditEventEntity("audit-customer-cancel", "CUSTOMER", customerId, "CREATED", "test", now)
        )
        val documentId = "credit-cancelled"
        dao.postSale(
            SaleDocumentEntity(documentId, periodId, customerId, "CREDIT", 7_500L, now, "Asia/Bangkok"),
            listOf(SaleLineEntity("credit-line-cancelled", documentId, periodId, "789", "THREE_TOP", 7_500L, false)),
            AuditEventEntity("audit-credit-cancelled", "SALE_DOCUMENT", documentId, "POSTED", "test", now)
        )
        dao.cancelSaleDocument(
            SaleCancellationEntity("cancel-unpaid-credit", documentId, 7_500L, "ลูกค้าเปลี่ยนใจ", now + 1_000L),
            AuditEventEntity("audit-cancel-unpaid-credit", "SALE_CANCELLATION", "cancel-unpaid-credit", "CANCELLED", "test", now + 1_000L)
        )

        assertEquals(0L, dao.getCreditSalesTotal(customerId))
        assertEquals(0, dao.getCreditDocumentsForCustomer(customerId).size)
        assertThrows(IllegalArgumentException::class.java) {
            dao.postPayment(
                PaymentEntryEntity("payment-after-cancel", documentId, customerId, 1_000L, now + 2_000L, "CASH", null),
                AuditEventEntity("audit-payment-after-cancel", "PAYMENT", "payment-after-cancel", "POSTED", "test", now + 2_000L)
            )
        }
    }

    @Test
    fun databaseAcceptsBothTwoDigitCategoriesAndRejectsUnsupportedCategoriesOrLengths() {
        val dao = createDatabase()
        val now = System.currentTimeMillis()
        val periodId = "period-category"
        dao.insertPeriod(DrawPeriodEntity(periodId, "งวดประเภท", now + 86_400_000L, "OPEN", null, now, null, now))

        val validLines = listOf(
            SaleLineEntity("valid-top-line", "valid-top-sale", periodId, "05", "TWO_TOP", 1_000L, false),
            SaleLineEntity("valid-bottom-line", "valid-bottom-sale", periodId, "01", "TWO_BOTTOM", 1_000L, false)
        )
        validLines.forEachIndexed { index, line ->
            val documentId = if (index == 0) "valid-top-sale" else "valid-bottom-sale"
            dao.postSale(
                SaleDocumentEntity(documentId, periodId, null, "CASH", 1_000L, now, "Asia/Bangkok"),
                listOf(line),
                AuditEventEntity("valid-audit-$index", "SALE_DOCUMENT", documentId, "POSTED", "valid two-digit category", now)
            )
        }

        assertEquals(2, dao.searchSales(periodId, "").size)
        assertEquals(1_000L, dao.getSoldAmountForNumber(periodId, "05", "TWO_TOP"))
        assertEquals(1_000L, dao.getSoldAmountForNumber(periodId, "01", "TWO_BOTTOM"))

        val invalidLines = listOf(
            SaleLineEntity("bad-length", "bad-sale-length", periodId, "123", "TWO_TOP", 1_000L, false),
            SaleLineEntity("bad-category", "bad-sale-category", periodId, "123", "THREE_BOTTOM", 1_000L, false)
        )
        invalidLines.forEachIndexed { index, line ->
            val documentId = if (index == 0) "bad-sale-length" else "bad-sale-category"
            assertThrows(IllegalArgumentException::class.java) {
                dao.postSale(
                    SaleDocumentEntity(documentId, periodId, null, "CASH", 1_000L, now, "Asia/Bangkok"),
                    listOf(line),
                    AuditEventEntity("bad-audit-$index", "SALE_DOCUMENT", documentId, "POSTED", "invalid test", now)
                )
            }
        }
    }

    @Test
    fun billWithPostedCreditPaymentCannotBeCancelled() {
        val dao = createDatabase()
        val now = System.currentTimeMillis()
        val periodId = "period-credit"
        dao.insertPeriod(DrawPeriodEntity(periodId, "งวดเครดิต", now + 86_400_000L, "OPEN", null, now, null, now))
        val customerId = "customer-1"
        dao.postCustomer(
            CustomerEntity(customerId, "ลูกค้าทดสอบ", "0812345678", "ข้อมูลยืนยัน", "CREDIT", now),
            AuditEventEntity("audit-customer", "CUSTOMER", customerId, "CREATED", "test customer", now)
        )
        val documentId = "credit-sale"
        dao.postSale(
            SaleDocumentEntity(documentId, periodId, customerId, "CREDIT", 5_000L, now, "Asia/Bangkok"),
            listOf(SaleLineEntity("credit-line", documentId, periodId, "456", "THREE_TOAD", 5_000L, false)),
            AuditEventEntity("audit-credit-sale", "SALE_DOCUMENT", documentId, "POSTED", "test credit sale", now)
        )
        dao.postPayment(
            PaymentEntryEntity("payment-1", documentId, customerId, 1_000L, now + 500L, "CASH", null),
            AuditEventEntity("audit-payment", "PAYMENT", "payment-1", "POSTED", "test payment", now + 500L)
        )

        assertThrows(IllegalArgumentException::class.java) {
            dao.cancelSaleDocument(
                SaleCancellationEntity("cancel-credit", documentId, 5_000L, "ต้องการยกเลิก", now + 1_000L),
                AuditEventEntity("audit-cancel-credit", "SALE_CANCELLATION", "cancel-credit", "CANCELLED", "test", now + 1_000L)
            )
        }
        assertNull(dao.getSaleCancellation(documentId))
        assertEquals(5_000L, dao.getCreditSalesTotal(customerId))
    }
}
