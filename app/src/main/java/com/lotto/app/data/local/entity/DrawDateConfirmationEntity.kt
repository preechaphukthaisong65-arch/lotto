package com.lotto.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "draw_date_confirmations")
data class DrawDateConfirmationEntity(
    @PrimaryKey val drawDate: String,
    val confirmedAt: Long
)
