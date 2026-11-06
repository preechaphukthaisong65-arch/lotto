package th.co.lotto

import th.co.lotto.data.DrawResultEntity
import th.co.lotto.data.SaleLineEntity

data class WinningLineMatch(
    val resultId: String,
    val resultType: String,
    val winningNumber: String,
    val betCategory: String,
    val lines: List<SaleLineEntity>
)

/**
 * Deterministic matching only. This deliberately does not calculate payouts:
 * The official GLO three-digit top result is the sole source; THREE_TOAD matches are derived by permutation.
 */
object DrawResultMatcher {
    private val aliases = mapOf(
        "THREE_TOP" to "THREE_TOP",
        "3 ตัวบน" to "THREE_TOP",
        "3บน" to "THREE_TOP",
    )

    fun findMatches(
        results: List<DrawResultEntity>,
        saleLines: List<SaleLineEntity>
    ): List<WinningLineMatch> = results.flatMap { result ->
        val category = aliases[result.resultType.trim().uppercase(java.util.Locale.ROOT)]
            ?: aliases[result.resultType.trim()]
            ?: return@flatMap emptyList()
        val expectedLength = 3
        if (result.winningNumber.length != expectedLength ||
            !result.winningNumber.all { it in '0'..'9' }) return@flatMap emptyList()

        val categoriesToCheck = when (category) {
            "THREE_TOP" -> listOf("THREE_TOP", "THREE_TOAD")
            else -> emptyList()
        }
        categoriesToCheck.mapNotNull { betCategory ->
            val matches = saleLines.filter { line ->
                if (line.periodId != result.periodId || line.betCategory != betCategory) {
                    false
                } else if (betCategory == "THREE_TOAD" && category == "THREE_TOP") {
                    line.numberText.length == 3 &&
                        line.numberText.all { it in '0'..'9' } &&
                        line.numberText.toCharArray().sorted() == result.winningNumber.toCharArray().sorted()
                } else {
                    line.numberText == result.winningNumber
                }
            }
            if (matches.isEmpty()) null else WinningLineMatch(
                result.resultId, result.resultType, result.winningNumber, betCategory, matches
            )
        }
    }
}
