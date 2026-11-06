package th.co.lotto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import th.co.lotto.data.SaleLineEntity
import th.co.lotto.data.SettlementSnapshotEntity
class PayoutCalculatorTest {
 @Test fun fullAndHalfPayoutKeepOriginalStake() {
  assertEquals(700_000L,PayoutCalculator.calculateLinePayoutSatang(10_000L,7_000L,false))
  assertEquals(350_000L,PayoutCalculator.calculateLinePayoutSatang(10_000L,7_000L,true))
 }
 @Test fun roundsEachWinningLineHalfUp() {
  assertEquals(706_013L,PayoutCalculator.calculateLinePayoutSatang(10_050L,7_025L,false))
  assertEquals(353_006L,PayoutCalculator.calculateLinePayoutSatang(10_050L,7_025L,true))
 }
 @Test fun calculatesIndependentRatesForAllFourCategories() {
  val snapshot=SettlementSnapshotEntity("s","p",7000L,7000L,50000L,10000L,1L,10L,twoTopRateHundredths=8000L,twoBottomRateHundredths=9000L)
  val top=SaleLineEntity("top","d1","p","123","THREE_TOP",10000L,false)
  val toad=SaleLineEntity("toad","d2","p","321","THREE_TOAD",5000L,false)
  val twoTop=SaleLineEntity("two-top","d3","p","23","TWO_TOP",2000L,false)
  val twoBottom=SaleLineEntity("two-bottom","d4","p","01","TWO_BOTTOM",3000L,false)
  val matches=listOf(
   WinningLineMatch("r1","THREE_TOP","123","THREE_TOP",listOf(top)),
   WinningLineMatch("r1","THREE_TOP","123","THREE_TOAD",listOf(toad)),
   WinningLineMatch("r1","THREE_TOP","23","TWO_TOP",listOf(twoTop)),
   WinningLineMatch("r2","TWO_BOTTOM","01","TWO_BOTTOM",listOf(twoBottom))
  )
  val summary=PayoutCalculator.calculatePeriod(matches,snapshot,2_000_000L)
  assertEquals(4,summary.linePayouts.size)
  assertEquals(1_980_000L,summary.totalPayoutSatang)
  assertEquals(20_000L,summary.salesAfterPayoutSatang)
 }
 @Test fun blocksDuplicateLinePayouts() {
  val snapshot=SettlementSnapshotEntity("s","p",7000L,7000L,50000L,10000L,1L,1L)
  val line=SaleLineEntity("same","d","p","123","THREE_TOP",100L,false)
  val matches=listOf(WinningLineMatch("r1","THREE_TOP","123","THREE_TOP",listOf(line)),WinningLineMatch("r2","THREE_TOP","123","THREE_TOP",listOf(line)))
  assertThrows(IllegalArgumentException::class.java){PayoutCalculator.calculatePeriod(matches,snapshot,100L)}
 }
 @Test fun rejectsInvalidStakeAndUnknownCategory() {
  assertThrows(IllegalArgumentException::class.java){PayoutCalculator.calculateLinePayoutSatang(-1L,7000L,false)}
  val snapshot=SettlementSnapshotEntity("s","p",7000L,7000L,50000L,10000L,1L,1L)
  val line=SaleLineEntity("line","doc","p","123","UNKNOWN",100L,false)
  assertThrows(IllegalStateException::class.java){PayoutCalculator.calculatePeriod(listOf(WinningLineMatch("r","THREE_TOP","123","UNKNOWN",listOf(line))),snapshot,100L)}
 }
}
