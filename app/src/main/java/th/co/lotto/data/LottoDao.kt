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
        UPDATE draw_periods SET status = 'OPEN', openedAtEpochMillis = scheduledOpenEpochMillis
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

    @Transaction
    open fun postCustomer(customer: CustomerEntity, audit: AuditEventEntity) {
        require(customer.customerId.isNotBlank()) { "Customer ID is required" }
        require(customer.displayName.isNotBlank()) { "Customer name is required" }
        require(customer.customerType in setOf("CASH", "CREDIT")) { "Unknown customer type" }
        if (customer.customerType == "CREDIT") {
            require(!customer.phone.isNullOrBlank()) { "Credit customer phone is required" }
            val normalizedPhone = customer.phone.filter { it.isDigit() }
            require(normalizedPhone.length in 8..15) { "Credit customer phone must contain 8-15 digits" }
            require(!customer.identityNote.isNullOrBlank()) { "Credit customer identity details are required" }
        }
        require(audit.eventId.isNotBlank() && audit.entityType == "CUSTOMER" &&
            audit.entityId == customer.customerId && audit.action == "CREATED") {
            "Customer audit event does not match the new customer"
        }
        insertCustomer(customer)
        insertAuditEvent(audit)
    }

    @Query("SELECT * FROM customers ORDER BY displayName COLLATE NOCASE")
    abstract fun getCustomers(): List<CustomerEntity>

    @Query("""
        SELECT * FROM customers
        WHERE customerType = :customerType
          AND (:searchText = '' OR displayName LIKE '%' || :searchText || '%'
               OR COALESCE(phone, '') LIKE '%' || :searchText || '%'
               OR COALESCE(identityNote, '') LIKE '%' || :searchText || '%')
        ORDER BY displayName COLLATE NOCASE
    """)
    abstract fun searchCustomers(customerType: String, searchText: String): List<CustomerEntity>

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
          AND NOT EXISTS (SELECT 1 FROM sale_cancellations sc WHERE sc.documentId = sd.documentId)
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
        require(document.correctionOfDocumentId == null) {
            "Confirmed bills cannot be corrected; cancel the bill and create a new independent bill"
        }
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
        val openedAt = period.openedAtEpochMillis ?: period.createdAtEpochMillis
        require(document.createdAtEpochMillis >= openedAt) {
            "Backdated sale is not allowed before this period opened"
        }
        require(document.createdAtEpochMillis <= System.currentTimeMillis()) {
            "Sale timestamp cannot be in the future"
        }
        val saleZone = runCatching { java.time.ZoneId.of(document.deviceTimeZone) }
            .getOrDefault(java.time.ZoneId.systemDefault())
        val scheduledClose = period.drawDateEpochMillis?.let { drawDate ->
            java.time.Instant.ofEpochMilli(drawDate).atZone(saleZone).toLocalDate()
                .atTime(java.time.LocalTime.of(14, 0)).atZone(saleZone).toInstant().toEpochMilli()
        }
        require(scheduledClose == null || System.currentTimeMillis() < scheduledClose) {
            "Sales cutoff time has passed"
        }
        require(document.correctionOfDocumentId == null) {
            "Editing or correcting a confirmed bill is disabled; cancel it and create a new bill"
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
            val blockCategory = line.betCategory
            require(getActiveNumberBlockRule(document.periodId, blockCategory, line.numberText) == null) {
                "Number is not accepted: ${line.numberText} / $blockCategory"
            }
        }

        // Aggregate each number/category once to avoid repeatedly adding a full batch.
        val incomingByNumberAndCategory = lines.groupBy { it.numberText to it.betCategory }
        incomingByNumberAndCategory.forEach { (key, groupedLines) ->
            val (number, category) = key
            require(number.length == if (category == "TWO_TOP" || category == "TWO_BOTTOM") 2 else 3) { "Number length does not match category" }
            require(number.all { it in '0'..'9' }) { "Number must contain ASCII digits only" }
            val validCategories = setOf("TWO_TOP", "TWO_BOTTOM", "THREE_TOP", "THREE_TOAD")
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

        val persistedLines = lines.map { line ->
            val rule = getLimitRuleForManagement(
                document.periodId, line.numberText.length, line.betCategory, line.numberText
            )
            line.copy(halfPayout = rule?.halfPayout == true)
        }
        insertSaleDocument(document)
        insertSaleLines(persistedLines)
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
    abstract fun insertSaleCancellation(cancellation: SaleCancellationEntity)

    @Query("SELECT * FROM sale_cancellations WHERE documentId = :documentId LIMIT 1")
    abstract fun getSaleCancellation(documentId: String): SaleCancellationEntity?

    @Query("""
        SELECT sc.* FROM sale_cancellations sc
        INNER JOIN sale_documents sd ON sd.documentId = sc.documentId
        WHERE sd.periodId = :periodId
        ORDER BY sc.cancelledAtEpochMillis DESC
    """)
    abstract fun getSaleCancellationsForPeriod(periodId: String): List<SaleCancellationEntity>

    @Transaction
    open fun cancelSaleDocument(cancellation: SaleCancellationEntity, audit: AuditEventEntity) {
        require(cancellation.cancellationId.isNotBlank()) { "Cancellation ID is required" }
        require(cancellation.reason.isNotBlank()) { "Cancellation reason is required" }
        val document = getSaleDocument(cancellation.documentId) ?: error("Sale document not found")
        require(getSaleCancellation(document.documentId) == null) { "This sale bill has already been cancelled" }
        require(cancellation.originalAmountSatang == document.totalAmountSatang) { "Cancellation amount does not match the original bill" }
        require(getPaymentsTotalForDocument(document.documentId) == 0L) {
            "This credit bill has posted payments; a payment reversal workflow is required before cancellation"
        }
        require(getDrawResults(document.periodId).isEmpty() && getSettlementSnapshot(document.periodId) == null) {
            "A bill cannot be cancelled after results or a settlement snapshot have been recorded"
        }
        require(audit.eventId.isNotBlank() && audit.entityType == "SALE_CANCELLATION" &&
            audit.entityId == cancellation.cancellationId && audit.action == "CANCELLED") {
            "Cancellation audit event does not match the cancellation record"
        }
        insertSaleCancellation(cancellation)
        insertAuditEvent(audit)
    }

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
        require(getSaleCancellation(document.documentId) == null) { "Cannot post payment to a cancelled bill" }
        require(document.customerType == "CREDIT") { "Payments can only be applied to credit documents" }
        require(document.customerId == payment.customerId) { "Payment customer does not match sale document" }

        val alreadyPaid = getPaymentsTotalForDocument(payment.documentId)
        val projectedPaid = Math.addExact(alreadyPaid, payment.amountSatang)
        require(projectedPaid <= document.totalAmountSatang) { "Payment exceeds document balance" }

        insertPayment(payment)
        insertAuditEvent(audit)
    }

    @Query("""
        SELECT COALESCE(SUM(sd.totalAmountSatang), 0) FROM sale_documents sd
        WHERE sd.customerId = :customerId AND sd.customerType = 'CREDIT'
          AND NOT EXISTS (SELECT 1 FROM sale_cancellations sc WHERE sc.documentId = sd.documentId)
    """)
    abstract fun getCreditSalesTotal(customerId: String): Long

    @Query("SELECT COALESCE(SUM(amountSatang), 0) FROM payment_entries WHERE customerId = :customerId")
    abstract fun getPaymentsTotal(customerId: String): Long

    @Query("""
        SELECT sd.* FROM sale_documents sd
        WHERE sd.periodId = :periodId
          AND NOT EXISTS (SELECT 1 FROM sale_cancellations sc WHERE sc.documentId = sd.documentId)
        ORDER BY sd.createdAtEpochMillis DESC
    """)
    abstract fun getSalesForPeriod(periodId: String): List<SaleDocumentEntity>

    @Query("SELECT * FROM sale_lines WHERE documentId = :documentId ORDER BY lineId")
    abstract fun getLinesForDocument(documentId: String): List<SaleLineEntity>

    @Query("SELECT COUNT(*) FROM sale_documents WHERE documentId = :documentId")
    abstract fun documentExists(documentId: String): Int

    @Query("SELECT * FROM payment_entries WHERE customerId = :customerId ORDER BY paidAtEpochMillis DESC")
    abstract fun getPaymentsForCustomer(customerId: String): List<PaymentEntryEntity>

    @Query("SELECT * FROM payment_entries WHERE documentId = :documentId ORDER BY paidAtEpochMillis DESC")
    abstract fun getPaymentsForDocument(documentId: String): List<PaymentEntryEntity>

    @Query("SELECT COUNT(*) FROM payment_entries WHERE paymentId = :paymentId")
    abstract fun paymentExists(paymentId: String): Int

    @Query("""
        SELECT sd.* FROM sale_documents sd
        WHERE sd.customerId = :customerId AND sd.customerType = 'CREDIT'
          AND NOT EXISTS (SELECT 1 FROM sale_cancellations sc WHERE sc.documentId = sd.documentId)
        ORDER BY sd.createdAtEpochMillis DESC
    """)
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
          AND blockCategory IN ('TWO_TOP', 'TWO_BOTTOM', 'THREE_TOP', 'THREE_TOAD')
        ORDER BY blockCategory, numberText
    """)
    abstract fun getActiveNumberBlockRules(periodId: String): List<NumberBlockRuleEntity>

    @Query("""
        SELECT * FROM number_block_rules
        WHERE periodId = :periodId
          AND blockCategory IN ('TWO_TOP', 'TWO_BOTTOM', 'THREE_TOP', 'THREE_TOAD')
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
            ((blockCategory in setOf("THREE_TOP", "THREE_TOAD") && numberText.length == 3) || (blockCategory in setOf("TWO_TOP", "TWO_BOTTOM") && numberText.length == 2))) {
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
        val validCategories = setOf("TWO_TOP", "TWO_BOTTOM", "THREE_TOP", "THREE_TOAD")
        val expectedLength = if (rule.betCategory == "TWO_TOP" || rule.betCategory == "TWO_BOTTOM") 2 else 3
        require(rule.numberText.length == expectedLength && rule.numberText.all { it in '0'..'9' }) {
            "Limit number length does not match category"
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
                enabled = rule.enabled,
                halfPayout = rule.halfPayout
            ))
        }
    }

    @Query("SELECT * FROM number_limit_rules WHERE periodId = :periodId AND betCategory IN ('TWO_TOP', 'TWO_BOTTOM', 'THREE_TOP', 'THREE_TOAD') ORDER BY numberLength, betCategory, numberText")
    abstract fun getLimitRules(periodId: String): List<NumberLimitRuleEntity>

    @Query("SELECT * FROM payout_rate_settings WHERE settingsId = 1 LIMIT 1")
    abstract fun getPayoutRateSettings(): PayoutRateSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertPayoutRateSettings(settings: PayoutRateSettingsEntity)

    @Update
    abstract fun updatePayoutRateSettings(settings: PayoutRateSettingsEntity)

    @Transaction
    open fun savePayoutRateSettings(settings: PayoutRateSettingsEntity, audit: AuditEventEntity): PayoutRateSettingsEntity {
        require(settings.settingsId == 1) { "Only the singleton payout settings row is supported" }
        require(listOf(
            settings.twoDigitRateHundredths,
            settings.twoTopRateHundredths,
            settings.twoBottomRateHundredths,
            settings.threeTopRateHundredths,
            settings.threeBottomRateHundredths,
            settings.threeToadRateHundredths
        ).all { it > 0L }) { "Every payout rate must be greater than zero" }
        require(audit.eventId.isNotBlank() && audit.entityType == "PAYOUT_SETTINGS" &&
            audit.entityId == "1" && audit.action == "UPDATED") {
            "Payout settings audit event is invalid"
        }
        val existing = getPayoutRateSettings()
        val saved = settings.copy(
            configVersion = Math.addExact(existing?.configVersion ?: 0L, 1L)
        )
        if (existing == null) insertPayoutRateSettings(saved) else updatePayoutRateSettings(saved)
        insertAuditEvent(audit.copy(details = "Payout configuration version ${saved.configVersion}"))
        return saved
    }

    @Query("SELECT * FROM settlement_snapshots WHERE periodId = :periodId LIMIT 1")
    abstract fun getSettlementSnapshot(periodId: String): SettlementSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertSettlementSnapshot(snapshot: SettlementSnapshotEntity)

    @Transaction
    open fun createSettlementSnapshot(snapshot: SettlementSnapshotEntity, audit: AuditEventEntity) {
        require(snapshot.settlementId.isNotBlank()) { "Settlement ID is required" }
        val period = getPeriod(snapshot.periodId) ?: error("Draw period not found")
        require(period.status == "CLOSED") { "Settlement snapshot requires a closed period" }
        require(isResultVerificationComplete(snapshot.periodId)) { "Two-source result verification or manual adjudication is required before settlement" }
        require(getDrawResults(snapshot.periodId).isNotEmpty()) { "Draw results are required before settlement" }
        require(getSettlementSnapshot(snapshot.periodId) == null) { "Settlement snapshot already exists for this period" }
        val rates = getPayoutRateSettings() ?: error("Payout rates must be configured before settlement")
        require(snapshot.twoDigitRateHundredths == rates.twoDigitRateHundredths &&
            snapshot.twoTopRateHundredths == rates.twoTopRateHundredths &&
            snapshot.twoBottomRateHundredths == rates.twoBottomRateHundredths &&
            snapshot.threeTopRateHundredths == rates.threeTopRateHundredths &&
            snapshot.threeBottomRateHundredths == rates.threeBottomRateHundredths &&
            snapshot.threeToadRateHundredths == rates.threeToadRateHundredths &&
            snapshot.payoutConfigVersion == rates.configVersion) {
            "Settlement snapshot must exactly copy the current payout configuration"
        }
        require(audit.eventId.isNotBlank() && audit.entityType == "SETTLEMENT" &&
            audit.entityId == snapshot.settlementId && audit.action == "SNAPSHOTTED") {
            "Settlement audit event does not match the snapshot"
        }
        insertSettlementSnapshot(snapshot)
        insertAuditEvent(audit)
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertDrawResult(result: DrawResultEntity)

    @Transaction
    open fun postDrawResult(result: DrawResultEntity, audit: AuditEventEntity) {
        require(result.resultId.isNotBlank()) { "Result ID is required" }
        require(result.resultType.isNotBlank()) { "Result type is required" }
        require(result.winningNumber.isNotBlank() && result.winningNumber.all { it in '0'..'9' }) {
            "Winning number must contain digits only"
        }
        val expectedLength = when (result.resultType.trim().uppercase(java.util.Locale.ROOT)) {
            "THREE_TOP", "3 ตัวบน" -> 3
            "TWO_BOTTOM", "2 ตัวล่าง" -> 2
            else -> 0
        }
        require(expectedLength > 0 && result.winningNumber.length == expectedLength) {
            "Only GLO first-prize top and official last-two results are supported"
        }
        require(result.sourceLabel.startsWith("AUTO-COMPARED") || result.sourceLabel.startsWith("MANUAL-GLO")) { "Result must be confirmed by matching public sources or manual GLO verification" }
        val period = getPeriod(result.periodId) ?: error("Draw period not found")
        require(period.status == "CLOSED") { "Results can only be recorded for a closed draw period" }
        require(audit.eventId.isNotBlank() && audit.entityType == "DRAW_RESULT" &&
            audit.entityId == result.resultId && audit.action == "POSTED") {
            "Result audit event does not match the result"
        }
        insertDrawResult(result)
        insertAuditEvent(audit)
    }

    @Transaction
    open fun postGloResults(
        topResult: DrawResultEntity,
        bottomResult: DrawResultEntity,
        topAudit: AuditEventEntity,
        bottomAudit: AuditEventEntity
    ) {
        require(topResult.periodId == bottomResult.periodId) { "GLO results must belong to the same draw period" }
        require(topResult.resultType == "THREE_TOP" && bottomResult.resultType == "TWO_BOTTOM") {
            "GLO import must contain first-prize top and official last-two results"
        }
        postDrawResult(topResult, topAudit)
        postDrawResult(bottomResult, bottomAudit)
    }


    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertDrawSourceCheckRow(check: DrawSourceCheckEntity)

    @Transaction
    open fun insertDrawSourceCheck(check: DrawSourceCheckEntity) {
        require(check.checkId.isNotBlank()) { "Source check ID is required" }
        require(check.sourceId in setOf("LOTTERYCHECKS", "HUAYREVIEW")) { "Unknown result source" }
        require(check.firstPrizeNumber.length == 6 && check.firstPrizeNumber.all { it in '0'..'9' }) { "Invalid first-prize number" }
        require(check.threeTopNumber == check.firstPrizeNumber.takeLast(3)) { "Derived three-top number is invalid" }
        require(check.twoBottomNumber.length == 2 && check.twoBottomNumber.all { it in '0'..'9' }) { "Invalid last-two number" }
        require(check.drawDateIso.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "Invalid draw date" }
        require(check.sourceUrl.startsWith("https://")) { "Source URL must use HTTPS" }
        require(check.fetchedAtEpochMillis > 0L) { "Source check timestamp is invalid" }
        val period = getPeriod(check.periodId) ?: error("Draw period not found")
        require(period.status == "CLOSED") { "Only closed periods can be checked" }
        require(check.drawDateIso == expectedDrawDateIso(check.periodId)) { "Source draw date does not match this period" }
        insertDrawSourceCheckRow(check)
    }

    private fun expectedDrawDateIso(periodId: String): String {
        val millis = getPeriod(periodId)?.drawDateEpochMillis ?: error("Draw date is not configured")
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Bangkok")
        }.format(java.util.Date(millis))
    }

    @Query("SELECT * FROM draw_source_checks WHERE periodId = :periodId ORDER BY fetchedAtEpochMillis DESC")
    abstract fun getDrawSourceChecks(periodId: String): List<DrawSourceCheckEntity>

    @Transaction
    open fun postComparedResults(
        periodId: String,
        topResult: DrawResultEntity,
        bottomResult: DrawResultEntity,
        topAudit: AuditEventEntity,
        bottomAudit: AuditEventEntity
    ) {
        val latest = getDrawSourceChecks(periodId).groupBy { it.sourceId }
            .mapValues { (_, rows) -> rows.maxByOrNull { it.fetchedAtEpochMillis }!! }
        val first = latest["LOTTERYCHECKS"] ?: error("LotteryChecks result is missing")
        val second = latest["HUAYREVIEW"] ?: error("HuayReview result is missing")
        require(first.drawDateIso == second.drawDateIso && first.drawDateIso == expectedDrawDateIso(periodId) &&
            first.firstPrizeNumber == second.firstPrizeNumber && first.twoBottomNumber == second.twoBottomNumber) {
            "Public source results do not match; settlement is blocked"
        }
        require(topResult.periodId == periodId && bottomResult.periodId == periodId)
        require(topResult.resultType == "THREE_TOP" && topResult.winningNumber == first.threeTopNumber)
        require(bottomResult.resultType == "TWO_BOTTOM" && bottomResult.winningNumber == first.twoBottomNumber)
        postDrawResult(topResult, topAudit)
        postDrawResult(bottomResult, bottomAudit)
    }

    @Transaction
    open fun postManualVerifiedResults(
        periodId: String,
        firstPrizeNumber: String,
        twoBottomNumber: String,
        topAudit: AuditEventEntity,
        bottomAudit: AuditEventEntity,
        now: Long
    ) {
        require(firstPrizeNumber.length == 6 && firstPrizeNumber.all { it in '0'..'9' }) { "รางวัลที่ 1 ต้องมี 6 หลัก" }
        require(twoBottomNumber.length == 2 && twoBottomNumber.all { it in '0'..'9' }) { "เลขท้าย 2 ตัวต้องมี 2 หลัก" }
        val latest = getDrawSourceChecks(periodId).groupBy { it.sourceId }
            .mapValues { (_, rows) -> rows.maxByOrNull { it.fetchedAtEpochMillis }!! }
        val first = latest["LOTTERYCHECKS"] ?: error("ยังไม่มีผลจาก LotteryChecks")
        val second = latest["HUAYREVIEW"] ?: error("ยังไม่มีผลจาก HuayReview")
        require(first.drawDateIso == second.drawDateIso && first.drawDateIso == expectedDrawDateIso(periodId) &&
            (first.firstPrizeNumber != second.firstPrizeNumber || first.twoBottomNumber != second.twoBottomNumber)) {
            "ไม่สามารถกรอกผลแทนได้เมื่อสองเว็บไซต์ตรงกัน"
        }
        val top = DrawResultEntity(java.util.UUID.randomUUID().toString(), periodId, "THREE_TOP",
            firstPrizeNumber.takeLast(3), "MANUAL-GLO • ผู้ใช้ตรวจสอบจากเว็บไซต์ GLO ด้วยตนเอง • รางวัลที่ 1 " + firstPrizeNumber, now, now)
        val bottom = DrawResultEntity(java.util.UUID.randomUUID().toString(), periodId, "TWO_BOTTOM",
            twoBottomNumber, "MANUAL-GLO • ผู้ใช้ตรวจสอบจากเว็บไซต์ GLO ด้วยตนเอง", now, now)
        postDrawResult(top, topAudit.copy(entityId = top.resultId,
            details = "Manual GLO verification; first prize=" + firstPrizeNumber + "; THREE_TOP=" + firstPrizeNumber.takeLast(3) + "; TWO_BOTTOM=" + twoBottomNumber))
        postDrawResult(bottom, bottomAudit.copy(entityId = bottom.resultId,
            details = "Manual GLO verification; TWO_BOTTOM=" + twoBottomNumber))
    }

    fun isResultVerificationComplete(periodId: String): Boolean {
        val latest = getDrawSourceChecks(periodId).groupBy { it.sourceId }
            .mapValues { (_, rows) -> rows.maxByOrNull { it.fetchedAtEpochMillis }!! }
        val first = latest["LOTTERYCHECKS"] ?: return false
        val second = latest["HUAYREVIEW"] ?: return false
        if (first.drawDateIso != second.drawDateIso || runCatching { first.drawDateIso != expectedDrawDateIso(periodId) }.getOrDefault(true)) return false
        val results = getDrawResults(periodId)
        val latestCheckTime = maxOf(first.fetchedAtEpochMillis, second.fetchedAtEpochMillis)
        if (first.firstPrizeNumber == second.firstPrizeNumber && first.twoBottomNumber == second.twoBottomNumber) {
            val top = results.filter { it.resultType == "THREE_TOP" }.maxByOrNull { it.importedAtEpochMillis } ?: return false
            val bottom = results.filter { it.resultType == "TWO_BOTTOM" }.maxByOrNull { it.importedAtEpochMillis } ?: return false
            return top.sourceLabel.startsWith("AUTO-COMPARED") &&
                bottom.sourceLabel.startsWith("AUTO-COMPARED") &&
                top.importedAtEpochMillis >= latestCheckTime && bottom.importedAtEpochMillis >= latestCheckTime
        }
        val top = results.filter { it.resultType == "THREE_TOP" }.maxByOrNull { it.importedAtEpochMillis } ?: return false
        val bottom = results.filter { it.resultType == "TWO_BOTTOM" }.maxByOrNull { it.importedAtEpochMillis } ?: return false
        return top.sourceLabel.startsWith("MANUAL-GLO") &&
            bottom.sourceLabel.startsWith("MANUAL-GLO") &&
            top.importedAtEpochMillis >= latestCheckTime && bottom.importedAtEpochMillis >= latestCheckTime
    }

    @Query("SELECT * FROM draw_results WHERE periodId = :periodId ORDER BY importedAtEpochMillis DESC")
    abstract fun getDrawResults(periodId: String): List<DrawResultEntity>

    @Query("SELECT * FROM audit_events WHERE entityId = :entityId ORDER BY createdAtEpochMillis")
    abstract fun getAuditEvents(entityId: String): List<AuditEventEntity>
}
