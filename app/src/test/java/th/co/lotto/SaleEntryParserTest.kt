package th.co.lotto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
class SaleEntryParserTest {
 @Test fun parsesFourCategoriesAndPreservesLeadingZeros() {
  val entries=SaleEntryParser.parse("056,THREE_TOP,20\n650,THREE_TOAD,10\n56,TWO_TOP,5\n01,TWO_BOTTOM,7")
  assertEquals(4,entries.size); assertEquals("056",entries[0].number); assertEquals("01",entries[3].number)
  assertEquals(4_200L,SaleEntryParser.totalSatang(entries))
 }
 @Test fun rejectsCategoryLengthMismatchAndUnsupportedTypes() {
  listOf("123,TWO_TOP,10","12,THREE_TOP,10","123,THREE_BOTTOM,10","01,TWO_TOAD,10","1,TWO_BOTTOM,10").forEach { row ->
   assertThrows(IllegalArgumentException::class.java){SaleEntryParser.parse(row)}
  }
 }
 @Test fun rejectsInvalidAmountsAndEmptyInput() {
  listOf("-1","0","1e2","1,000",".50","1.239").forEach { amount ->
   assertThrows(IllegalArgumentException::class.java){SaleEntryParser.parse("123,THREE_TOP,$amount")}
  }
  assertThrows(IllegalArgumentException::class.java){SaleEntryParser.parse(" ")}
 }
}
