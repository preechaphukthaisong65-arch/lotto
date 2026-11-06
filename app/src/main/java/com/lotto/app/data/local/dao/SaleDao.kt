package com.lotto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.lotto.app.data.local.entity.CreditDocumentEntity
import com.lotto.app.data.local.entity.CreditDocumentItemEntity
import com.lotto.app.data.local.entity.SaleEntity
import com.lotto.app.data.local.entity.SaleItemEntity

@Dao
abstract class SaleDao {
    @Insert
    protected abstract suspend fun insertSale(sale: SaleEntity): Long

    @Insert
    protected abstract suspend fun insertSaleItems(items: List<SaleItemEntity>)

    @Insert
    protected abstract suspend fun insertCreditDocument(document: CreditDocumentEntity): Long

    @Insert
    protected abstract suspend fun insertCreditDocumentItems(items: List<CreditDocumentItemEntity>)

    @Transaction
    open suspend fun insertSaleWithItems(
        sale: SaleEntity,
        items: List<SaleItemEntity>
    ): Long {
        val saleId = insertSale(sale)
        insertSaleItems(items.map { it.copy(saleId = saleId) })

        if (sale.creditAmount > 0L) {
            val creditDocumentId = insertCreditDocument(
                CreditDocumentEntity(
                    sourceSaleId = saleId,
                    customerFirstName = sale.customerFirstName,
                    customerLastName = sale.customerLastName,
                    saleTotalAmount = sale.totalAmount,
                    cashAmount = sale.cashAmount,
                    creditAmount = sale.creditAmount,
                    createdAt = sale.createdAt
                )
            )
            insertCreditDocumentItems(
                items.map {
                    CreditDocumentItemEntity(
                        creditDocumentId = creditDocumentId,
                        number = it.number,
                        prizeType = it.prizeType,
                        amount = it.amount,
                        payoutMultiplier = it.payoutMultiplier
                    )
                }
            )
        }
        return saleId
    }

    @Query("SELECT * FROM sales WHERE saleId = :saleId LIMIT 1")
    abstract suspend fun getSaleById(saleId: Long): SaleEntity?

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId ORDER BY itemId ASC")
    abstract suspend fun getItemsForSale(saleId: Long): List<SaleItemEntity>

    @Query("SELECT * FROM credit_documents ORDER BY createdAt DESC, creditDocumentId DESC")
    abstract suspend fun getAllCreditDocuments(): List<CreditDocumentEntity>

    @Query("SELECT * FROM credit_documents WHERE creditDocumentId = :creditDocumentId LIMIT 1")
    abstract suspend fun getCreditDocumentById(creditDocumentId: Long): CreditDocumentEntity?

    @Query("SELECT * FROM credit_document_items WHERE creditDocumentId = :creditDocumentId ORDER BY creditItemId ASC")
    abstract suspend fun getItemsForCreditDocument(creditDocumentId: Long): List<CreditDocumentItemEntity>
}
