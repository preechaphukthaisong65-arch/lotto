package com.lotto.app.data.repository

import com.lotto.app.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

interface CustomerRepository {
    fun observeAll(): Flow<List<CustomerEntity>>
    suspend fun getById(customerId: Long): CustomerEntity?
}
