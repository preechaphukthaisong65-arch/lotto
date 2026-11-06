package th.co.lotto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import th.co.lotto.data.DrawResultEntity
import th.co.lotto.data.SaleLineEntity

class DrawResultMatcherTest {
    @Test
    fun matchesOnlyExactNumberCategoryAndPeriod() {
        val results = listOf(
            DrawResultEntity("r1", "p1", "2 ตัวบน", "07", "manual", null, 1L),
            DrawResultEntity("r2", "p1", "THREE_TOAD", "123", "manual", null, 1L),
            DrawResultEntity("r3", "p1", "TWO_BOTTOM", "7", "manual", null, 1L)
        )
        val lines = listOf(
            SaleLineEntity("l1", "d1", "p1", "07", "TWO_TOP", 1_000L, false),
            SaleLineEntity("l2", "d2", "p1", "07", "TWO_BOTTOM", 2_000L, false),
            SaleLineEntity("l3", "d3", "p1", "123", "THREE_TOAD", 3_000L, true),
            SaleLineEntity("l4", "d4", "p2", "07", "TWO_TOP", 4_000L, false)
        )

        val matches = DrawResultMatcher.findMatches(results, lines)

        assertEquals(2, matches.size)
        assertEquals("TWO_TOP", matches[0].betCategory)
        assertEquals(listOf("l1"), matches[0].lines.map { it.lineId })
        assertEquals("THREE_TOAD", matches[1].betCategory)
        assertEquals(listOf("l3"), matches[1].lines.map { it.lineId })
    }

    @Test
    fun unknownOrWrongLengthResultTypeIsNotMatched() {
        val results = listOf(
            DrawResultEntity("r1", "p1", "unknown", "12", "manual", null, 1L),
            DrawResultEntity("r2", "p1", "TWO_TOP", "123", "manual", null, 1L)
        )
        assertTrue(DrawResultMatcher.findMatches(results, emptyList()).isEmpty())
    }
}
