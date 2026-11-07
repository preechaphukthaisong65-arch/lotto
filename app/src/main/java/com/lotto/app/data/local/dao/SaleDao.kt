package com.lotto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.lotto.app.data.local.entity.CreditDocumentEntity
import com.lotto.app.data.local.entity.CreditDocumentItemEntity
import com.lotto.app.data.local.entity.CreditPaymentEntity
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

    @Insert
    protected abstract suspend fun insertCreditPayment(payment: CreditPaymentEntity): Long

    @Query("SELECT COALESCE(SUM(amount), 0) FROM credit_payments WHERE creditDocumentId = :creditDocumentId")
    protected abstract suspend fun getTotalPayments(creditDocumentId: Long): Long

    @Query("UPDATE credit_documents SET status = :status, paidAt = :paidAt WHERE creditDocumentId = :creditDocumentId")
    protected abstract suspend fun updateCreditStatus(creditDocumentId: Long, status: String, paidAt: Long?)

    @Transaction
    open suspend fun insertSaleWithItems(sale: SaleEntity, items: List<SaleItemEntity>): Long {
        val saleId = insertSale(sale)
        insertSaleItems(items.map { it.copy(saleId = saleId) })

        // All sales have a document in the same account category.
        // Cash-only bills are PAID from creation; credit bills remain OPEN.
        val isCashSale = sale.creditAmount == 0L
        val creditDocumentId = insertCreditDocument(
            CreditDocumentEntity(
                sourceSaleId = saleId,
                customerFirstName = sale.customerFirstName,
                customerLastName = sale.customerLastName,
                saleTotalAmount = sale.totalAmount,
                cashAmount = sale.cashAmount,
                creditAmount = sale.creditAmount,
                createdAt = sale.createdAt,
                status = if (isCashSale) CreditDocumentEntity.STATUS_PAID else CreditDocumentEntity.STATUS_OPEN,
                paidAt = if (isCashSale) sale.createdAt else null
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
        return saleId
    }

    @Query(
        """
        SELECT
            sale_items.number AS number,
            sale_items.prizeType AS prizeType,
            SUM(sale_items.amount) AS totalAmount
        FROM sale_items
        INNER JOIN sales ON sales.saleId = sale_items.saleId
        WHERE sales.createdAt >= :startAt
          AND sales.createdAt < :endAt
          AND sales.status = 'CONFIRMED'
        GROUP BY sale_items.prizeType, sale_items.number
        ORDER BY totalAmount DESC, sale_items.number ASC
        """
    )
    abstract suspend fun getNumberSalesSummary(
        startAt: Long,
        endAt: Long
    ): List<com.lotto.app.data.local.model.SaleNumberSummary>

    @Query("SELECT * FROM sales ORDER BY createdAt DESC, saleId DESC")
    abstract suspend fun getAllSales(): List<SaleEntity>

    @Query("SELECT * FROM sales WHERE saleId = :saleId LIMIT 1")
    abstract suspend fun getSaleById(saleId: Long): SaleEntity?

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId ORDER BY itemId ASC")
    abstract suspend fun getItemsForSale(saleId: Long): List<SaleItemEntity>

    @Query("SELECT * FROM credit_documents WHERE status = 'OPEN' ORDER BY createdAt DESC, creditDocumentId DESC")
    abstract suspend fun getOpenCreditDocuments(): List<CreditDocumentEntity>

    @Query("SELECT * FROM credit_documents WHERE status = 'PAID' ORDER BY paidAt DESC, creditDocumentId DESC")
    abstract suspend fun getPaidDocuments(): List<CreditDocumentEntity>

    @Query("SELECT * FROM credit_documents WHERE creditDocumentId = :creditDocumentId LIMIT 1")
    abstract suspend fun getCreditDocumentById(creditDocumentId: Long): CreditDocumentEntity?

    @Query("SELECT * FROM credit_document_items WHERE creditDocumentId = :creditDocumentId ORDER BY creditItemId ASC")
    abstract suspend fun getItemsForCreditDocument(creditDocumentId: Long): List<CreditDocumentItemEntity>

    @Query("SELECT * FROM credit_payments WHERE creditDocumentId = :creditDocumentId ORDER BY paidAt ASC, paymentId ASC")
    abstract suspend fun getPaymentsForCreditDocument(creditDocumentId: Long): List<CreditPaymentEntity>

    @Transaction
    open suspend fun recordCreditPayment(creditDocumentId: Long, amount: Long, paidAt: Long) {
        require(amount > 0L) { "ยอดรับชำระต้องมากกว่า 0 บาท" }
        val document = getCreditDocumentById(creditDocumentId)
            ?: throw IllegalArgumentException("ไม่พบเอกสารบัญชีเชื่อ")
        require(document.status == CreditDocumentEntity.STATUS_OPEN) { "บิลนี้ชำระครบแล้ว" }

        val alreadyPaid = getTotalPayments(creditDocumentId)
        val outstanding = document.creditAmount - alreadyPaid
        require(outstanding > 0L) { "ไม่มียอดค้างชำระ" }
        require(amount <= outstanding) { "ยอดรับชำระเกินยอดค้างชำระ" }

        insertCreditPayment(CreditPaymentEntity(
            creditDocumentId = creditDocumentId,
            amount = amount,
            paidAt = paidAt
        ))

        if (alreadyPaid + amount == document.creditAmount) {
            updateCreditStatus(creditDocumentId, CreditDocumentEntity.STATUS_PAID, paidAt)
        }
    }

    /**
     * Only PAID documents older than 180 days are eligible.
     * OPEN documents are never deleted, regardless of age.
     * Related items and payment history cascade with the paid document.
     */
    @Query("""
        DELETE FROM credit_documents
        WHERE status = 'PAID'
          AND paidAt IS NOT NULL
          AND paidAt <= :cutoffTimestamp
    """)
    abstract suspend fun deleteExpiredPaidDocuments(cutoffTimestamp: Long): Int
}
