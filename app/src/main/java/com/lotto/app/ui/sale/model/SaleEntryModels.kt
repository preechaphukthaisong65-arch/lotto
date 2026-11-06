package com.lotto.app.ui.sale.model

data class ThreeDigitEntry(
    val id: Long,
    val firstDigit: String = "",
    val secondDigit: String = "",
    val thirdDigit: String = "",
    val straightAmount: String = "0",
    val boxAmount: String = "0"
)

data class TwoDigitEntry(
    val id: Long,
    val firstDigit: String = "",
    val secondDigit: String = "",
    val topAmount: String = "0",
    val bottomAmount: String = "0"
)
