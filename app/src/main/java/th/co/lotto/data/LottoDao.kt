package th.co.lotto.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
abstract class LottoDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertPeriod(period: DrawPeriodEntity)

    @Query("SELECT * FROM draw_periods ORDER BY createdAtEpochMillis DESC")
    abstract fun getPeriods(): List<DrawPeriodEntity>

    @Query("SELECT * FROM draw_periods WHERE periodId = :periodId LIMIT 1")
    abstract fun getPeriod(periodId: String): DrawPeriodEntity?

    @Query("""
        UPDATE draw_periods SET drawDateEpochMillis = :drawDateEpochMillis
        WHERE periodId = :periodId AND status IN ('OPEN', 'PREPARED')
    """)
    abstract fun updateEditablePeriodDrawDate(periodId: String, drawDateEpochMillis: Long): Int

    @Query("SELECT * FROM draw_periods WHERE status = 'OPEN' ORDER BY createdAtEpochMillis DESC LIMIT 1")
    abstract fun getOpenPeriod(): DrawPeriodEntity?

    @Query("SELECT * FROM draw_periods WHERE status = 'PREPARED' ORDER BY scheduledOpenEpochMillis ASC LIMIT 1")
    abstract fun getPreparedPeriod(): DrawPeriodEntity?

    @Query("""
        SELECT * FROM draw_periods
        WHERE status = 'PREPARED' AND scheduledOpenEpochMillis IS NOT NULL
          AND scheduledOpenEpochMillis <= :nowEpochMillis
        ORDER BY scheduledOpenEpochMillis ASC LIMIT 1
    """)
    abstract fun getDuePreparedPeriod(nowEpochMillis: Long): DrawPeriodEntity?

    @Query("""
        UPDATE draw_periods
        SET status = 'CLOSED', salesCloseEpochMillis = :closedAtEpochMillis
        WHERE periodId = :periodId AND status = 'OPEN'
    """)
    abstract fun closeOpenPeriod(periodId: String, closedAtEpochMillis: Long): Int

    @Query("""
        UPDATE draw_periods SET status = 'OPEN'
        WHERE periodId = :periodId AND status = 'PREPARED'
          AND scheduledOpenEpochMillis IS NOT NULL
          AND scheduledOpenEpochMillis <= :nowEpochMillis
          AND NOT EXISTS (SELECT 1 FROM draw_periods WHERE status = 'OPEN')
    """)
    abstract fun openPreparedPeriod(periodId: String, nowEpochMillis: Long): Int

    @Transaction
    open fun closePeriod(periodId: String, closedAtEpochMillis: Long) {
        require(closeOpenPeriod(periodId, closedAtEpochMillis) == 1) {
            "Only an open period can be closed"
        }
    }

    @Transaction
    open fun openDuePeriod(nowEpochMillis: Long): DrawPeriodEntity? {
        val prepared = getDuePreparedPeriod(nowEpochMillis) ?: return null
        if (openPreparedPeriod(prepared.periodId, nowEpochMillis) != 1) return null
        return getPeriod(prepared.periodId)
    }

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

        // Explicitly rejected numbers are separate from monetary limit rules.
        lines.forEach { line ->
            val blockCategory = when {
                line.numberText.length == 2 -> "TWO_DIGIT"
                line.betCategory == "THREE_TOAD" -> "THREE_TOAD"
                else -> "THREE_STRAIGHT"
            }
            require(getActiveNumberBlockRule(document.periodId, blockCategory, line.numberText) == null) {
                "Number is not accepted: ${line.numberText} / $blockCategory"
            }
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
    abstract fun insertNumberBlockRule(rule: NumberBlockRuleEntity)

    @Update
    abstract fun updateNumberBlockRule(rule: NumberBlockRuleEntity)

    @Query("""
        SELECT * FROM number_block_rules
        WHERE periodId = :periodId AND blockCategory = :blockCategory
          AND numberText = :numberText LIMIT 1
    """)
    abstract fun getNumberBlockRule(
        periodId: String, blockCategory: String, numberText: String
    ): NumberBlockRuleEntity?

    @Query("""
        SELECT * FROM number_block_rules
        WHERE periodId = :periodId AND blocked = 1
        ORDER BY blockCategory, numberText
    """)
    abstract fun getActiveNumberBlockRules(periodId: String): List<NumberBlockRuleEntity>

    @Query("""
        SELECT * FROM number_block_rules
        WHERE periodId = :periodId
        ORDER BY blockCategory, numberText
    """)
    abstract fun getAllNumberBlockRules(periodId: String): List<NumberBlockRuleEntity>

    @Query("""
        SELECT * FROM number_block_rules
        WHERE periodId = :periodId AND blockCategory = :blockCategory
          AND numberText = :numberText AND blocked = 1 LIMIT 1
    """)
    abstract fun getActiveNumberBlockRule(
        periodId: String, blockCategory: String, numberText: String
    ): NumberBlockRuleEntity?

    @Transaction
    open fun setNumberBlocked(
        ruleId: String,
        periodId: String,
        blockCategory: String,
        numberText: String,
        blocked: Boolean,
        changedAtEpochMillis: Long,
        auditEventId: String
    ): Boolean {
        require(ruleId.isNotBlank() && auditEventId.isNotBlank()) { "Rule and audit IDs are required" }
        require(numberText.all { it in '0'..'9' } &&
            ((blockCategory == "TWO_DIGIT" && numberText.length == 2) ||
             ((blockCategory == "THREE_STRAIGHT" || blockCategory == "THREE_TOAD") && numberText.length == 3))) {
            "Blocked number does not match its category"
        }
        val period = getPeriod(periodId) ?: error("Draw period not found")
        require(period.status == "OPEN" || period.status == "PREPARED") {
            "Blocked numbers can only be changed for an open or prepared period"
        }
        val existing = getNumberBlockRule(periodId, blockCategory, numberText)
        if (existing == null && !blocked) return false
        if (existing != null && existing.blocked == blocked) return false

        if (existing == null) {
            insertNumberBlockRule(NumberBlockRuleEntity(
                ruleId, periodId, blockCategory, numberText, true,
                changedAtEpochMillis, changedAtEpochMillis
            ))
        } else {
            updateNumberBlockRule(existing.copy(
                blocked = blocked,
                updatedAtEpochMillis = changedAtEpochMillis
            ))
        }
        insertAuditEvent(AuditEventEntity(
            auditEventId, "NUMBER_BLOCK_RULE", existing?.ruleId ?: ruleId,
            if (blocked) "BLOCKED" else "UNBLOCKED",
            "$blockCategory:$numberText",
            changedAtEpochMillis
        ))
        return true
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertLimitRule(rule: NumberLimitRuleEntity)

    @Update
    abstract fun updateLimitRule(rule: NumberLimitRuleEntity)

    @Query("""
        SELECT * FROM number_limit_rules
        WHERE periodId = :periodId AND numberLength = :numberLength
          AND betCategory = :betCategory AND numberText = :numberText
        LIMIT 1
    """)
    abstract fun getLimitRuleForManagement(
        periodId: String, numberLength: Int, betCategory: String, numberText: String
    ): NumberLimitRuleEntity?

    @Transaction
    open fun saveLimitRule(rule: NumberLimitRuleEntity) {
        require(rule.ruleId.isNotBlank()) { "Limit rule ID is required" }
        require(rule.numberText.length in 2..3 && rule.numberText.all { it in '0'..'9' }) {
            "Limit number must contain 2 or 3 ASCII digits"
        }
        val validCategories = if (rule.numberLength == 2) {
            setOf("TWO_TOP", "TWO_BOTTOM")
        } else if (rule.numberLength == 3) {
            setOf("THREE_TOP", "THREE_BOTTOM", "THREE_TOAD")
        } else {
            emptySet()
        }
        require(rule.numberLength == rule.numberText.length && rule.betCategory in validCategories) {
            "Limit category does not match number length"
        }
        require(rule.maximumAmountSatang > 0L) { "Limit amount must be positive" }
        val period = getPeriod(rule.periodId) ?: error("Draw period not found")
        require(period.status == "OPEN" || period.status == "PREPARED") { "Limits can only be changed for an open or prepared period" }

        val sold = getSoldAmountForNumber(rule.periodId, rule.numberText, rule.betCategory)
        require(rule.maximumAmountSatang >= sold) {
            "Limit cannot be lower than the amount already sold ($sold satang)"
        }

        val existing = getLimitRuleForManagement(
            rule.periodId, rule.numberLength, rule.betCategory, rule.numberText
        )
        if (existing == null) {
            insertLimitRule(rule)
        } else {
            updateLimitRule(existing.copy(
                maximumAmountSatang = rule.maximumAmountSatang,
                enabled = rule.enabled
            ))
        }
    }

    @Query("SELECT * FROM number_limit_rules WHERE periodId = :periodId ORDER BY numberLength, betCategory, numberText")
    abstract fun getLimitRules(periodId: String): List<NumberLimitRuleEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertDrawResult(result: DrawResultEntity)

    @Query("SELECT * FROM draw_results WHERE periodId = :periodId ORDER BY importedAtEpochMillis DESC")
    abstract fun getDrawResults(periodId: String): List<DrawResultEntity>

    @Query("SELECT * FROM audit_events WHERE entityId = :entityId ORDER BY createdAtEpochMillis")
    abstract fun getAuditEvents(entityId: String): List<AuditEventEntity>
}
