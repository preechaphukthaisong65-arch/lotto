package com.lotto.app.domain.rules

data class PrizeExposureEntry(
    val number: String,
    val prizeType: PrizeType,
    val stakeAmount: Long,
    val payoutMultiplier: Long
)

object PrizeExposurePolicy {
    private fun sumExact(values: Iterable<Long>): Long =
        values.fold(0L) { total, value -> Math.addExact(total, value) }

    fun maximumPossiblePrizeLiability(entries: List<PrizeExposureEntry>): Long {
        entries.forEach {
            require(it.stakeAmount > 0L) { "ยอดแทงต้องมากกว่า 0 บาท" }
            require(it.payoutMultiplier > 0L) { "อัตราจ่ายต้องมากกว่า 0" }
            when (it.prizeType) {
                PrizeType.THREE_DIGIT_STRAIGHT, PrizeType.THREE_DIGIT_TOD ->
                    require(LottoRules.isValidThreeDigitNumber(it.number)) { "เลข 3 ตัวไม่ถูกต้อง" }
                PrizeType.TWO_DIGIT_TOP, PrizeType.TWO_DIGIT_BOTTOM ->
                    require(LottoRules.isValidTwoDigitNumber(it.number)) { "เลข 2 ตัวไม่ถูกต้อง" }
            }
        }

        val straight = entries.filter { it.prizeType == PrizeType.THREE_DIGIT_STRAIGHT }
            .groupBy { it.number }
            .mapValues { (_, rows) -> sumExact(rows.map { Math.multiplyExact(it.stakeAmount, it.payoutMultiplier) }) }
        val tod = entries.filter { it.prizeType == PrizeType.THREE_DIGIT_TOD }
            .groupBy { it.number }
            .mapValues { (_, rows) -> sumExact(rows.map { Math.multiplyExact(it.stakeAmount, it.payoutMultiplier) }) }
        val top = entries.filter { it.prizeType == PrizeType.TWO_DIGIT_TOP }
            .groupBy { it.number }
            .mapValues { (_, rows) -> sumExact(rows.map { Math.multiplyExact(it.stakeAmount, it.payoutMultiplier) }) }
        val bottom = entries.filter { it.prizeType == PrizeType.TWO_DIGIT_BOTTOM }
            .groupBy { it.number }
            .mapValues { (_, rows) -> sumExact(rows.map { Math.multiplyExact(it.stakeAmount, it.payoutMultiplier) }) }

        val maximumThreeDigitAndTop = (0..999).maxOf { value ->
            val result = value.toString().padStart(3, '0')
            val todLiability = sumExact(tod.filterKeys { number ->
                LottoRules.isThreeDigitTodMatch(number, result)
            }.values)
            sumExact(listOf(
                straight[result] ?: 0L,
                todLiability,
                top[result.takeLast(2)] ?: 0L
            ))
        }
        val maximumTwoDigitBottom = bottom.values.maxOrNull() ?: 0L
        return Math.addExact(maximumThreeDigitAndTop, maximumTwoDigitBottom)
    }
}
