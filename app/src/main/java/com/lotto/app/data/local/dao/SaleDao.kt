package com.lotto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.lotto.app.data.local.entity.SaleEntity
import com.lotto.app.data.local.entity.SaleItemEntity

@Dao
abstract class SaleDao {
    @Insert
    protected abstract suspend fun insertSale(sale: SaleEntity): Long

    @Insert
    protected abstract suspend fun insertSaleItems(items: List<SaleItemEntity>)

    @Transaction
    open suspend fun insertSaleWithItems(
        sale: SaleEntity,
        items: List<SaleItemEntity>
    ): Long {
        val saleId = insertSale(sale)
        insertSaleItems(items.map { it.copy(saleId = saleId) })
        return saleId
    }

    @Query("SELECT * FROM sales WHERE saleId = :saleId LIMIT 1")
    abstract suspend fun getSaleById(saleId: Long): SaleEntity?

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId ORDER BY itemId ASC")
    abstract suspend fun getItemsForSale(saleId: Long): List<SaleItemEntity>
}
