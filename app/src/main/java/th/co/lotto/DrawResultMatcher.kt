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
 * half-payout precedence and accounting offsets remain unresolved requirements.
 */
object DrawResultMatcher {
    private val aliases = mapOf(
        "TWO_TOP" to "TWO_TOP",
        "2 ตัวบน" to "TWO_TOP",
        "2บน" to "TWO_TOP",
        "TWO_BOTTOM" to "TWO_BOTTOM",
        "2 ตัวล่าง" to "TWO_BOTTOM",
        "2ล่าง" to "TWO_BOTTOM",
        "THREE_TOP" to "THREE_TOP",
        "3 ตัวบน" to "THREE_TOP",
        "3บน" to "THREE_TOP",
        "THREE_BOTTOM" to "THREE_BOTTOM",
        "3 ตัวล่าง" to "THREE_BOTTOM",
        "3ล่าง" to "THREE_BOTTOM",
        "THREE_TOAD" to "THREE_TOAD",
        "3 ตัวโต๊ด" to "THREE_TOAD",
        "3โต๊ด" to "THREE_TOAD"
    )

    fun findMatches(
        results: List<DrawResultEntity>,
        saleLines: List<SaleLineEntity>
    ): List<WinningLineMatch> = results.flatMap { result ->
        val category = aliases[result.resultType.trim().uppercase(java.util.Locale.ROOT)]
            ?: aliases[result.resultType.trim()]
            ?: return@flatMap emptyList()
        val expectedLength = if (category.startsWith("TWO_")) 2 else 3
        if (result.winningNumber.length != expectedLength ||
            !result.winningNumber.all { it in '0'..'9' }) return@flatMap emptyList()

        val categoriesToCheck = when (category) {
            "THREE_TOP" -> listOf("THREE_TOP", "THREE_TOAD")
            else -> listOf(category)
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
