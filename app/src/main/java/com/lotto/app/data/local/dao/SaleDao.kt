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
import com.lotto.app.data.local.entity.SaleVoidEventEntity
import com.lotto.app.data.local.entity.CustomerEntity
import java.util.UUID
import com.lotto.app.domain.rules.PrizeType
import com.lotto.app.domain.rules.PrizeExposureEntry
import com.lotto.app.domain.rules.PrizeExposurePolicy

@Dao
abstract class SaleDao {
    @Insert
    protected abstract suspend fun insertSale(sale: SaleEntity): Long

    @Insert
    protected abstract suspend fun insertCustomer(customer: CustomerEntity): Long

    @Query("SELECT * FROM customers WHERE customerId = :customerId LIMIT 1")
    protected abstract suspend fun getCustomerById(customerId: Long): CustomerEntity?

    @Insert
    protected abstract suspend fun insertSaleItems(items: List<SaleItemEntity>)

    @Insert
    protected abstract suspend fun insertCreditDocument(document: CreditDocumentEntity): Long

    @Insert
    protected abstract suspend fun insertCreditDocumentItems(items: List<CreditDocumentItemEntity>)

    @Insert
    protected abstract suspend fun insertCreditPayment(payment: CreditPaymentEntity): Long

    @Insert
    protected abstract suspend fun insertSaleVoidEvent(event: SaleVoidEventEntity): Long

    @Query("SELECT * FROM credit_documents WHERE sourceSaleId = :saleId LIMIT 1")
    protected abstract suspend fun getCreditDocumentForSale(saleId: Long): CreditDocumentEntity?

    @Query("SELECT COUNT(*) FROM credit_payments WHERE creditDocumentId = :creditDocumentId")
    protected abstract suspend fun countCreditPayments(creditDocumentId: Long): Int

    @Query("SELECT COUNT(*) FROM draw_results WHERE drawDate = :drawDate")
    protected abstract suspend fun countResultsForDraw(drawDate: String): Int

    @Query("SELECT COUNT(*) FROM winning_sale_items WHERE saleId = :saleId")
    protected abstract suspend fun countWinningItemsForSale(saleId: Long): Int

    @Query("UPDATE sales SET status = 'VOIDED' WHERE saleId = :saleId AND status = 'CONFIRMED'")
    protected abstract suspend fun markSaleVoided(saleId: Long): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM credit_payments WHERE creditDocumentId = :creditDocumentId")
    protected abstract suspend fun getTotalPayments(creditDocumentId: Long): Long

    @Query("SELECT value FROM app_settings WHERE key = :key LIMIT 1")
    protected abstract suspend fun getSettingValue(key: String): String?

    @Query("""
        INSERT INTO app_settings (key, value, updatedAt)
        VALUES (:key, :value, :updatedAt)
        ON CONFLICT(key) DO UPDATE SET value = excluded.value, updatedAt = excluded.updatedAt
    """)
    protected abstract suspend fun upsertSettingValue(key: String, value: String, updatedAt: Long)

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

    @Query("SELECT COALESCE(SUM(totalAmount), 0) FROM sales WHERE drawDate = :drawDate AND status = 'CONFIRMED'")
    protected abstract suspend fun getSalesTotalForDraw(drawDate: String): Long

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

    @Query("""
        SELECT si.* FROM sale_items si
        INNER JOIN sales s ON s.saleId = si.saleId
        WHERE s.drawDate = :drawDate AND s.status = 'CONFIRMED'
        ORDER BY si.itemId
    """)
    protected abstract suspend fun getAllItemsForDraw(drawDate: String): List<SaleItemEntity>

    private fun calculateMaximumPrizeExposure(items: List<SaleItemEntity>): Long =
        PrizeExposurePolicy.maximumPossiblePrizeLiability(
            items.map { item ->
                PrizeExposureEntry(
                    number = item.number,
                    prizeType = PrizeType.valueOf(item.prizeType),
                    stakeAmount = item.amount,
                    payoutMultiplier = item.payoutMultiplier
                )
            }
        )

    private suspend fun validateAndAdvanceClock(timestamp: Long) {
        require(timestamp > 0L) { "เวลาอุปกรณ์ไม่ถูกต้อง ไม่สามารถบันทึกรายการการเงินได้" }
        val lastSeenRaw = getSettingValue("clock_high_water_epoch_ms")
        val lastSeen = lastSeenRaw?.toLongOrNull()
            ?: if (lastSeenRaw == null) 0L else throw IllegalStateException("ข้อมูลตรวจสอบเวลาของระบบเสียหาย")
        require(timestamp >= lastSeen) {
            "ตรวจพบว่านาฬิกาเครื่องเดินย้อนเวลา ไม่สามารถบันทึกรายการการเงินได้"
        }
        upsertSettingValue("clock_high_water_epoch_ms", timestamp.toString(), timestamp)
    }


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

        val customerId = if (sale.customerId != null) {
            val existingCustomer = getCustomerById(sale.customerId)
                ?: throw IllegalArgumentException("ไม่พบรหัสลูกค้าที่เลือก กรุณาเลือกลูกค้าใหม่อีกครั้ง")
            require(existingCustomer.firstName.trim().equals(sale.customerFirstName.trim(), ignoreCase = true) &&
                existingCustomer.lastName.trim().equals(sale.customerLastName.trim(), ignoreCase = true)) {
                "ข้อมูลชื่อลูกค้าไม่ตรงกับรหัสลูกค้าที่เลือก กรุณาเลือกลูกค้าใหม่"
            }
            existingCustomer.customerId
        } else if (sale.customerFirstName.isNotBlank() || sale.customerLastName.isNotBlank()) {
            require(sale.customerFirstName.isNotBlank() && sale.customerLastName.isNotBlank()) {
                "กรุณากรอกชื่อและนามสกุลให้ครบก่อนสร้างรหัสลูกค้า"
            }
            insertCustomer(
                CustomerEntity(
                    customerCode = "CUS-" + UUID.randomUUID().toString().replace("-", "").take(12).uppercase(),
                    firstName = sale.customerFirstName.trim(),
                    lastName = sale.customerLastName.trim(),
                    phone = sale.customerPhone?.trim()?.takeIf { it.isNotBlank() },
                    createdAt = sale.createdAt,
                    updatedAt = sale.createdAt
                )
            )
        } else null
        if (sale.creditAmount > 0L) {
            require(customerId != null) { "การขายเงินเชื่อต้องเลือกลูกค้าหรือสร้างรหัสลูกค้าก่อน" }
        }
        val customer = customerId?.let { getCustomerById(it) }
        val saleToInsert = sale.copy(
            customerId = customerId,
            customerPhone = customer?.phone
        )
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

        // Persist a monotonic wall-clock high-water mark. If the device clock is
        // moved backwards after the app has observed a later time, fail closed.
        validateAndAdvanceClock(sale.createdAt)
        // Keep draw-level totals within Long capacity so later financial summaries cannot overflow.
        Math.addExact(getSalesTotalForDraw(targetDrawDate), sale.totalAmount)

        val exposureCapRaw = getSettingValue("risk_max_prize_liability_per_draw")
            ?: throw IllegalStateException("ยังไม่ได้กำหนดเพดานความเสี่ยงสูงสุดต่องวด กรุณาตั้งค่าก่อนขาย")
        val exposureCap = exposureCapRaw.toLongOrNull()
            ?: throw IllegalStateException("ค่าเพดานความเสี่ยงต่องวดไม่ถูกต้อง กรุณาตรวจสอบการตั้งค่า")
        require(exposureCap > 0L) { "เพดานความเสี่ยงต่องวดต้องมากกว่า 0 บาท จึงจะเปิดรับขายได้" }
        val existingItems = getAllItemsForDraw(targetDrawDate)
        val projectedExposure = calculateMaximumPrizeExposure(
            existingItems + items.map { it.copy(saleId = 0L) }
        )
        require(projectedExposure <= exposureCap) {
            "ยอดรางวัลสูงสุดที่อาจต้องจ่ายต่องวดจะเกินเพดานที่ตั้งไว้ (${projectedExposure} / ${exposureCap} บาท)"
        }

        if (sale.creditAmount > 0L) {
            val legacyOutstanding = getOutstandingCreditByLegacyName(
                sale.customerFirstName,
                sale.customerLastName
            )
            require(legacyOutstanding == 0L || sale.legacyDebtReviewed) {
                "พบยอดเชื่อค้างจากข้อมูลเดิมที่ยังไม่มีรหัสลูกค้า $legacyOutstanding บาท กรุณาตรวจสอบว่าเป็นบุคคลเดียวกันหรือไม่และยืนยันการตรวจสอบก่อนขายเชื่อ"
            }
            val creditCapRaw = getSettingValue("risk_max_credit_per_customer")
                ?: throw IllegalStateException("ยังไม่ได้กำหนดเพดานเครดิตต่อลูกค้า กรุณาตั้งค่าก่อนขายเงินเชื่อ")
            val creditCap = creditCapRaw.toLongOrNull()
                ?: throw IllegalStateException("ค่าเพดานเครดิตต่อลูกค้าไม่ถูกต้อง กรุณาตรวจสอบการตั้งค่า")
            require(creditCap > 0L) { "เพดานเครดิตต่อลูกค้าต้องมากกว่า 0 บาท" }
            val outstanding = getOutstandingCreditByCustomer(
                customerId ?: throw IllegalStateException("ไม่พบรหัสลูกค้าสำหรับตรวจสอบวงเงินเครดิต")
            )
            val projectedCredit = Math.addExact(outstanding, sale.creditAmount)
            require(projectedCredit <= creditCap) {
                "ยอดเครดิตของลูกค้ารายนี้จะเกินเพดาน (${projectedCredit} / ${creditCap} บาท)"
            }
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
            // A stored setting is authoritative. Never turn malformed or negative
            // persisted limits into zero (zero means unlimited), and never silently
            // fall back to a potentially stale UI value when storage is corrupt.
            val settingKey = "full_limit_${key.first}"
            val persistedLimit = getSettingValue(settingKey)
            val limit = if (persistedLimit != null) {
                persistedLimit.toLongOrNull()
                    ?: throw IllegalStateException("ค่าลิมิต $settingKey ในฐานข้อมูลไม่ถูกต้อง กรุณาตรวจสอบการตั้งค่าก่อนขาย")
            } else {
                fullLimitsByPrizeType[key.first]
                    ?: throw IllegalStateException("ยังไม่ได้กำหนดวงเงินรับซื้อสำหรับหมวด ${key.first} กรุณาตั้งค่าก่อนขาย")
            }
            require(limit > 0L) {
                "วงเงินรับซื้อหมวด ${key.first} ต้องมากกว่า 0 บาท ระบบปิดการขายไว้จนกว่าจะกำหนดวงเงินเพื่อป้องกันการรับซื้อไม่จำกัด"
            }
            val alreadySold = getNumberStakeForDraw(sale.drawDate.orEmpty(), key.first, key.second)
            val requested = requestedItems.fold(0L) { total, item -> Math.addExact(total, item.amount) }
            val combined = Math.addExact(alreadySold, requested)
            require(combined <= limit) {
                "เลข ${key.second} ในหมวด ${key.first} เต็มแล้ว (รับได้สูงสุด ${limit} บาท/งวด, สะสมเดิม ${alreadySold} บาท, รายการนี้ ${requested} บาท)"
            }
        }

        val saleId = insertSale(saleToInsert)
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
                paidAt = if (isCashSale) sale.createdAt else null,
                customerId = customerId,
                customerPhone = customer?.phone
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

    @Query("SELECT * FROM sale_void_events ORDER BY voidedAt DESC, voidId DESC")
    abstract suspend fun getAllSaleVoidEvents(): List<SaleVoidEventEntity>

    @Transaction
    open suspend fun voidConfirmedSale(
        saleId: Long,
        reason: String,
        voidedAt: Long,
        cashRefundConfirmed: Boolean
    ): SaleVoidEventEntity {
        require(reason.isNotBlank()) { "กรุณาระบุเหตุผลการยกเลิกบิล" }
        validateAndAdvanceClock(voidedAt)
        val sale = getSaleById(saleId) ?: throw IllegalArgumentException("ไม่พบบิลขายเลขที่ $saleId")
        require(sale.status == "CONFIRMED") { "บิลนี้ถูกยกเลิกไปแล้วหรือไม่อยู่ในสถานะที่ยกเลิกได้" }
        val drawDate = sale.drawDate ?: throw IllegalStateException("บิลเก่าไม่มีวันที่งวด จึงยกเลิกอัตโนมัติไม่ได้")
        require(countResultsForDraw(drawDate) == 0) {
            "งวดนี้บันทึกผลรางวัลแล้ว ไม่อนุญาตให้ยกเลิกบิลย้อนหลัง"
        }
        require(countWinningItemsForSale(saleId) == 0) {
            "บิลนี้มีรายการรางวัลแล้ว ไม่อนุญาตให้ยกเลิก"
        }
        val creditDocument = getCreditDocumentForSale(saleId)
            ?: throw IllegalStateException("ไม่พบเอกสารบัญชีเชื่อที่เชื่อมกับบิลนี้ จึงยกเลิกอย่างปลอดภัยไม่ได้")
        require(creditDocument.status == CreditDocumentEntity.STATUS_OPEN ||
            creditDocument.status == CreditDocumentEntity.STATUS_PAID) {
            "สถานะเอกสารบัญชีเชื่อไม่รองรับการยกเลิก"
        }
        require(countCreditPayments(creditDocument.creditDocumentId) == 0) {
            "บิลนี้มีประวัติรับชำระเงินเชื่อแล้ว กรุณาตรวจสอบและปิดบัญชีด้วยวิธีที่มีหลักฐานก่อนยกเลิก"
        }
        require(sale.cashAmount == 0L || cashRefundConfirmed) {
            "กรุณายืนยันว่าได้คืนเงินสดให้ลูกค้าครบตามยอดแล้วก่อนบันทึกยกเลิก"
        }

        val event = SaleVoidEventEntity(
            saleId = saleId,
            drawDate = drawDate,
            reason = reason.trim(),
            cashRefundAmount = sale.cashAmount,
            cancelledCreditAmount = sale.creditAmount,
            voidedAt = voidedAt
        )
        check(markSaleVoided(saleId) == 1) { "เปลี่ยนสถานะบิลเป็นยกเลิกไม่สำเร็จ" }
        updateCreditStatus(creditDocument.creditDocumentId, "VOIDED", null)
        val voidId = insertSaleVoidEvent(event)
        return event.copy(voidId = voidId)
    }

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId ORDER BY itemId ASC")
    abstract suspend fun getItemsForSale(saleId: Long): List<SaleItemEntity>

    @Query("SELECT * FROM credit_documents WHERE status = 'OPEN' ORDER BY createdAt DESC, creditDocumentId DESC")
    abstract suspend fun getOpenCreditDocuments(): List<CreditDocumentEntity>

    @Query("""
        SELECT COALESCE(SUM(d.creditAmount - COALESCE((
            SELECT SUM(p.amount) FROM credit_payments p
            WHERE p.creditDocumentId = d.creditDocumentId
        ), 0)), 0)
        FROM credit_documents d
        WHERE d.status = 'OPEN' AND d.customerId = :customerId
    """)
    abstract suspend fun getOutstandingCreditByCustomer(customerId: Long): Long

    @Query("""
        SELECT COALESCE(SUM(d.creditAmount - COALESCE((
            SELECT SUM(p.amount) FROM credit_payments p
            WHERE p.creditDocumentId = d.creditDocumentId
        ), 0)), 0)
        FROM credit_documents d
        WHERE d.status = 'OPEN'
          AND d.customerId IS NULL
          AND lower(trim(d.customerFirstName)) = lower(trim(:firstName))
          AND lower(trim(d.customerLastName)) = lower(trim(:lastName))
    """)
    abstract suspend fun getOutstandingCreditByLegacyName(firstName: String, lastName: String): Long

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
        validateAndAdvanceClock(paidAt)
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

        if (Math.addExact(alreadyPaid, amount) == document.creditAmount) {
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
