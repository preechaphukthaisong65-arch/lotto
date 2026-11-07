package com.lotto.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "draw_dates")
data class DrawDateEntity(
    @PrimaryKey
    val drawDate: String,
    val referenceDate: String?,
    val source: String,
    val note: String?,
    val updatedAt: Long
)
