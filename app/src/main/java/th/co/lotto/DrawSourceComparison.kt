package th.co.lotto

data class DrawSourceComparison(
    val drawDateIso: String,
    val lotteryChecks: PublicDrawResult?,
    val huayReview: PublicDrawResult?
) {
    val bothAvailable: Boolean get() = lotteryChecks != null && huayReview != null
    val datesMatch: Boolean get() = bothAvailable &&
        lotteryChecks!!.drawDateIso == drawDateIso &&
        huayReview!!.drawDateIso == drawDateIso
    val numbersMatch: Boolean get() = bothAvailable &&
        lotteryChecks!!.firstPrizeNumber == huayReview!!.firstPrizeNumber &&
        lotteryChecks!!.twoBottomNumber == huayReview!!.twoBottomNumber
    val matches: Boolean get() = datesMatch && numbersMatch
    val mismatch: Boolean get() = datesMatch && !numbersMatch
}
