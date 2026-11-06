package th.co.lotto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SaleEntryParserTest {
    @Test
    fun parsesOnlyThreeDigitTopAndToadAndPreservesLeadingZeros() {
        val entries = SaleEntryParser.parse(
            """
            001,THREE_TOP,5
            001,THREE_TOAD,7.25
            """.trimIndent()
        )
        assertEquals(2, entries.size)
        assertEquals("001", entries[0].number)
        assertEquals(500L, entries[0].amountSatang)
        assertEquals(725L, entries[1].amountSatang)
        assertEquals(1_225L, SaleEntryParser.totalSatang(entries))
    }

    @Test
    fun ignoresBlankLinesAndAcceptsWholeBahtAmounts() {
        val entries = SaleEntryParser.parse("\n12,TWO_TOP,20\n\n123,THREE_TOAD,10\n")
        assertEquals(2, entries.size)
        assertEquals(3_000L, SaleEntryParser.totalSatang(entries))
    }

    @Test
    fun rejectsAmountsWithMoreThanTwoDecimalPlacesInsteadOfRounding() {
        assertThrows(IllegalArgumentException::class.java) {
            SaleEntryParser.parse("12,TWO_TOP,1.239")
        }
    }

    @Test
    fun rejectsNegativeZeroAndMalformedAmounts() {
        listOf("-1", "0", "1e2", "1,000", ".50", "abc").forEach { amount ->
            assertThrows(IllegalArgumentException::class.java) {
                SaleEntryParser.parse("12,TWO_TOP,$amount")
            }
        }
    }

    @Test
    fun rejectsInvalidNumberAndCategory() {
        listOf(
            "1,TWO_TOP,10",
            "1A,TWO_TOP,10",
            "123,TWO_TOP,10",
            "12,THREE_TOAD,10",
            "12,TWO_TOP,10,HALF",
            "12,TWO_TOP,10,EXTRA"
        ).forEach { row ->
            assertThrows(IllegalArgumentException::class.java) {
                SaleEntryParser.parse(row)
            }
        }
    }

    @Test
    fun rejectsEmptyInputAndIncorrectColumnCount() {
        assertThrows(IllegalArgumentException::class.java) { SaleEntryParser.parse("  \n") }
        assertThrows(IllegalArgumentException::class.java) { SaleEntryParser.parse("12,TWO_TOP,10,HALF") }
        assertThrows(IllegalArgumentException::class.java) { SaleEntryParser.parse("12,TWO_TOP,10,HALF,EXTRA") }
    }

    @Test
    fun rejectsTotalOverflow() {
        val entries = listOf(
            ParsedSaleEntry("12", "TWO_TOP", Long.MAX_VALUE),
            ParsedSaleEntry("13", "TWO_TOP", 1L)
        )
        assertThrows(ArithmeticException::class.java) {
            SaleEntryParser.totalSatang(entries)
        }
    }
}
