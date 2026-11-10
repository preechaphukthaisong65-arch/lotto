package com.lotto.app.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Test

class PrizeExposurePolicyTest {
    @Test
    fun includesStraightAndTodWhenExactResultMatchesBoth() {
        val liability = PrizeExposurePolicy.maximumPossiblePrizeLiability(
            listOf(
                PrizeExposureEntry("123", PrizeType.THREE_DIGIT_STRAIGHT, 10, 500),
                PrizeExposureEntry("123", PrizeType.THREE_DIGIT_TOD, 10, 100)
            )
        )
        assertEquals(6_000L, liability)
    }

    @Test
    fun includesAllMatchingTodPermutationsAndDuplicateDigitRules() {
        val liability = PrizeExposurePolicy.maximumPossiblePrizeLiability(
            listOf(
                PrizeExposureEntry("112", PrizeType.THREE_DIGIT_TOD, 10, 100),
                PrizeExposureEntry("121", PrizeType.THREE_DIGIT_TOD, 20, 100),
                PrizeExposureEntry("122", PrizeType.THREE_DIGIT_TOD, 30, 100)
            )
        )
        // Result 112 matches 112 and 121, but not 122.
        assertEquals(3_000L, liability)
    }

    @Test
    fun combinesTwoDigitTopWithBestThreeDigitResultAndBottomIndependently() {
        val liability = PrizeExposurePolicy.maximumPossiblePrizeLiability(
            listOf(
                PrizeExposureEntry("123", PrizeType.THREE_DIGIT_STRAIGHT, 2, 500),
                PrizeExposureEntry("99", PrizeType.TWO_DIGIT_TOP, 3, 70),
                PrizeExposureEntry("99", PrizeType.TWO_DIGIT_BOTTOM, 4, 35)
            )
        )
        assertEquals(1_000L + 210L + 140L, liability)
    }

    @Test(expected = ArithmeticException::class)
    fun rejectsOverflowInsteadOfWrappingLiability() {
        PrizeExposurePolicy.maximumPossiblePrizeLiability(
            listOf(
                PrizeExposureEntry("000", PrizeType.THREE_DIGIT_STRAIGHT, Long.MAX_VALUE, 500)
            )
        )
    }
}
