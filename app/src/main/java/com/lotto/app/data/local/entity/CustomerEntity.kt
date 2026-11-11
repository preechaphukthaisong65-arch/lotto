package com.lotto.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "customers",
    indices = [Index(value = ["customerCode"], unique = true), Index(value = ["firstName", "lastName"])]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val customerId: Long = 0L,
    val customerCode: String,
    val firstName: String,
    val lastName: String,
    val phone: String?,
    val createdAt: Long,
    val updatedAt: Long
)
