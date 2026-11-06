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

    private fun checkedSum(values: Iterable<Long>): Long =
        values.fold(0L) { total, value -> Math.addExact(total, value) }

    @Transaction
    open fun postSale(document: SaleDocumentEntity, lines: List<SaleLineEntity>, audit: AuditEventEntity) {
        require(document.documentId.isNotBlank()) { "Document ID is required" }
        require(lines.isNotEmpty()) { "A sale must contain at least one line" }
        require(document.totalAmountSatang > 0L) { "Sale total must be positive" }
        require(lines.all { it.lineId.isNotBlank() }) { "Every sale line requires an ID" }
        require(lines.map { it.lineId }.distinct().size == lines.size) { "Duplicate sale line ID" }
        require(lines.all { it.amountSatang > 0L }) { "Every sale line must be positive" }
        require(lines.all { it.documentId == document.documentId && it.periodId == document.periodId }) { "Sale line/document mismatch" }
        require(checkedSum(lines.map { it.amountSatang }) == document.totalAmountSatang) { "Sale total does not match line totals" }
        require(audit.eventId.isNotBlank() && audit.entityType == "SALE_DOCUMENT" &&
            audit.entityId == document.documentId && audit.action == "POSTED") {
            "Sale audit event does not match the posted document"
        }

        val period = getPeriod(document.periodId) ?: error("Draw period not found")
        require(period.status == "OPEN") { "Draw period is closed" }
        document.correctionOfDocumentId?.let { originalId ->
            val original = getSaleDocument(originalId) ?: error("Original sale document not found")
            require(original.periodId == document.periodId) { "Correction must stay in the original draw period" }
            require(original.correctionOfDocumentId == null) { "A correction document cannot be used as an original" }
            require(original.documentId != document.documentId) { "A document cannot reference itself" }
        }

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
                require(!customer.identityNote.isNullOrBlank()) { "Credit customer identity details are required" }
            }
            else -> error("Unknown customer type")
        }

        // Aggregate each number/category once to avoid repeatedly adding a full batch.
        val incomingByNumberAndCategory = lines.groupBy { it.numberText to it.betCategory }
        incomingByNumberAndCategory.forEach { (key, groupedLines) ->
            val (number, category) = key
            require(number.length == 2 || number.length == 3) { "Number must contain 2 or 3 digits" }
            require(number.all { it in '0'..'9' }) { "Number must contain ASCII digits only" }
            val validCategories = if (number.length == 2) {
                setOf("TWO_TOP", "TWO_BOTTOM")
            } else {
                setOf("THREE_TOP", "THREE_BOTTOM", "THREE_TOAD")
            }
            require(category in validCategories) { "Category does not match number length" }

            val rule = getNumberLimitRule(document.periodId, number.length, category, number)
            if (rule != null) {
                val alreadySold = getSoldAmountForNumber(document.periodId, number, category)
                val incoming = checkedSum(groupedLines.map { it.amountSatang })
                val projected = Math.addExact(alreadySold, incoming)
                require(projected <= rule.maximumAmountSatang) {
                    "Number limit exceeded: $number / $category (sold=$alreadySold, incoming=$incoming, limit=${rule.maximumAmountSatang})"
                }
            }
        }

        insertSaleDocument(document)
        insertSaleLines(lines)
        insertAuditEvent(audit)
    }

    @Query("SELECT * FROM sale_documents WHERE documentId = :documentId LIMIT 1")
    abstract fun getSaleDocument(documentId: String): SaleDocumentEntity?

    @Query("""
        SELECT DISTINCT sd.* FROM sale_documents sd
        LEFT JOIN sale_lines sl ON sl.documentId = sd.documentId
        WHERE (:periodId IS NULL OR sd.periodId = :periodId)
          AND (:searchText = '' OR sd.documentId LIKE '%' || :searchText || '%'
               OR sl.numberText LIKE '%' || :searchText || '%')
        ORDER BY sd.createdAtEpochMillis DESC
    """)
    abstract fun searchSales(periodId: String?, searchText: String): List<SaleDocumentEntity>

    @Query("SELECT COALESCE(SUM(amountSatang), 0) FROM payment_entries WHERE documentId = :documentId")
    abstract fun getPaymentsTotalForDocument(documentId: String): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertPayment(payment: PaymentEntryEntity)

    @Transaction
    open fun postPayment(payment: PaymentEntryEntity, audit: AuditEventEntity) {
        require(payment.paymentId.isNotBlank()) { "Payment ID is required" }
        require(payment.amountSatang > 0L) { "Payment must be positive" }
        require(payment.customerId.isNotBlank()) { "Payment customer is required" }
        require(audit.eventId.isNotBlank() && audit.entityType == "PAYMENT" &&
            audit.entityId == payment.paymentId && audit.action == "POSTED") {
            "Payment audit event does not match the payment"
        }

        val document = getSaleDocument(payment.documentId) ?: error("Sale document not found")
        require(document.customerType == "CREDIT") { "Payments can only be applied to credit documents" }
        require(document.customerId == payment.customerId) { "Payment customer does not match sale document" }

        val alreadyPaid = getPaymentsTotalForDocument(payment.documentId)
        val projectedPaid = Math.addExact(alreadyPaid, payment.amountSatang)
        require(projectedPaid <= document.totalAmountSatang) { "Payment exceeds document balance" }

        insertPayment(payment)
        insertAuditEvent(audit)
    }

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
