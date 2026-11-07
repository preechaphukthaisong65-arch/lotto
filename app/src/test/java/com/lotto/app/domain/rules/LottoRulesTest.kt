package com.lotto.app.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LottoRulesTest {
    @Test
    fun preservesLeadingZeroNumbers() {
        assertTrue(LottoRules.isValidThreeDigitNumber("007"))
        assertTrue(LottoRules.isValidTwoDigitNumber("05"))
        assertFalse(LottoRules.isValidThreeDigitNumber("07"))
        assertFalse(LottoRules.isValidTwoDigitNumber("5"))
    }

    @Test
    fun straightMatchIsPositional() {
        assertTrue(LottoRules.isThreeDigitStraightMatch("007", "007"))
        assertFalse(LottoRules.isThreeDigitStraightMatch("070", "007"))
    }

    @Test
    fun todMatchUsesDigitFrequencyIncludingDuplicates() {
        assertTrue(LottoRules.isThreeDigitTodMatch("112", "121"))
        assertTrue(LottoRules.isThreeDigitTodMatch("112", "211"))
        assertFalse(LottoRules.isThreeDigitTodMatch("112", "122"))
        assertFalse(LottoRules.isThreeDigitTodMatch("112", "111"))
    }

    @Test
    fun payoutRatesMatchAgreedFullAndHalfRules() {
        assertEquals(500L, LottoRules.payoutMultiplier(PrizeType.THREE_DIGIT_STRAIGHT))
        assertEquals(100L, LottoRules.payoutMultiplier(PrizeType.THREE_DIGIT_TOD))
        assertEquals(70L, LottoRules.payoutMultiplier(PrizeType.TWO_DIGIT_TOP))
        assertEquals(70L, LottoRules.payoutMultiplier(PrizeType.TWO_DIGIT_BOTTOM))
        assertEquals(250L, LottoRules.payoutMultiplier(PrizeType.THREE_DIGIT_STRAIGHT, PayoutMode.HALF))
        assertEquals(50L, LottoRules.payoutMultiplier(PrizeType.THREE_DIGIT_TOD, PayoutMode.HALF))
        assertEquals(35L, LottoRules.payoutMultiplier(PrizeType.TWO_DIGIT_TOP, PayoutMode.HALF))
        assertEquals(35L, LottoRules.payoutMultiplier(PrizeType.TWO_DIGIT_BOTTOM, PayoutMode.HALF))
    }

    @Test
    fun calculatesPrizeExactly() {
        assertEquals(50_000L, LottoRules.calculatePrize(100L, PrizeType.THREE_DIGIT_STRAIGHT))
        assertEquals(7_000L, LottoRules.calculatePrize(100L, PrizeType.TWO_DIGIT_TOP))
        assertEquals(2_500L, LottoRules.calculatePrize(100L, PrizeType.THREE_DIGIT_STRAIGHT, PayoutMode.HALF))
    }

    @Test(expected = ArithmeticException::class)
    fun rejectsPrizeOverflow() {
        LottoRules.calculatePrize(Long.MAX_VALUE, PrizeType.THREE_DIGIT_STRAIGHT)
    }
}
