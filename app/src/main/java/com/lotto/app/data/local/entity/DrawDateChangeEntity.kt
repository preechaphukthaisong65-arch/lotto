package com.lotto.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "draw_date_changes",
    indices = [Index("changedAt"), Index("drawDate")]
)
data class DrawDateChangeEntity(
    @PrimaryKey(autoGenerate = true)
    val changeId: Long = 0,
    val drawDate: String,
    val previousDate: String?,
    val newDate: String,
    val changedAt: Long,
    val source: String
)
