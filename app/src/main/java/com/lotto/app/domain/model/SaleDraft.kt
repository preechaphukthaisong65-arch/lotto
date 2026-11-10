package com.lotto.app.domain.model

data class SaleDraft(
    val customerFirstName: String,
    val customerLastName: String,
    val customerId: Long? = null,
    val customerPhone: String? = null,
    val totalAmount: Long,
    val cashAmount: Long,
    val creditAmount: Long,
    val items: List<SaleDraftItem>
)

data class SaleDraftItem(
    val number: String,
    val prizeType: String,
    val amount: Long,
    val payoutMultiplier: Long
)
