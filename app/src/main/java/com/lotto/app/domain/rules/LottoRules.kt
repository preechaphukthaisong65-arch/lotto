package com.lotto.app.domain.rules

/**
 * Central, UI-independent rules for prize matching and payout calculation.
 *
 * Keep lottery numbers as strings so leading zeroes (for example, "007" and "05")
 * are preserved.
 */
enum class PrizeType(
    val fullPayoutMultiplier: Long
) {
    THREE_DIGIT_STRAIGHT(500L),
    THREE_DIGIT_TOD(100L),
    TWO_DIGIT_TOP(70L),
    TWO_DIGIT_BOTTOM(70L)
}

enum class PayoutMode {
    FULL,
    HALF
}

object LottoRules {

    /**
     * Returns the agreed payout multiplier for a prize type and payout mode.
     *
     * FULL: 500, 100, 70, 70
     * HALF: 250, 50, 35, 35
     */
    fun payoutMultiplier(
        prizeType: PrizeType,
        payoutMode: PayoutMode = PayoutMode.FULL
    ): Long {
        val fullRate = prizeType.fullPayoutMultiplier
        return when (payoutMode) {
            PayoutMode.FULL -> fullRate
            PayoutMode.HALF -> fullRate / 2L
        }
    }

    /**
     * Calculates a prize from an integer-baht stake.
     * Throws if the stake is negative or the result exceeds Long capacity.
     */
    fun calculatePrize(
        stakeBaht: Long,
        prizeType: PrizeType,
        payoutMode: PayoutMode = PayoutMode.FULL
    ): Long {
        require(stakeBaht >= 0L) { "Stake must not be negative." }
        return Math.multiplyExact(stakeBaht, payoutMultiplier(prizeType, payoutMode))
    }

    /** True only when both values are exactly two ASCII digits. */
    fun isValidTwoDigitNumber(number: String): Boolean =
        number.length == 2 && number.all { it in '0'..'9' }

    /** True only when both values are exactly three ASCII digits. */
    fun isValidThreeDigitNumber(number: String): Boolean =
        number.length == 3 && number.all { it in '0'..'9' }

    /**
     * Two-digit matching is positional: "05" matches "05", not "50".
     */
    fun isTwoDigitMatch(betNumber: String, resultNumber: String): Boolean {
        return isValidTwoDigitNumber(betNumber) &&
            isValidTwoDigitNumber(resultNumber) &&
            betNumber == resultNumber
    }

    /**
     * Three-digit straight matching compares all three positions.
     */
    fun isThreeDigitStraightMatch(betNumber: String, resultNumber: String): Boolean {
        return isValidThreeDigitNumber(betNumber) &&
            isValidThreeDigitNumber(resultNumber) &&
            betNumber == resultNumber
    }

    /**
     * Three-digit TOD matching compares digit frequency, not position.
     * Duplicate digits are significant. For example, 112 matches 121 and 211,
     * but does not match 122 or 111.
     *
     * An exact result also matches TOD. This is intentional: straight and TOD
     * are evaluated as independent purchased entries under the agreed rules.
     */
    fun isThreeDigitTodMatch(betNumber: String, resultNumber: String): Boolean {
        if (!isValidThreeDigitNumber(betNumber) ||
            !isValidThreeDigitNumber(resultNumber)
        ) {
            return false
        }

        return betNumber.toCharArray().sorted() ==
            resultNumber.toCharArray().sorted()
    }
}
