package th.co.lotto

import th.co.lotto.data.SaleDocumentEntity
import th.co.lotto.data.SaleLineEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class PeriodReportSummary(
    val periodId: String,
    val documentCount: Int,
    val originalDocumentCount: Int,
    val correctionDocumentCount: Int,
    val grossSalesSatang: Long,
    val cashSalesSatang: Long,
    val creditSalesSatang: Long,
    val paymentsReceivedSatang: Long,
    val outstandingCreditSatang: Long,
    val lineCount: Int,
    val categoryTotalsSatang: Map<String, Long>,
    val halfPayoutLineCount: Int,
    val halfPayoutAmountSatang: Long,
    val dailyGrossSalesSatang: Map<String, Long>
)

/**
 * Builds descriptive reports from immutable source records.
 * Payouts, correction offsets, and net profit are intentionally excluded
 * until their accounting rules are approved in SRS 0.2.
 */
object PeriodReportEngine {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun calculate(
        periodId: String,
        documents: List<SaleDocumentEntity>,
        lines: List<SaleLineEntity>,
        paymentsByDocument: Map<String, Long>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): PeriodReportSummary {
        require(periodId.isNotBlank()) { "Period ID is required" }
        val periodDocuments = documents.filter { it.periodId == periodId }
        val documentIds = periodDocuments.map { it.documentId }.toSet()
        val periodLines = lines.filter { it.documentId in documentIds && it.periodId == periodId }

        fun sum(values: Iterable<Long>): Long =
            values.fold(0L) { total, value -> Math.addExact(total, value) }

        val gross = sum(periodDocuments.map { it.totalAmountSatang })
        val cash = sum(periodDocuments.filter { it.customerType == "CASH" }.map { it.totalAmountSatang })
        val creditDocuments = periodDocuments.filter { it.customerType == "CREDIT" }
        val credit = sum(creditDocuments.map { it.totalAmountSatang })
        val payments = sum(creditDocuments.map { paymentsByDocument[it.documentId] ?: 0L })
        val outstanding = sum(creditDocuments.map { document ->
            val paid = paymentsByDocument[document.documentId] ?: 0L
            require(paid >= 0L && paid <= document.totalAmountSatang) {
                "Payment total is invalid for document \${document.documentId}"
            }
            document.totalAmountSatang - paid
        })

        val categoryTotals = periodLines.groupBy { it.betCategory }
            .mapValues { (_, categoryLines) -> sum(categoryLines.map { it.amountSatang }) }
            .toSortedMap()
        val halfLines = periodLines.filter { it.halfPayout }
        val dailyTotals = periodDocuments.groupBy { document ->
            Instant.ofEpochMilli(document.createdAtEpochMillis).atZone(zoneId).toLocalDate().format(dateFormatter)
        }.mapValues { (_, dayDocuments) -> sum(dayDocuments.map { it.totalAmountSatang }) }
            .toSortedMap()

        return PeriodReportSummary(
            periodId = periodId,
            documentCount = periodDocuments.size,
            originalDocumentCount = periodDocuments.count { it.correctionOfDocumentId == null },
            correctionDocumentCount = periodDocuments.count { it.correctionOfDocumentId != null },
            grossSalesSatang = gross,
            cashSalesSatang = cash,
            creditSalesSatang = credit,
            paymentsReceivedSatang = payments,
            outstandingCreditSatang = outstanding,
            lineCount = periodLines.size,
            categoryTotalsSatang = categoryTotals,
            halfPayoutLineCount = halfLines.size,
            halfPayoutAmountSatang = sum(halfLines.map { it.amountSatang }),
            dailyGrossSalesSatang = dailyTotals
        )
    }
}
