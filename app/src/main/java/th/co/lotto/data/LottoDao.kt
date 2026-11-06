package th.co.lotto.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class LottoDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertPeriod(period: DrawPeriodEntity)

    @Query("SELECT * FROM draw_periods ORDER BY createdAtEpochMillis DESC")
    abstract fun getPeriods(): List<DrawPeriodEntity>

    @Query("SELECT * FROM draw_periods WHERE periodId = :periodId LIMIT 1")
    abstract fun getPeriod(periodId: String): DrawPeriodEntity?

    @Query("SELECT * FROM draw_periods WHERE status = 'OPEN' ORDER BY createdAtEpochMillis DESC LIMIT 1")
    abstract fun getOpenPeriod(): DrawPeriodEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertCustomer(customer: CustomerEntity)

    @Query("SELECT * FROM customers ORDER BY displayName COLLATE NOCASE")
    abstract fun getCustomers(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE customerId = :customerId LIMIT 1")
    abstract fun getCustomer(customerId: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertSaleDocument(document: SaleDocumentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertSaleLines(lines: List<SaleLineEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertAuditEvent(event: AuditEventEntity)

    @Query("""
        SELECT COALESCE(SUM(sl.amountSatang), 0)
        FROM sale_lines sl
        INNER JOIN sale_documents sd ON sd.documentId = sl.documentId
        WHERE sl.periodId = :periodId
          AND sl.numberText = :numberText
          AND sl.betCategory = :betCategory
    """)
    abstract fun getSoldAmountForNumber(periodId: String, numberText: String, betCategory: String): Long

    @Query("""
        SELECT * FROM number_limit_rules
        WHERE periodId = :periodId AND numberLength = :numberLength
          AND betCategory = :betCategory AND numberText = :numberText AND enabled = 1
        LIMIT 1
    """)
    abstract fun getNumberLimitRule(periodId: String, numberLength: Int, betCategory: String, numberText: String): NumberLimitRuleEntity?

    @Transaction
    open fun postSale(document: SaleDocumentEntity, lines: List<SaleLineEntity>, audit: AuditEventEntity) {
        require(lines.isNotEmpty()) { "A sale must contain at least one line" }
        require(document.totalAmountSatang > 0L) { "Sale total must be positive" }
        require(lines.all { it.amountSatang > 0L }) { "Every sale line must be positive" }
        require(lines.all { it.documentId == document.documentId && it.periodId == document.periodId }) { "Sale line/document mismatch" }
        require(lines.sumOf { it.amountSatang } == document.totalAmountSatang) { "Sale total does not match line totals" }

        val period = getPeriod(document.periodId) ?: error("Draw period not found")
        require(period.status == "OPEN") { "Draw period is closed" }

        when (document.customerType) {
            "CASH" -> {
                if (document.customerId != null) {
                    val customer = getCustomer(document.customerId) ?: error("Customer not found")
                    require(customer.customerType == "CASH") { "Cash sale cannot use a credit customer" }
                }
            }
            "CREDIT" -> {
                val customerId = document.customerId ?: error("Credit sale requires a customer")
                val customer = getCustomer(customerId) ?: error("Credit customer not found")
                require(customer.customerType == "CREDIT") { "Selected customer is not a credit customer" }
                require(customer.displayName.isNotBlank()) { "Credit customer name is required" }
                require(!customer.phone.isNullOrBlank()) { "Credit customer phone is required" }
            }
            else -> error("Unknown customer type")
        }

        lines.forEach { line ->
            require(line.numberText.all(Char::isDigit)) { "Number must contain digits only" }
            require(line.numberText.length == 2 || line.numberText.length == 3) { "Number must be 2 or 3 digits" }
            val validCategories = if (line.numberText.length == 2) setOf("TWO_TOP", "TWO_BOTTOM") else setOf("THREE_TOP", "THREE_BOTTOM", "THREE_TOAD")
            require(line.betCategory in validCategories) { "Category does not match number length" }
            val rule = getNumberLimitRule(document.periodId, line.numberText.length, line.betCategory, line.numberText)
            if (rule != null) {
                val alreadySold = getSoldAmountForNumber(document.periodId, line.numberText, line.betCategory)
                val incomingForSameKey = lines.filter {
                    it.numberText == line.numberText && it.betCategory == line.betCategory
                }.sumOf { it.amountSatang }
                require(alreadySold + incomingForSameKey <= rule.maximumAmountSatang) {
                    "Number limit exceeded: ${line.numberText} / ${line.betCategory}"
                }
            }
        }

        insertSaleDocument(document)
        insertSaleLines(lines)
        insertAuditEvent(audit)
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertPayment(payment: PaymentEntryEntity)

    @Query("SELECT COALESCE(SUM(totalAmountSatang), 0) FROM sale_documents WHERE customerId = :customerId AND customerType = 'CREDIT'")
    abstract fun getCreditSalesTotal(customerId: String): Long

    @Query("SELECT COALESCE(SUM(amountSatang), 0) FROM payment_entries WHERE customerId = :customerId")
    abstract fun getPaymentsTotal(customerId: String): Long

    @Query("SELECT * FROM sale_documents WHERE periodId = :periodId ORDER BY createdAtEpochMillis DESC")
    abstract fun getSalesForPeriod(periodId: String): List<SaleDocumentEntity>

    @Query("SELECT * FROM sale_lines WHERE documentId = :documentId ORDER BY lineId")
    abstract fun getLinesForDocument(documentId: String): List<SaleLineEntity>

    @Query("SELECT COUNT(*) FROM sale_documents WHERE documentId = :documentId")
    abstract fun documentExists(documentId: String): Int

    @Query("SELECT * FROM payment_entries WHERE customerId = :customerId ORDER BY paidAtEpochMillis DESC")
    abstract fun getPaymentsForCustomer(customerId: String): List<PaymentEntryEntity>

    @Query("SELECT * FROM sale_documents WHERE customerId = :customerId AND customerType = 'CREDIT' ORDER BY createdAtEpochMillis DESC")
    abstract fun getCreditDocumentsForCustomer(customerId: String): List<SaleDocumentEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertLimitRule(rule: NumberLimitRuleEntity)

    @Query("SELECT * FROM number_limit_rules WHERE periodId = :periodId ORDER BY numberLength, betCategory, numberText")
    abstract fun getLimitRules(periodId: String): List<NumberLimitRuleEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertDrawResult(result: DrawResultEntity)

    @Query("SELECT * FROM draw_results WHERE periodId = :periodId ORDER BY importedAtEpochMillis DESC")
    abstract fun getDrawResults(periodId: String): List<DrawResultEntity>

    @Query("SELECT * FROM audit_events WHERE entityId = :entityId ORDER BY createdAtEpochMillis")
    abstract fun getAuditEvents(entityId: String): List<AuditEventEntity>
}
