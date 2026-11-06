package com.lotto.app.ui.sale.model

data class ThreeDigitEntry(
    val id: Long,
    val firstDigit: String = "",
    val secondDigit: String = "",
    val thirdDigit: String = "",
    val straightAmount: String = "",
    val boxAmount: String = ""
)

data class TwoDigitEntry(
    val id: Long,
    val firstDigit: String = "",
    val secondDigit: String = "",
    val topAmount: String = "",
    val bottomAmount: String = ""
)
