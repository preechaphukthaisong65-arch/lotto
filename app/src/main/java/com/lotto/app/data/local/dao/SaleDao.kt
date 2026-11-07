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
import com.lotto.app.domain.rules.PrizeType

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

    @Query("SELECT value FROM app_settings WHERE key = :key LIMIT 1")
    protected abstract suspend fun getSettingValue(key: String): String?

    @Query("""
        SELECT COUNT(*) FROM draw_dates d
        WHERE d.drawDate = :drawDate
          AND NOT EXISTS (SELECT 1 FROM draw_results r WHERE r.drawDate = d.drawDate)
          AND EXISTS (
              SELECT 1 FROM draw_dates previous
              WHERE previous.drawDate = (
                  SELECT MAX(p.drawDate) FROM draw_dates p WHERE p.drawDate < d.drawDate
              )
                AND :createdAt >= CAST(strftime('%s', date(previous.drawDate, '+1 day') || ' 08:00:00', '-7 hours') AS INTEGER) * 1000
                AND :createdAt < CAST(strftime('%s', d.drawDate || ' 14:30:00', '-7 hours') AS INTEGER) * 1000
          )
    """)
    protected abstract suspend fun isDrawSaleWindowOpen(drawDate: String, createdAt: Long): Int

    @Query("UPDATE credit_documents SET status = :status, paidAt = :paidAt WHERE creditDocumentId = :creditDocumentId")
    protected abstract suspend fun updateCreditStatus(creditDocumentId: Long, status: String, paidAt: Long?)

    @Query("""
        SELECT COALESCE(SUM(si.amount), 0)
        FROM sale_items si
        INNER JOIN sales s ON s.saleId = si.saleId
        WHERE s.drawDate = :drawDate
          AND s.status = 'CONFIRMED'
          AND si.prizeType = :prizeType
          AND si.number = :number
    """)
    abstract suspend fun getNumberStakeForDraw(
        drawDate: String,
        prizeType: String,
        number: String
    ): Long

    @Transaction
    open suspend fun insertSaleWithItems(
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        fullLimitsByPrizeType: Map<String, Long> = emptyMap()
    ): Long {
        val targetDrawDate = sale.drawDate
            ?: throw IllegalArgumentException("ไม่พบงวดหวยสำหรับบิลนี้")
        require(sale.totalAmount > 0L) { "ยอดขายต้องมากกว่า 0 บาท" }
        require(sale.cashAmount >= 0L && sale.creditAmount >= 0L) { "ยอดชำระต้องไม่ติดลบ" }
        require(Math.addExact(sale.cashAmount, sale.creditAmount) == sale.totalAmount) {
            "ยอดเงินสดและเงินเชื่อต้องรวมกันเท่ากับยอดขาย"
        }
        require(items.isNotEmpty()) { "ไม่พบรายการเลขที่ซื้อ" }
        require(items.all { it.amount > 0L }) { "จำนวนเงินแต่ละรายการต้องมากกว่า 0 บาท" }
        require(items.fold(0L) { total, item -> Math.addExact(total, item.amount) } == sale.totalAmount) {
            "ผลรวมรายการซื้อไม่ตรงกับยอดขาย"
        }
        if (sale.creditAmount > 0L) {
            require(sale.customerFirstName.isNotBlank() && sale.customerLastName.isNotBlank()) {
                "การขายเงินเชื่อต้องระบุชื่อและนามสกุลลูกค้า"
            }
        }
        items.forEach { item ->
            val type = runCatching { PrizeType.valueOf(item.prizeType) }
                .getOrElse { throw IllegalArgumentException("ไม่รู้จักประเภทหวย ${item.prizeType}") }
            val validNumber = when (type) {
                PrizeType.THREE_DIGIT_STRAIGHT, PrizeType.THREE_DIGIT_TOD ->
                    com.lotto.app.domain.rules.LottoRules.isValidThreeDigitNumber(item.number)
                PrizeType.TWO_DIGIT_TOP, PrizeType.TWO_DIGIT_BOTTOM ->
                    com.lotto.app.domain.rules.LottoRules.isValidTwoDigitNumber(item.number)
            }
            require(validNumber) { "เลข ${item.number} ไม่ถูกต้องสำหรับประเภท ${item.prizeType}" }
        }
        require(isDrawSaleWindowOpen(targetDrawDate, sale.createdAt) > 0) {
            "อยู่นอกเวลาเปิดรับขาย หรือมีการบันทึกผลรางวัลของงวดนี้แล้ว ไม่สามารถบันทึกบิลได้"
        }

        // No-accept rules are absolute and checked inside the sale transaction.
        items.forEach { item ->
            val blockedNumbers = getSettingValue("no_accept_${item.prizeType}")
                .orEmpty()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            require(item.number !in blockedNumbers) {
                "เลข ${item.number} ในหมวด ${item.prizeType} ถูกตั้งค่าไม่รับ ไม่สามารถบันทึกได้"
            }
        }

        // Persisted payout multipliers must match the current shared half-payout setting.
        items.forEach { item ->
            val type = PrizeType.valueOf(item.prizeType)
            val halfNumbers = getSettingValue("half_payout_${item.prizeType}").orEmpty()
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            val expectedMultiplier = if (item.number in halfNumbers) type.fullPayoutMultiplier / 2L else type.fullPayoutMultiplier
            require(item.payoutMultiplier == expectedMultiplier) {
                "อัตราจ่ายของเลข ${item.number} เปลี่ยนแปลง กรุณาตรวจสอบรายการก่อนบันทึก"
            }
        }

        // Check each number/type pair inside the same transaction as insertion.
        // Grouping also prevents duplicate rows in one bill from bypassing the cap.
        items.groupBy { it.prizeType to it.number }.forEach { (key, requestedItems) ->
            // The persisted setting is authoritative; the UI-supplied map is only a fallback.
            val limit = getSettingValue("full_limit_${key.first}")?.toLongOrNull()?.coerceAtLeast(0L)
                ?: fullLimitsByPrizeType[key.first]?.coerceAtLeast(0L)
                ?: 0L
            if (limit > 0L) {
                val alreadySold = getNumberStakeForDraw(sale.drawDate.orEmpty(), key.first, key.second)
                val requested = requestedItems.fold(0L) { total, item -> Math.addExact(total, item.amount) }
                val combined = Math.addExact(alreadySold, requested)
                require(combined <= limit) {
                    "เลข ${key.second} ในหมวด ${key.first} เต็มแล้ว (รับได้สูงสุด ${limit} บาท/งวด, สะสมเดิม ${alreadySold} บาท, รายการนี้ ${requested} บาท)"
                }
            }
        }

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
        INSERT OR IGNORE INTO expired_sale_retention (saleId, eligibleAt)
        SELECT sourceSaleId, paidAt
        FROM credit_documents
        WHERE status = 'PAID'
          AND paidAt IS NOT NULL
          AND paidAt <= :cutoffTimestamp
    """)
    protected abstract suspend fun markExpiredSalesForRetention(cutoffTimestamp: Long)

    @Query("""
        DELETE FROM credit_documents
        WHERE status = 'PAID'
          AND paidAt IS NOT NULL
          AND paidAt <= :cutoffTimestamp
    """)
    protected abstract suspend fun deleteExpiredPaidDocumentsInternal(cutoffTimestamp: Long): Int

    @Query("""
        DELETE FROM sales
        WHERE saleId IN (
            SELECT retention.saleId
            FROM expired_sale_retention retention
            WHERE NOT EXISTS (
                SELECT 1 FROM winning_sale_items wi WHERE wi.saleId = retention.saleId
            )
        )
    """)
    protected abstract suspend fun deleteExpiredSourceSalesWithoutWinningHistory(): Int

    @Query("""
        DELETE FROM expired_sale_retention
        WHERE saleId NOT IN (SELECT saleId FROM sales)
    """)
    protected abstract suspend fun deleteCompletedRetentionMarkers(): Int

    @Transaction
    open suspend fun deleteExpiredPaidDocuments(cutoffTimestamp: Long): Int {
        markExpiredSalesForRetention(cutoffTimestamp)
        val deletedDocuments = deleteExpiredPaidDocumentsInternal(cutoffTimestamp)
        deleteExpiredSourceSalesWithoutWinningHistory()
        deleteCompletedRetentionMarkers()
        return deletedDocuments
    }
}
