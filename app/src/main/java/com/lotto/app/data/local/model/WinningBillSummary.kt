package com.lotto.app.data.local.model

data class WinningBillSummary(
    val drawDate: String,
    val saleId: Long,
    val customerFirstName: String,
    val customerLastName: String,
    val totalPrize: Long,
    val payoutStatus: String,
    val recipientName: String?,
    val paidAt: Long?,
    val saleCreatedAt: Long,
    val itemDetails: String
)
