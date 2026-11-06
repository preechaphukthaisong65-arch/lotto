package th.co.lotto
import th.co.lotto.data.DrawResultEntity
import th.co.lotto.data.SaleLineEntity
data class WinningLineMatch(val resultId:String,val resultType:String,val winningNumber:String,val betCategory:String,val lines:List<SaleLineEntity>)
/** TWO_TOP derives from first-prize tail; TWO_BOTTOM is official GLO last-two. */
object DrawResultMatcher {
 private val aliases=mapOf("THREE_TOP" to "THREE_TOP","3 ตัวบน" to "THREE_TOP","3บน" to "THREE_TOP","TWO_BOTTOM" to "TWO_BOTTOM","2 ตัวล่าง" to "TWO_BOTTOM","2ล่าง" to "TWO_BOTTOM")
 fun findMatches(results:List<DrawResultEntity>,saleLines:List<SaleLineEntity>):List<WinningLineMatch> = results.flatMap { result ->
  val source=aliases[result.resultType.trim().uppercase(java.util.Locale.ROOT)] ?: aliases[result.resultType.trim()] ?: return@flatMap emptyList()
  val len=if(source=="THREE_TOP")3 else 2
  if(result.winningNumber.length!=len || !result.winningNumber.all{it in '0'..'9'}) return@flatMap emptyList()
  val cats=if(source=="THREE_TOP") listOf("THREE_TOP","THREE_TOAD","TWO_TOP") else listOf("TWO_BOTTOM")
  cats.mapNotNull { cat ->
   val winning=if(cat=="TWO_TOP") result.winningNumber.takeLast(2) else result.winningNumber
   val matched=saleLines.filter { line ->
    if(line.periodId!=result.periodId || line.betCategory!=cat) false else when(cat) {
     "THREE_TOAD" -> line.numberText.length==3 && line.numberText.all{it in '0'..'9'} && line.numberText.toCharArray().sorted()==result.winningNumber.toCharArray().sorted()
     "TWO_TOP","TWO_BOTTOM" -> line.numberText.length==2 && line.numberText==winning
     "THREE_TOP" -> line.numberText==result.winningNumber
     else -> false
    }
   }
   if(matched.isEmpty()) null else WinningLineMatch(result.resultId,result.resultType,winning,cat,matched)
  }
 }
}
