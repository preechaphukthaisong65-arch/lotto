package th.co.lotto

import th.co.lotto.data.SaleLineEntity
import th.co.lotto.data.SettlementSnapshotEntity
import java.math.BigDecimal
import java.math.RoundingMode

data class WinningLinePayout(
    val resultId: String,
    val winningNumber: String,
    val line: SaleLineEntity,
    val payoutSatang: Long
)

data class PeriodPayoutSummary(
    val linePayouts: List<WinningLinePayout>,
    val totalPayoutSatang: Long,
    val grossSalesSatang: Long,
    val salesAfterPayoutSatang: Long
)

/**
 * Payout is calculated per winning sale line, rounded HALF_UP to the nearest satang.
 * Rates are stored in hundredths (70.00x = 7000). A half-payout line keeps its
 * full stake and divides the configured rate by two.
 */
object PayoutCalculator {
    fun calculateLinePayoutSatang(
        stakeSatang: Long,
        rateHundredths: Long,
        halfPayout: Boolean
    ): Long {
        require(stakeSatang >= 0L) { "Stake cannot be negative" }
        require(rateHundredths > 0L) { "Payout rate must be positive" }
        val denominator = if (halfPayout) 200L else 100L
        return BigDecimal.valueOf(stakeSatang)
            .multiply(BigDecimal.valueOf(rateHundredths))
            .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    fun calculatePeriod(
        matches: List<WinningLineMatch>,
        snapshot: SettlementSnapshotEntity,
        grossSalesSatang: Long
    ): PeriodPayoutSummary {
        require(grossSalesSatang >= 0L) { "Gross sales cannot be negative" }
        val linePayouts = matches.flatMap { match ->
            val rate = when (match.betCategory) {
                "THREE_TOP" -> snapshot.threeTopRateHundredths
                "THREE_TOAD" -> snapshot.threeToadRateHundredths
                else -> error("Unsupported payout category: ${match.betCategory}")
            }
            match.lines.map { line ->
                require(line.betCategory == match.betCategory) { "Matched line category mismatch" }
                WinningLinePayout(
                    resultId = match.resultId,
                    winningNumber = match.winningNumber,
                    line = line,
                    payoutSatang = calculateLinePayoutSatang(line.amountSatang, rate, line.halfPayout)
                )
            }
        }
        val totalPayout = linePayouts.fold(0L) { total, item ->
            Math.addExact(total, item.payoutSatang)
        }
        return PeriodPayoutSummary(
            linePayouts = linePayouts,
            totalPayoutSatang = totalPayout,
            grossSalesSatang = grossSalesSatang,
            salesAfterPayoutSatang = Math.subtractExact(grossSalesSatang, totalPayout)
        )
    }
}
