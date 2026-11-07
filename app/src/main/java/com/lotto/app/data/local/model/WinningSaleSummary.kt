package com.lotto.app.data.local.model

data class WinningSaleSummary(
    val drawDate: String,
    val saleId: Long,
    val customerFirstName: String,
    val customerLastName: String,
    val number: String,
    val prizeType: String,
    val stakeAmount: Long,
    val prizeAmount: Long
)
