package com.lotto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lotto.app.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY firstName COLLATE NOCASE, lastName COLLATE NOCASE, customerId")
    fun observeAll(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE customerId = :customerId LIMIT 1")
    suspend fun getById(customerId: Long): CustomerEntity?

    @Upsert
    suspend fun upsert(customer: CustomerEntity)
}
