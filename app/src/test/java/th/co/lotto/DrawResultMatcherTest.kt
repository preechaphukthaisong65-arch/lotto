package th.co.lotto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import th.co.lotto.data.DrawResultEntity
import th.co.lotto.data.SaleLineEntity
class DrawResultMatcherTest {
 @Test fun firstPrizeTailMatchesTopToadAndDerivedTwoTop() {
  val r=DrawResultEntity("r","p","THREE_TOP","056","GLO",null,1L)
  val lines=listOf(SaleLineEntity("a","d1","p","056","THREE_TOP",100L,false),SaleLineEntity("b","d2","p","650","THREE_TOAD",200L,false),SaleLineEntity("c","d3","p","56","TWO_TOP",300L,false))
  val m=DrawResultMatcher.findMatches(listOf(r),lines)
  assertEquals(3,m.size);assertEquals(listOf("a"),m.first{it.betCategory=="THREE_TOP"}.lines.map{it.lineId})
  assertEquals(listOf("b"),m.first{it.betCategory=="THREE_TOAD"}.lines.map{it.lineId})
  assertEquals(listOf("c"),m.first{it.betCategory=="TWO_TOP"}.lines.map{it.lineId})
 }
 @Test fun officialLastTwoMatchesOnlyTwoBottomAndKeepsZero() {
  val r=DrawResultEntity("r2","p","TWO_BOTTOM","01","GLO",null,1L)
  val lines=listOf(SaleLineEntity("a","d1","p","01","TWO_BOTTOM",100L,false),SaleLineEntity("b","d2","p","01","TWO_TOP",100L,false))
  val m=DrawResultMatcher.findMatches(listOf(r),lines)
  assertEquals(1,m.size);assertEquals("a",m.single().lines.single().lineId)
 }
 @Test fun rejectsUnknownTypesAndWrongLengths() {
  val rs=listOf(DrawResultEntity("r1","p","unknown","123","GLO",null,1L),DrawResultEntity("r2","p","THREE_TOP","12","GLO",null,1L),DrawResultEntity("r3","p","TWO_BOTTOM","123","GLO",null,1L))
  assertTrue(DrawResultMatcher.findMatches(rs,emptyList()).isEmpty())
 }
}
