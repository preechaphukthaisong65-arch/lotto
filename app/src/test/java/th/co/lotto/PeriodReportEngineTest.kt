package th.co.lotto

import org.junit.Assert.assertEquals
import org.junit.Test
import th.co.lotto.data.SaleDocumentEntity
import th.co.lotto.data.SaleLineEntity
import java.time.Instant
import java.time.ZoneId

class PeriodReportEngineTest {
    @Test
    fun calculatesSalesCreditPaymentsHalfPayoutAndDailyTotalsFromSourceRows() {
        val firstTime = Instant.parse("2026-11-06T02:00:00Z").toEpochMilli()
        val secondTime = Instant.parse("2026-11-06T04:00:00Z").toEpochMilli()
        val documents = listOf(
            SaleDocumentEntity("cash", "p1", null, "CASH", 2_000L, firstTime, "Asia/Bangkok"),
            SaleDocumentEntity("credit", "p1", "c1", "CREDIT", 3_000L, secondTime, "Asia/Bangkok"),
            SaleDocumentEntity("correction", "p1", null, "CASH", 500L, secondTime, "Asia/Bangkok", "cash"),
            SaleDocumentEntity("other-period", "p2", null, "CASH", 9_000L, firstTime, "Asia/Bangkok")
        )
        val lines = listOf(
            SaleLineEntity("l1", "cash", "p1", "12", "TWO_TOP", 2_000L, false),
            SaleLineEntity("l2", "credit", "p1", "123", "THREE_TOAD", 3_000L, true),
            SaleLineEntity("l3", "correction", "p1", "13", "TWO_BOTTOM", 500L, false),
            SaleLineEntity("l4", "other-period", "p2", "99", "TWO_TOP", 9_000L, false)
        )

        val report = PeriodReportEngine.calculate(
            "p1",
            documents,
            lines,
            mapOf("credit" to 1_000L),
            ZoneId.of("Asia/Bangkok")
        )

        assertEquals(3, report.documentCount)
        assertEquals(2, report.originalDocumentCount)
        assertEquals(1, report.correctionDocumentCount)
        assertEquals(5_500L, report.grossSalesSatang)
        assertEquals(2_500L, report.cashSalesSatang)
        assertEquals(3_000L, report.creditSalesSatang)
        assertEquals(1_000L, report.paymentsReceivedSatang)
        assertEquals(2_000L, report.outstandingCreditSatang)
        assertEquals(3, report.lineCount)
        assertEquals(3_000L, report.categoryTotalsSatang["THREE_TOAD"])
        assertEquals(1, report.halfPayoutLineCount)
        assertEquals(3_000L, report.halfPayoutAmountSatang)
        assertEquals(5_500L, report.dailyGrossSalesSatang.values.single())
    }

    @Test
    fun rejectsPaymentTotalsAboveCreditDocumentAmount() {
        val document = SaleDocumentEntity("credit", "p1", "c1", "CREDIT", 1_000L, 1L, "UTC")
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            PeriodReportEngine.calculate("p1", listOf(document), emptyList(), mapOf("credit" to 1_001L))
        }
    }
}
