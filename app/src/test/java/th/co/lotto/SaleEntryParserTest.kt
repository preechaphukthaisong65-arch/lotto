package th.co.lotto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SaleEntryParserTest {
    @Test
    fun parsesAllFiveCategoriesAndPreservesLeadingZerosAndHalfMarkers() {
        val entries = SaleEntryParser.parse(
            """
            01,TWO_TOP,20,FULL
            01,TWO_BOTTOM,10.50,HALF
            001,THREE_TOP,5,FULL
            001,THREE_BOTTOM,6,HALF
            001,THREE_TOAD,7.25,FULL
            """.trimIndent()
        )

        assertEquals(5, entries.size)
        assertEquals("01", entries[0].number)
        assertEquals(2_000L, entries[0].amountSatang)
        assertFalse(entries[0].halfPayout)
        assertEquals(1_050L, entries[1].amountSatang)
        assertTrue(entries[1].halfPayout)
        assertEquals("001", entries[2].number)
        assertEquals(725L, entries[4].amountSatang)
        assertEquals(4_875L, SaleEntryParser.totalSatang(entries))
    }

    @Test
    fun ignoresBlankLinesAndAcceptsWholeBahtAmounts() {
        val entries = SaleEntryParser.parse("\n12,TWO_TOP,20,FULL\n\n123,THREE_TOAD,10,HALF\n")
        assertEquals(2, entries.size)
        assertEquals(3_000L, SaleEntryParser.totalSatang(entries))
    }

    @Test
    fun rejectsAmountsWithMoreThanTwoDecimalPlacesInsteadOfRounding() {
        assertThrows(IllegalArgumentException::class.java) {
            SaleEntryParser.parse("12,TWO_TOP,1.239,FULL")
        }
    }

    @Test
    fun rejectsNegativeZeroAndMalformedAmounts() {
        listOf("-1", "0", "1e2", "1,000", ".50", "abc").forEach { amount ->
            assertThrows(IllegalArgumentException::class.java) {
                SaleEntryParser.parse("12,TWO_TOP,$amount,FULL")
            }
        }
    }

    @Test
    fun rejectsInvalidNumberCategoryAndHalfMarker() {
        listOf(
            "1,TWO_TOP,10,FULL",
            "1A,TWO_TOP,10,FULL",
            "123,TWO_TOP,10,FULL",
            "12,THREE_TOAD,10,FULL",
            "12,TWO_TOP,10,UNKNOWN"
        ).forEach { row ->
            assertThrows(IllegalArgumentException::class.java) {
                SaleEntryParser.parse(row)
            }
        }
    }

    @Test
    fun rejectsEmptyInputAndIncorrectColumnCount() {
        assertThrows(IllegalArgumentException::class.java) { SaleEntryParser.parse("  \n") }
        assertThrows(IllegalArgumentException::class.java) { SaleEntryParser.parse("12,TWO_TOP,10") }
        assertThrows(IllegalArgumentException::class.java) { SaleEntryParser.parse("12,TWO_TOP,10,FULL,EXTRA") }
    }

    @Test
    fun rejectsTotalOverflow() {
        val entries = listOf(
            ParsedSaleEntry("12", "TWO_TOP", Long.MAX_VALUE, false),
            ParsedSaleEntry("13", "TWO_TOP", 1L, false)
        )
        assertThrows(ArithmeticException::class.java) {
            SaleEntryParser.totalSatang(entries)
        }
    }
}
