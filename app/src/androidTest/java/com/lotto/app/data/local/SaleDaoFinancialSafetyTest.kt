package com.lotto.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lotto.app.data.local.entity.SaleEntity
import com.lotto.app.data.local.entity.SaleItemEntity
import com.lotto.app.domain.rules.PrizeType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class SaleDaoFinancialSafetyTest {
    private lateinit var database: LottoDatabase
    private val saleDao get() = database.saleDao()
    private val saleTime = Instant.parse("2026-11-10T03:00:00Z").toEpochMilli()
    private val drawDate = "2026-11-16"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LottoDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val sql = database.openHelper.writableDatabase
        sql.execSQL("INSERT INTO draw_dates (drawDate, referenceDate, source, note, updatedAt) VALUES ('2026-11-01', NULL, 'TEST', NULL, 1)")
        sql.execSQL("INSERT INTO draw_dates (drawDate, referenceDate, source, note, updatedAt) VALUES ('$drawDate', NULL, 'TEST', NULL, 1)")
        putSetting("risk_max_prize_liability_per_draw", "10000000")
        putSetting("risk_max_credit_per_customer", "150")
        putSetting("full_limit_${PrizeType.THREE_DIGIT_STRAIGHT.name}", "100000")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun cashSaleStillAllowsFirstNameOnlyCustomerLabel() {
        val saleId = saveSale(
            at = saleTime,
            firstName = "นิด",
            lastName = "",
            total = 10,
            cash = 10,
            credit = 0
        )
        val sale = kotlinx.coroutines.runBlocking { saleDao.getSaleById(saleId) }
        assertEquals("นิด", sale?.customerFirstName)
        assertEquals("", sale?.customerLastName)
        assertNotNull(sale?.customerId)
    }

    @Test
    fun cashSaleCanBeVoidedOnlyAfterCashRefundIsConfirmedAndAuditIsStored() {
        val saleId = saveSale(
            at = saleTime,
            firstName = "",
            lastName = "",
            total = 10,
            cash = 10,
            credit = 0
        )

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                saleDao.voidConfirmedSale(saleId, "กรอกเลขผิด", saleTime + 1_000, false)
            }
        }

        val event = kotlinx.coroutines.runBlocking {
            saleDao.voidConfirmedSale(saleId, "กรอกเลขผิด", saleTime + 2_000, true)
        }
        assertEquals(10L, event.cashRefundAmount)
        assertEquals(0L, event.cancelledCreditAmount)
        assertEquals("VOIDED", kotlinx.coroutines.runBlocking { saleDao.getSaleById(saleId) }?.status)
        assertEquals(1, kotlinx.coroutines.runBlocking { saleDao.getAllSaleVoidEvents() }.size)
    }

    @Test
    fun creditLimitIsAppliedToStableCustomerIdNotSameNameText() {
        val firstId = saveSale(
            at = saleTime,
            firstName = "สมชาย",
            lastName = "ใจดี",
            total = 100,
            cash = 0,
            credit = 100,
            newCustomerIdentityConfirmed = true
        )
        val customerId = kotlinx.coroutines.runBlocking { saleDao.getSaleById(firstId) }?.customerId
        assertNotNull(customerId)

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                saleDao.insertSaleWithItems(
                    SaleEntity(
                        customerFirstName = "สมชาย",
                        customerLastName = "ใจดี",
                        customerId = customerId,
                        totalAmount = 60,
                        cashAmount = 0,
                        creditAmount = 60,
                        createdAt = saleTime + 1_000,
                        drawDate = drawDate
                    ),
                    listOf(item(amount = 60))
                )
            }
        }
    }

    @Test
    fun newCreditSaleRequiresReviewWhenLegacySameNameDebtExists() {
        val legacySaleId = saveSale(
            at = saleTime,
            firstName = "สมชาย",
            lastName = "ใจดี",
            total = 100,
            cash = 0,
            credit = 100,
            newCustomerIdentityConfirmed = true
        )
        database.openHelper.writableDatabase.execSQL(
            "UPDATE credit_documents SET customerId = NULL WHERE sourceSaleId = ?",
            arrayOf(legacySaleId)
        )

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                saleDao.insertSaleWithItems(
                    SaleEntity(
                        customerFirstName = "สมชาย",
                        customerLastName = "ใจดี",
                        totalAmount = 20,
                        cashAmount = 0,
                        creditAmount = 20,
                        createdAt = saleTime + 1_000,
                        drawDate = drawDate,
                        newCustomerIdentityConfirmed = true
                    ),
                    listOf(item(amount = 20))
                )
            }
        }

        val acceptedId = kotlinx.coroutines.runBlocking {
            saleDao.insertSaleWithItems(
                SaleEntity(
                    customerFirstName = "สมชาย",
                    customerLastName = "ใจดี",
                    totalAmount = 20,
                    cashAmount = 0,
                    creditAmount = 20,
                    createdAt = saleTime + 2_000,
                    drawDate = drawDate,
                    legacyDebtReviewed = true,
                    newCustomerIdentityConfirmed = true
                ),
                listOf(item(amount = 20))
            )
        }
        assertNotNull(kotlinx.coroutines.runBlocking { saleDao.getSaleById(acceptedId) }?.customerId)
    }

    @Test
    fun twoDifferentCustomersWithTheSameNameKeepSeparateCreditBalances() {
        val firstSaleId = saveSale(
            at = saleTime,
            firstName = "สมชาย",
            lastName = "ใจดี",
            total = 100,
            cash = 0,
            credit = 100,
            newCustomerIdentityConfirmed = true
        )
        val secondSaleId = saveSale(
            at = saleTime + 1_000,
            firstName = "สมชาย",
            lastName = "ใจดี",
            total = 60,
            cash = 0,
            credit = 60,
            newCustomerIdentityConfirmed = true
        )
        val firstCustomerId = kotlinx.coroutines.runBlocking { saleDao.getSaleById(firstSaleId) }?.customerId
        val secondCustomerId = kotlinx.coroutines.runBlocking { saleDao.getSaleById(secondSaleId) }?.customerId
        assertNotNull(firstCustomerId)
        assertNotNull(secondCustomerId)
        org.junit.Assert.assertNotEquals(firstCustomerId, secondCustomerId)
        assertEquals(100L, kotlinx.coroutines.runBlocking {
            saleDao.getOutstandingCreditByCustomer(firstCustomerId!!)
        })
        assertEquals(60L, kotlinx.coroutines.runBlocking {
            saleDao.getOutstandingCreditByCustomer(secondCustomerId!!)
        })
    }

    @Test
    fun saleCannotBeVoidedAfterDrawResultHasBeenRecorded() {
        val saleId = saveSale(
            at = saleTime,
            firstName = "",
            lastName = "",
            total = 10,
            cash = 10,
            credit = 0
        )
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO draw_results (drawDate, threeDigit, twoDigitTop, twoDigitBottom, recordedAt, payoutLocked) VALUES (?, '999', '99', '99', ?, 0)",
            arrayOf(drawDate, Instant.parse("2026-11-16T09:00:00Z").toEpochMilli())
        )

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                saleDao.voidConfirmedSale(saleId, "ยกเลิก", saleTime + 2_000, true)
            }
        }
        assertEquals("CONFIRMED", kotlinx.coroutines.runBlocking { saleDao.getSaleById(saleId) }?.status)
    }

    private fun saveSale(
        at: Long,
        firstName: String,
        lastName: String,
        total: Long,
        cash: Long,
        credit: Long,
        newCustomerIdentityConfirmed: Boolean = false
    ): Long = kotlinx.coroutines.runBlocking {
        saleDao.insertSaleWithItems(
            SaleEntity(
                customerFirstName = firstName,
                customerLastName = lastName,
                totalAmount = total,
                cashAmount = cash,
                creditAmount = credit,
                newCustomerIdentityConfirmed = newCustomerIdentityConfirmed,
                createdAt = at,
                drawDate = drawDate
            ),
            listOf(item(amount = total))
        )
    }

    private fun item(amount: Long) = SaleItemEntity(
        saleId = 0,
        number = "123",
        prizeType = PrizeType.THREE_DIGIT_STRAIGHT.name,
        amount = amount,
        payoutMultiplier = PrizeType.THREE_DIGIT_STRAIGHT.fullPayoutMultiplier
    )

    private fun putSetting(key: String, value: String) {
        database.openHelper.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO app_settings (key, value, updatedAt) VALUES (?, ?, ?)",
            arrayOf(key, value, saleTime)
        )
    }
}
