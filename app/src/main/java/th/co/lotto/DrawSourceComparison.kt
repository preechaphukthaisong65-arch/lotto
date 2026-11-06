package th.co.lotto

data class DrawSourceComparison(
    val drawDateIso: String,
    val lotteryChecks: PublicDrawResult?,
    val huayReview: PublicDrawResult?
) {
    val bothAvailable: Boolean get() = lotteryChecks != null && huayReview != null
    val matches: Boolean get() = bothAvailable &&
        lotteryChecks!!.firstPrizeNumber == huayReview!!.firstPrizeNumber &&
        lotteryChecks!!.twoBottomNumber == huayReview!!.twoBottomNumber
    val mismatch: Boolean get() = bothAvailable && !matches
}
