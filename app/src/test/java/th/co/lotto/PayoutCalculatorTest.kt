package th.co.lotto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import th.co.lotto.data.SaleLineEntity
import th.co.lotto.data.SettlementSnapshotEntity

class PayoutCalculatorTest {
    @Test
    fun fullAndHalfPayoutKeepTheOriginalStakeAndApplyRateOrHalfRate() {
        assertEquals(700_000L, PayoutCalculator.calculateLinePayoutSatang(10_000L, 7_000L, false))
        assertEquals(350_000L, PayoutCalculator.calculateLinePayoutSatang(10_000L, 7_000L, true))
    }

    @Test
    fun roundsEachWinningLineHalfUpToTheNearestSatang() {
        assertEquals(706_013L, PayoutCalculator.calculateLinePayoutSatang(10_050L, 7_025L, false))
        assertEquals(353_006L, PayoutCalculator.calculateLinePayoutSatang(10_050L, 7_025L, true))
    }

    @Test
    fun calculatesSeparateRatesAndPeriodTotals() {
        val snapshot = SettlementSnapshotEntity(
            "snapshot-1", "period-1",
            7_000L, 50_000L, 50_000L, 10_000L, 1L, 10L
        )
        val topFull = SaleLineEntity("top-full", "d1", "period-1", "123", "THREE_TOP", 10_000L, false)
        val topHalf = SaleLineEntity("top-half", "d2", "period-1", "123", "THREE_TOP", 10_000L, true)
        val toadFull = SaleLineEntity("toad-full", "d3", "period-1", "321", "THREE_TOAD", 5_000L, false)
        val matches = listOf(
            WinningLineMatch("r1", "THREE_TOP", "123", "THREE_TOP", listOf(topFull, topHalf)),
            WinningLineMatch("r1", "THREE_TOP", "123", "THREE_TOAD", listOf(toadFull))
        )

        val summary = PayoutCalculator.calculatePeriod(matches, snapshot, 2_000_000L)

        assertEquals(3, summary.linePayouts.size)
        assertEquals(1_550_000L, summary.totalPayoutSatang)
        assertEquals(450_000L, summary.salesAfterPayoutSatang)
    }

    @Test
    fun rejectsNegativeStakeAndUnsupportedPayoutCategory() {
        assertThrows(IllegalArgumentException::class.java) {
            PayoutCalculator.calculateLinePayoutSatang(-1L, 7_000L, false)
        }
        val snapshot = SettlementSnapshotEntity("s", "p", 7_000L, 50_000L, 50_000L, 10_000L, 1L, 1L)
        val line = SaleLineEntity("line", "doc", "p", "123", "UNKNOWN", 100L, false)
        val match = WinningLineMatch("r", "THREE_TOP", "123", "UNKNOWN", listOf(line))
        assertThrows(IllegalStateException::class.java) {
            PayoutCalculator.calculatePeriod(listOf(match), snapshot, 100L)
        }
    }
}
