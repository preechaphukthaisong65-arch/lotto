package th.co.lotto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import th.co.lotto.data.DrawResultEntity
import th.co.lotto.data.SaleLineEntity

class DrawResultMatcherTest {
    @Test
    fun matchesOnlyThreeDigitTopAndToadCategories() {
        val results = listOf(
            DrawResultEntity("r1", "p1", "THREE_TOP", "123", "GLO", null, 1L),
        )
        val lines = listOf(
            SaleLineEntity("l1", "d1", "p1", "123", "THREE_TOP", 1_000L, false),
            SaleLineEntity("l2", "d2", "p1", "321", "THREE_TOAD", 2_000L, true),
            SaleLineEntity("l3", "d3", "p2", "123", "THREE_TOP", 4_000L, false)
        )

        val matches = DrawResultMatcher.findMatches(results, lines)

        assertEquals(2, matches.size)
        assertEquals("THREE_TOP", matches[0].betCategory)
        assertEquals(listOf("l1"), matches[0].lines.map { it.lineId })
        assertEquals("THREE_TOAD", matches[1].betCategory)
        assertEquals(listOf("l2"), matches[1].lines.map { it.lineId })
    }

    @Test
    fun threeDigitTopResultMatchesToadByDigitPermutation() {
        val result = DrawResultEntity("r-top", "p1", "THREE_TOP", "123", "GLO", null, 1L)
        val lines = listOf(
            SaleLineEntity("straight", "d1", "p1", "123", "THREE_TOP", 1_000L, false),
            SaleLineEntity("toad-1", "d2", "p1", "321", "THREE_TOAD", 2_000L, false),
            SaleLineEntity("toad-2", "d3", "p1", "132", "THREE_TOAD", 3_000L, true),
            SaleLineEntity("wrong", "d4", "p1", "124", "THREE_TOAD", 4_000L, false),
            SaleLineEntity("other-period", "d5", "p2", "213", "THREE_TOAD", 5_000L, false)
        )

        val matches = DrawResultMatcher.findMatches(listOf(result), lines)

        assertEquals(2, matches.size)
        assertEquals(listOf("straight"), matches.first { it.betCategory == "THREE_TOP" }.lines.map { it.lineId })
        assertEquals(setOf("toad-1", "toad-2"), matches.first { it.betCategory == "THREE_TOAD" }.lines.map { it.lineId }.toSet())
    }

    @Test
    fun unknownOrWrongLengthResultTypeIsNotMatched() {
        val results = listOf(
            DrawResultEntity("r1", "p1", "unknown", "123", "GLO", null, 1L),
            DrawResultEntity("r2", "p1", "THREE_TOP", "12", "GLO", null, 1L)
        )
        assertTrue(DrawResultMatcher.findMatches(results, emptyList()).isEmpty())
    }
}
