package th.co.lotto.data

import th.co.lotto.PeriodSchedule

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
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class LottoDatabaseIntegrityTest {
    // Test fixtures updated for the supported three-digit sale categories.
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
    fun drawResultNumberLengthMustMatchSupportedResultCategory() {
        dao.insertPeriod(period("period-result-validation"))
        database.openHelper.writableDatabase.execSQL("UPDATE draw_periods SET status = 'CLOSED' WHERE periodId = 'period-result-validation'")
        val invalid = DrawResultEntity("bad-result", "period-result-validation", "THREE_TOP", "12", "GLO", null, 1L)
        val audit = AuditEventEntity("bad-result-audit", "DRAW_RESULT", "bad-result", "POSTED", "test", 1L)
        assertThrows(IllegalArgumentException::class.java) { dao.postDrawResult(invalid, audit) }
        assertEquals(0, dao.getDrawResults("period-result-validation").size)
    }

    @Test
    fun settlementSnapshotRequiresClosedPeriodAndIsImmutable() {
        dao.insertPeriod(period("period-settlement"))
        val settings = PayoutRateSettingsEntity(1, 7_000L, 50_000L, 50_000L, 10_000L, 1L, 1L)
        dao.insertPayoutRateSettings(settings)
        val openSnapshot = SettlementSnapshotEntity("settlement-open", "period-settlement",
            7_000L, 50_000L, 50_000L, 10_000L, 1L, 2L)
        val openAudit = AuditEventEntity("settlement-open-audit", "SETTLEMENT", "settlement-open", "SNAPSHOTTED", "test", 2L)
        assertThrows(IllegalArgumentException::class.java) {
            dao.createSettlementSnapshot(openSnapshot, openAudit)
        }
        database.openHelper.writableDatabase.execSQL("UPDATE draw_periods SET status = 'CLOSED' WHERE periodId = 'period-settlement'")
        dao.postDrawResult(
            DrawResultEntity("settlement-result", "period-settlement", "THREE_TOP", "123", "GLO", 3L, 3L),
            AuditEventEntity("settlement-result-audit", "DRAW_RESULT", "settlement-result", "POSTED", "test", 3L)
        )
        dao.createSettlementSnapshot(openSnapshot, openAudit)
        assertEquals(1L, dao.getSettlementSnapshot("period-settlement")?.payoutConfigVersion)
        val changedSettings = settings.copy(
            twoDigitRateHundredths = 8_000L,
            configVersion = 1L,
            updatedAtEpochMillis = 4L
        )
        dao.savePayoutRateSettings(changedSettings,
            AuditEventEntity("settings-update-audit", "PAYOUT_SETTINGS", "1", "UPDATED", "test", 4L))
        assertEquals(7_000L, dao.getSettlementSnapshot("period-settlement")?.twoDigitRateHundredths)
        assertEquals(8_000L, dao.getPayoutRateSettings()?.twoDigitRateHundredths)
        assertThrows(IllegalArgumentException::class.java) {
            dao.createSettlementSnapshot(openSnapshot.copy(settlementId = "duplicate"),
                openAudit.copy(eventId = "duplicate-audit", entityId = "duplicate"))
        }
        assertThrows(SQLiteException::class.java) {
            database.openHelper.writableDatabase.execSQL("UPDATE settlement_snapshots SET twoDigitRateHundredths = 1 WHERE settlementId = 'settlement-open'")
        }
        assertThrows(SQLiteException::class.java) {
            database.openHelper.writableDatabase.execSQL("DELETE FROM settlement_snapshots WHERE settlementId = 'settlement-open'")
        }
    }

    @Test
    fun paymentHistoryIsAppendOnlyAndCanBeReadPerCreditDocument() {
        dao.insertPeriod(period("period-payment"))
        dao.insertCustomer(CustomerEntity("credit-payment", "Credit customer", "0800000000", "verified", "CREDIT", 1L))
        val document = SaleDocumentEntity("credit-document", "period-payment", "credit-payment", "CREDIT", 5_000L, 2L, "Asia/Bangkok")
        dao.insertSaleDocument(document)
        val payment = PaymentEntryEntity("payment-history-1", document.documentId, "credit-payment", 2_000L, 3L, "CASH", "first")
        dao.postPayment(payment, AuditEventEntity("payment-audit-1", "PAYMENT", payment.paymentId, "POSTED", "test", 3L))
        assertEquals(listOf(payment), dao.getPaymentsForDocument(document.documentId))
        assertEquals(1, dao.paymentExists(payment.paymentId))
        assertThrows(IllegalArgumentException::class.java) {
            dao.postPayment(payment.copy(paymentId = "payment-history-2", amountSatang = 4_000L),
                AuditEventEntity("payment-audit-2", "PAYMENT", "payment-history-2", "POSTED", "test", 4L))
        }
        assertEquals(1, dao.getPaymentsForDocument(document.documentId).size)
    }

    @Test
    fun creditCustomerRequiresCompleteIdentityAndCreationIsAuditedAtomically() {
        val incomplete = CustomerEntity("credit-bad", "Credit customer", "123", null, "CREDIT", 1L)
        val incompleteAudit = AuditEventEntity("customer-bad-event", "CUSTOMER", "credit-bad", "CREATED", "test", 1L)
        assertThrows(IllegalArgumentException::class.java) {
            dao.postCustomer(incomplete, incompleteAudit)
        }
        assertEquals(null, dao.getCustomer("credit-bad"))
        assertEquals(0, dao.getAuditEvents("credit-bad").size)

        val customer = CustomerEntity("credit-good", "Credit customer", "0800000000", "verified", "CREDIT", 2L)
        val audit = AuditEventEntity("customer-good-event", "CUSTOMER", "credit-good", "CREATED", "test", 2L)
        dao.postCustomer(customer, audit)

        assertEquals(customer, dao.getCustomer("credit-good"))
        assertEquals(listOf("CREATED"), dao.getAuditEvents("credit-good").map { it.action })
        assertEquals(listOf("credit-good"), dao.searchCustomers("CREDIT", "0800000000").map { it.customerId })
        assertEquals(listOf("credit-good"), dao.searchCustomers("CREDIT", "verified").map { it.customerId })
    }

    @Test
    fun customerAuditMismatchRollsBackCustomerCreation() {
        val customer = CustomerEntity("cash-1", "Cash customer", null, null, "CASH", 2L)
        val wrongAudit = AuditEventEntity("wrong-event", "CUSTOMER", "other-id", "CREATED", "test", 2L)
        assertThrows(IllegalArgumentException::class.java) {
            dao.postCustomer(customer, wrongAudit)
        }
        assertEquals(null, dao.getCustomer("cash-1"))
        assertEquals(0, dao.getAuditEvents("cash-1").size)
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
                "rule-1", "period-1", 3, "THREE_TOP", "123", 1_000L, true
            )
        )

        val document = SaleDocumentEntity(
            "sale-1", "period-1", null, "CASH", 1_200L, 100L, "Asia/Bangkok"
        )
        val lines = listOf(
            SaleLineEntity("line-1", "sale-1", "period-1", "123", "THREE_TOP", 600L, false),
            SaleLineEntity("line-2", "sale-1", "period-1", "123", "THREE_TOP", 600L, false)
        )
        val audit = AuditEventEntity(
            "event-1", "SALE_DOCUMENT", "sale-1", "POSTED", "test", 100L
        )

        assertThrows(IllegalArgumentException::class.java) {
            dao.postSale(document, lines, audit)
        }
        assertEquals(0, dao.documentExists("sale-1"))
        assertEquals(0L, dao.getSoldAmountForNumber("period-1", "123", "THREE_TOP"))
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
    fun drawResultsRequireClosedPeriodAndAreImmutableWithAudit() {
        dao.insertPeriod(period("period-1"))
        val openResult = DrawResultEntity(
            "result-open", "period-1", "TWO_BOTTOM", "12", "manual", null, 10L
        )
        val openAudit = AuditEventEntity(
            "result-open-event", "DRAW_RESULT", "result-open", "POSTED", "test", 10L
        )
        assertThrows(IllegalArgumentException::class.java) {
            dao.postDrawResult(openResult, openAudit)
        }
        database.openHelper.writableDatabase.execSQL("UPDATE draw_periods SET status = 'CLOSED' WHERE periodId = 'period-1'")
        val result = openResult.copy(resultId = "result-closed", verifiedAtEpochMillis = 20L, importedAtEpochMillis = 20L)
        val audit = openAudit.copy(eventId = "result-event", entityId = "result-closed", createdAtEpochMillis = 20L)
        dao.postDrawResult(result, audit)
        assertEquals(1, dao.getDrawResults("period-1").size)
        assertEquals(listOf("POSTED"), dao.getAuditEvents("result-closed").map { it.action })
        val sql = database.openHelper.writableDatabase
        assertThrows(SQLiteException::class.java) {
            sql.execSQL("UPDATE draw_results SET winningNumber = '99' WHERE resultId = 'result-closed'")
        }
        assertThrows(SQLiteException::class.java) {
            sql.execSQL("DELETE FROM draw_results WHERE resultId = 'result-closed'")
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
    fun halfPayoutIsResolvedFromPeriodRuleAndCannotBeChosenInSalePayload() {
        dao.insertPeriod(period("period-1"))
        dao.saveLimitRule(NumberLimitRuleEntity(
            "half-rule", "period-1", 2, "TWO_TOP", "12", 5_000L, true, true
        ))
        val halfDocument = SaleDocumentEntity(
            "sale-half", "period-1", null, "CASH", 1_000L, 10L, "Asia/Bangkok"
        )
        dao.postSale(
            halfDocument,
            listOf(SaleLineEntity("line-half", "sale-half", "period-1", "12", "TWO_TOP", 1_000L, false)),
            AuditEventEntity("event-half", "SALE_DOCUMENT", "sale-half", "POSTED", "test", 10L)
        )
        assertEquals(true, dao.getLinesForDocument("sale-half").single().halfPayout)

        val fullDocument = SaleDocumentEntity(
            "sale-full", "period-1", null, "CASH", 1_000L, 20L, "Asia/Bangkok"
        )
        dao.postSale(
            fullDocument,
            listOf(SaleLineEntity("line-full", "sale-full", "period-1", "13", "TWO_TOP", 1_000L, true)),
            AuditEventEntity("event-full", "SALE_DOCUMENT", "sale-full", "POSTED", "test", 20L)
        )
        assertEquals(false, dao.getLinesForDocument("sale-full").single().halfPayout)
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
        assertFalse(rules.single().halfPayout)
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

    @Test
    fun scheduleClosesCurrentPeriodAtFourteenAndOpensPreparedPeriodAtEighteen() {
        val zone = ZoneId.of("Asia/Bangkok")
        val drawDate = LocalDate.of(2026, 11, 6).atStartOfDay(zone).toInstant().toEpochMilli()
        val closeAt = PeriodSchedule.closeAt(drawDate, zone)
        val openAt = PeriodSchedule.openNextAt(drawDate, zone)
        dao.insertPeriod(DrawPeriodEntity(
            "period-current", "งวดปัจจุบัน", drawDate, "OPEN", null, 1L
        ))
        dao.insertPeriod(DrawPeriodEntity(
            "period-next", "งวดถัดไป",
            LocalDate.of(2026, 11, 16).atStartOfDay(zone).toInstant().toEpochMilli(),
            "PREPARED", null, 2L, openAt
        ))

        PeriodSchedule.advance(dao, closeAt, zone)
        assertEquals("CLOSED", dao.getPeriod("period-current")?.status)
        assertEquals(null, dao.getOpenPeriod())
        assertEquals("PREPARED", dao.getPeriod("period-next")?.status)

        PeriodSchedule.advance(dao, openAt, zone)
        assertEquals("OPEN", dao.getPeriod("period-next")?.status)
        assertEquals("period-next", dao.getOpenPeriod()?.periodId)
        assertEquals(closeAt, dao.getPeriod("period-current")?.salesCloseEpochMillis)
    }

    @Test
    fun overduePreparedPeriodCanCatchUpWhenAppReturnsAfterEighteen() {
        val zone = ZoneId.of("Asia/Bangkok")
        val drawDate = LocalDate.of(2026, 11, 6).atStartOfDay(zone).toInstant().toEpochMilli()
        val openAt = PeriodSchedule.openNextAt(drawDate, zone)
        dao.insertPeriod(DrawPeriodEntity(
            "period-next", "งวดถัดไป", null, "PREPARED", null, 1L, openAt
        ))

        PeriodSchedule.advance(dao, openAt + 60_000L, zone)

        assertEquals("period-next", dao.getOpenPeriod()?.periodId)
    }

    @Test
    fun blockedTwoDigitNumberRejectsBothTopAndBottomUntilUnblocked() {
        dao.insertPeriod(period("period-1"))
        dao.setNumberBlocked("block-00", "period-1", "TWO_DIGIT", "00", true, 10L, "block-event")
        val top = SaleDocumentEntity("sale-top", "period-1", null, "CASH", 100L, 11L, "Asia/Bangkok")
        assertThrows(IllegalArgumentException::class.java) {
            dao.postSale(top,
                listOf(SaleLineEntity("line-top", "sale-top", "period-1", "00", "TWO_TOP", 100L, false)),
                AuditEventEntity("sale-event-top", "SALE_DOCUMENT", "sale-top", "POSTED", "test", 11L))
        }
        val bottom = SaleDocumentEntity("sale-bottom", "period-1", null, "CASH", 100L, 12L, "Asia/Bangkok")
        assertThrows(IllegalArgumentException::class.java) {
            dao.postSale(bottom,
                listOf(SaleLineEntity("line-bottom", "sale-bottom", "period-1", "00", "TWO_BOTTOM", 100L, false)),
                AuditEventEntity("sale-event-bottom", "SALE_DOCUMENT", "sale-bottom", "POSTED", "test", 12L))
        }

        dao.setNumberBlocked("block-00", "period-1", "TWO_DIGIT", "00", false, 13L, "unblock-event")
        dao.postSale(bottom,
            listOf(SaleLineEntity("line-bottom", "sale-bottom", "period-1", "00", "TWO_BOTTOM", 100L, false)),
            AuditEventEntity("sale-event-bottom", "SALE_DOCUMENT", "sale-bottom", "POSTED", "test", 14L))

        assertEquals(listOf("BLOCKED", "UNBLOCKED"), dao.getAuditEvents("block-00").map { it.action })
        assertEquals(1, dao.documentExists("sale-bottom"))
    }

    @Test
    fun threeDigitBlockedCategoriesAreIndependent() {
        dao.insertPeriod(period("period-1"))
        dao.setNumberBlocked("top-123", "period-1", "THREE_TOP", "123", true, 10L, "top-event")

        val top = SaleDocumentEntity("sale-top-123", "period-1", null, "CASH", 100L, 11L, "Asia/Bangkok")
        assertThrows(IllegalArgumentException::class.java) {
            dao.postSale(top,
                listOf(SaleLineEntity("line-top-123", "sale-top-123", "period-1", "123", "THREE_TOP", 100L, false)),
                AuditEventEntity("sale-event-top-123", "SALE_DOCUMENT", "sale-top-123", "POSTED", "test", 11L))
        }

        val bottom = SaleDocumentEntity("sale-bottom-123", "period-1", null, "CASH", 100L, 12L, "Asia/Bangkok")
        dao.postSale(bottom,
            listOf(SaleLineEntity("line-bottom-123", "sale-bottom-123", "period-1", "123", "THREE_BOTTOM", 100L, false)),
            AuditEventEntity("sale-event-bottom-123", "SALE_DOCUMENT", "sale-bottom-123", "POSTED", "test", 12L))

        val toad = SaleDocumentEntity("sale-toad-123", "period-1", null, "CASH", 100L, 13L, "Asia/Bangkok")
        dao.postSale(toad,
            listOf(SaleLineEntity("line-toad-123", "sale-toad-123", "period-1", "123", "THREE_TOAD", 100L, false)),
            AuditEventEntity("sale-event-toad-123", "SALE_DOCUMENT", "sale-toad-123", "POSTED", "test", 13L))

        dao.setNumberBlocked("toad-123", "period-1", "THREE_TOAD", "123", true, 14L, "toad-event")
        val blockedToad = SaleDocumentEntity("sale-toad-blocked", "period-1", null, "CASH", 100L, 15L, "Asia/Bangkok")
        assertThrows(IllegalArgumentException::class.java) {
            dao.postSale(blockedToad,
                listOf(SaleLineEntity("line-toad-blocked", "sale-toad-blocked", "period-1", "123", "THREE_TOAD", 100L, false)),
                AuditEventEntity("sale-event-toad-blocked", "SALE_DOCUMENT", "sale-toad-blocked", "POSTED", "test", 15L))
        }
        assertEquals(1, dao.documentExists("sale-bottom-123"))
        assertEquals(1, dao.documentExists("sale-toad-123"))
        assertEquals(0, dao.documentExists("sale-top-123"))
        assertEquals(0, dao.documentExists("sale-toad-blocked"))
    }

    @Test
    fun blockedNumbersAreIsolatedByPeriodAndCanBeConfiguredBeforeOpening() {
        dao.insertPeriod(period("period-1"))
        dao.insertPeriod(DrawPeriodEntity(
            "period-2", "prepared", null, "PREPARED", null, 2L, 100L
        ))
        dao.setNumberBlocked("block-12", "period-2", "TWO_DIGIT", "12", true, 3L, "block-event")
        assertEquals(1, dao.getActiveNumberBlockRules("period-2").size)
        assertEquals(0, dao.getActiveNumberBlockRules("period-1").size)

        val sale = SaleDocumentEntity("sale-12", "period-1", null, "CASH", 100L, 4L, "Asia/Bangkok")
        dao.postSale(sale,
            listOf(SaleLineEntity("line-12", "sale-12", "period-1", "12", "TWO_TOP", 100L, false)),
            AuditEventEntity("sale-event-12", "SALE_DOCUMENT", "sale-12", "POSTED", "test", 4L))
        assertEquals(1, dao.documentExists("sale-12"))
    }

    private fun period(id: String) = DrawPeriodEntity(
        id, id, null, "OPEN", null, 0L
    )
}
