package th.co.lotto.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class LottoDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertPeriod(period: DrawPeriodEntity)

    @Query("SELECT * FROM draw_periods ORDER BY createdAtEpochMillis DESC")
    abstract suspend fun getPeriods(): List<DrawPeriodEntity>

    @Query("SELECT * FROM draw_periods WHERE status = 'OPEN' ORDER BY createdAtEpochMillis DESC LIMIT 1")
    abstract suspend fun getOpenPeriod(): DrawPeriodEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertCustomer(customer: CustomerEntity)

    @Query("SELECT * FROM customers ORDER BY displayName COLLATE NOCASE")
    abstract suspend fun getCustomers(): List<CustomerEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertSaleDocument(document: SaleDocumentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertSaleLines(lines: List<SaleLineEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertAuditEvent(event: AuditEventEntity)

    @Transaction
    open suspend fun postSale(document: SaleDocumentEntity, lines: List<SaleLineEntity>, audit: AuditEventEntity) {
        require(lines.isNotEmpty()) { "A sale must contain at least one line" }
        require(lines.all { it.documentId == document.documentId && it.periodId == document.periodId }) { "Sale line/document mismatch" }
        require(lines.sumOf { it.amountSatang } == document.totalAmountSatang) { "Sale total does not match line totals" }
        insertSaleDocument(document)
        insertSaleLines(lines)
        insertAuditEvent(audit)
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertPayment(payment: PaymentEntryEntity)

    @Query("SELECT COALESCE(SUM(amountSatang), 0) FROM sale_documents WHERE customerId = :customerId")
    abstract suspend fun getCreditSalesTotal(customerId: String): Long

    @Query("SELECT COALESCE(SUM(amountSatang), 0) FROM payment_entries WHERE customerId = :customerId")
    abstract suspend fun getPaymentsTotal(customerId: String): Long

    @Query("SELECT * FROM sale_documents WHERE periodId = :periodId ORDER BY createdAtEpochMillis DESC")
    abstract suspend fun getSalesForPeriod(periodId: String): List<SaleDocumentEntity>

    @Query("SELECT * FROM sale_lines WHERE documentId = :documentId ORDER BY lineId")
    abstract suspend fun getLinesForDocument(documentId: String): List<SaleLineEntity>

    @Query("SELECT COUNT(*) FROM sale_documents WHERE documentId = :documentId")
    abstract suspend fun documentExists(documentId: String): Int
}
