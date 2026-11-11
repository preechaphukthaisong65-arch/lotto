package com.lotto.app.data.repository

import com.lotto.app.data.local.dao.CustomerDao
import com.lotto.app.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

class RoomCustomerRepository(private val dao: CustomerDao) : CustomerRepository {
    override fun observeAll(): Flow<List<CustomerEntity>> = dao.observeAll()
    override suspend fun getById(customerId: Long): CustomerEntity? = dao.getById(customerId)
}
