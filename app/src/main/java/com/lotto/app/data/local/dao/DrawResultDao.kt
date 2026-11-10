package com.lotto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lotto.app.data.local.entity.DrawResultEntity
import com.lotto.app.data.local.entity.SaleItemEntity
import com.lotto.app.data.local.entity.WinningSaleItemEntity
import com.lotto.app.data.local.entity.DrawProfitSummaryEntity
import com.lotto.app.data.local.entity.QUALITY_COMPLETE
import com.lotto.app.data.local.entity.QUALITY_NEEDS_REVIEW
import com.lotto.app.data.local.model.WinningSaleSummary
import com.lotto.app.data.local.model.WinningBillSummary
import com.lotto.app.domain.rules.LottoRules
import com.lotto.app.domain.rules.PrizeType
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Dao
abstract class DrawResultDao {
    @Query("SELECT * FROM draw_profit_summaries ORDER BY drawDate DESC")
    abstract fun observeProfitSummaries(): Flow<List<DrawProfitSummaryEntity>>

    @Query("DELETE FROM draw_profit_summaries WHERE drawDate < :cutoffDate")
    abstract suspend fun deleteExpiredProfitSummaries(cutoffDate: String): Int

    @Query("SELECT COALESCE(SUM(totalAmount), 0) FROM sales WHERE drawDate = :drawDate AND status = 'CONFIRMED'")
    protected abstract suspend fun getSalesTotalForDraw(drawDate: String): Long

    @Query("""
        SELECT COUNT(*) FROM sales s
        WHERE s.drawDate IS NULL
          AND s.status = 'CONFIRMED'
          AND EXISTS (
              SELECT 1 FROM draw_dates d
              WHERE d.drawDate = :drawDate
                AND EXISTS (
                    SELECT 1 FROM draw_dates previous
                    WHERE previous.drawDate = (
                        SELECT MAX(p.drawDate) FROM draw_dates p WHERE p.drawDate < d.drawDate
                    )
                      AND s.createdAt >= CAST(strftime('%s', date(previous.drawDate, '+1 day') || ' 08:00:00', '-7 hours') AS INTEGER) * 1000
                      AND s.createdAt < CAST(strftime('%s', d.drawDate || ' 14:30:00', '-7 hours') AS INTEGER) * 1000
                )
          )
    """)
    protected abstract suspend fun countUnassignedSalesInDrawWindow(drawDate: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun saveProfitSummary(summary: DrawProfitSummaryEntity)

    @Query("SELECT * FROM draw_results ORDER BY drawDate DESC")
    abstract fun observeAllResults(): Flow<List<DrawResultEntity>>

    @Query("SELECT * FROM draw_result_corrections WHERE drawDate = :drawDate ORDER BY correctedAt DESC")
    abstract fun observeCorrections(drawDate: String): Flow<List<com.lotto.app.data.local.entity.DrawResultCorrectionEntity>>

    @Query("SELECT * FROM draw_results WHERE drawDate = :drawDate LIMIT 1")
    abstract suspend fun getResult(drawDate: String): DrawResultEntity?

    @Query("SELECT COUNT(*) FROM draw_dates WHERE drawDate = :drawDate")
    protected abstract suspend fun countScheduledDrawDate(drawDate: String): Int

    @Query("SELECT value FROM app_settings WHERE key = :key LIMIT 1")
    protected abstract suspend fun getSettingValue(key: String): String?

    @Query("""
        INSERT INTO app_settings (key, value, updatedAt)
        VALUES (:key, :value, :updatedAt)
        ON CONFLICT(key) DO UPDATE SET value = excluded.value, updatedAt = excluded.updatedAt
    """)
    protected abstract suspend fun upsertSettingValue(key: String, value: String, updatedAt: Long)

    @Query("""
        SELECT wi.drawDate AS drawDate, wi.saleId AS saleId,
               s.customerFirstName AS customerFirstName,
               s.customerLastName AS customerLastName,
               wi.number AS number, wi.prizeType AS prizeType,
               wi.stakeAmount AS stakeAmount, wi.prizeAmount AS prizeAmount
        FROM winning_sale_items wi
        INNER JOIN sales s ON s.saleId = wi.saleId
        WHERE wi.drawDate = :drawDate
        ORDER BY wi.prizeAmount DESC, wi.saleId ASC
    """)
    abstract fun observeWinners(drawDate: String): Flow<List<WinningSaleSummary>>

    @Query("""
        SELECT sale_items.* FROM sale_items
        INNER JOIN sales ON sales.saleId = sale_items.saleId
        WHERE sales.drawDate = :drawDate AND sales.status = 'CONFIRMED'
        ORDER BY sale_items.itemId
    """)
    protected abstract suspend fun getItemsForDraw(drawDate: String): List<SaleItemEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertResult(result: DrawResultEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertWinners(items: List<WinningSaleItemEntity>)

    @Insert
    protected abstract suspend fun insertCorrection(correction: com.lotto.app.data.local.entity.DrawResultCorrectionEntity)

    @Query("UPDATE draw_results SET threeDigit = :threeDigit, twoDigitTop = :twoDigitTop, twoDigitBottom = :twoDigitBottom, recordedAt = :recordedAt WHERE drawDate = :drawDate")
    protected abstract suspend fun updateResult(drawDate: String, threeDigit: String, twoDigitTop: String, twoDigitBottom: String, recordedAt: Long)

    @Query("DELETE FROM winning_sale_items WHERE drawDate = :drawDate")
    protected abstract suspend fun deleteWinnersForDraw(drawDate: String): Int

    @Query("UPDATE draw_results SET payoutLocked = 1 WHERE drawDate = :drawDate")
    protected abstract suspend fun lockResultAfterPayout(drawDate: String)

    private suspend fun calculateWinningItems(result: DrawResultEntity): List<WinningSaleItemEntity> =
        getItemsForDraw(result.drawDate).mapNotNull { item ->
            // Never silently omit an invalid legacy sale item from prize calculation:
            // doing so would understate the operator's prize liability.
            val type = runCatching { PrizeType.valueOf(item.prizeType) }.getOrElse {
                throw IllegalStateException(
                    "พบรายการขายเลขที่ ${item.itemId} ที่มีประเภทหวยไม่ถูกต้อง (${item.prizeType}) กรุณาตรวจสอบข้อมูลก่อนบันทึกผล"
                )
            }
            require(item.amount > 0L && item.payoutMultiplier > 0L) {
                "พบรายการขายเลขที่ ${item.itemId} ที่มียอดเงินหรืออัตราจ่ายไม่ถูกต้อง กรุณาตรวจสอบข้อมูลก่อนบันทึกผล"
            }
            val validNumber = when (type) {
                PrizeType.THREE_DIGIT_STRAIGHT, PrizeType.THREE_DIGIT_TOD ->
                    LottoRules.isValidThreeDigitNumber(item.number)
                PrizeType.TWO_DIGIT_TOP, PrizeType.TWO_DIGIT_BOTTOM ->
                    LottoRules.isValidTwoDigitNumber(item.number)
            }
            require(validNumber) {
                "พบเลข ${item.number} ในรายการขายเลขที่ ${item.itemId} ที่ไม่ถูกต้อง กรุณาตรวจสอบข้อมูลก่อนบันทึกผล"
            }
            val matched = when (type) {
                PrizeType.THREE_DIGIT_STRAIGHT -> LottoRules.isThreeDigitStraightMatch(item.number, result.threeDigit)
                PrizeType.THREE_DIGIT_TOD -> LottoRules.isThreeDigitTodMatch(item.number, result.threeDigit)
                PrizeType.TWO_DIGIT_TOP -> LottoRules.isTwoDigitMatch(item.number, result.twoDigitTop)
                PrizeType.TWO_DIGIT_BOTTOM -> LottoRules.isTwoDigitMatch(item.number, result.twoDigitBottom)
            }
            if (!matched) return@mapNotNull null
            val prize = Math.multiplyExact(item.amount, item.payoutMultiplier)
            WinningSaleItemEntity(
                drawDate = result.drawDate,
                saleId = item.saleId,
                saleItemId = item.itemId,
                prizeType = item.prizeType,
                number = item.number,
                stakeAmount = item.amount,
                payoutMultiplier = item.payoutMultiplier,
                prizeAmount = prize
            )
        }

    private suspend fun saveDrawProfitSummary(drawDate: String, recordedAt: Long, winningItems: List<WinningSaleItemEntity>) {
        val salesTotal = getSalesTotalForDraw(drawDate)
        val prizeLiability = winningItems.fold(0L) { total, item -> Math.addExact(total, item.prizeAmount) }
        val dataQuality = if (countUnassignedSalesInDrawWindow(drawDate) == 0) {
            QUALITY_COMPLETE
        } else {
            QUALITY_NEEDS_REVIEW
        }
        saveProfitSummary(
            DrawProfitSummaryEntity(
                drawDate = drawDate,
                salesTotal = salesTotal,
                prizeLiability = prizeLiability,
                netProfit = Math.subtractExact(salesTotal, prizeLiability),
                recordedAt = recordedAt,
                dataQuality = dataQuality
            )
        )
    }

    @Transaction
    open suspend fun saveResultAndFindWinners(result: DrawResultEntity): Int {
        require(countScheduledDrawDate(result.drawDate) == 1) { "ไม่พบวันที่ออกรางวัลนี้ในปฏิทินหวย กรุณาตรวจสอบงวดก่อนบันทึกผล" }

        val resultDate = runCatching { LocalDate.parse(result.drawDate) }
            .getOrElse { throw IllegalArgumentException("วันที่งวดหวยไม่ถูกต้อง") }
        val resultLocalTime = Instant.ofEpochMilli(result.recordedAt)
            .atZone(ZoneId.of("Asia/Bangkok")).toLocalDateTime()
        require(!resultLocalTime.isBefore(resultDate.atTime(LocalTime.of(16, 0)))) {
            "ยังไม่ถึงเวลา 16:00 น. ของวันออกรางวัล จึงยังบันทึกผลรางวัลไม่ได้"
        }

        val lastSeenRaw = getSettingValue("clock_high_water_epoch_ms")
        val lastSeen = lastSeenRaw?.toLongOrNull()
            ?: if (lastSeenRaw == null) 0L else throw IllegalStateException("ข้อมูลตรวจสอบเวลาของระบบเสียหาย")
        require(result.recordedAt >= lastSeen) {
            "ตรวจพบว่านาฬิกาเครื่องเดินย้อนเวลา ไม่สามารถบันทึกผลรางวัลได้"
        }
        upsertSettingValue("clock_high_water_epoch_ms", result.recordedAt.toString(), result.recordedAt)
        require(LottoRules.isValidThreeDigitNumber(result.threeDigit)) { "กรุณากรอกผล 3 ตัวให้ครบ 3 หลัก" }
        require(LottoRules.isValidTwoDigitNumber(result.twoDigitTop)) { "กรุณากรอกผล 2 ตัวบนให้ครบ 2 หลัก" }
        require(LottoRules.isValidTwoDigitNumber(result.twoDigitBottom)) { "กรุณากรอกผล 2 ตัวล่างให้ครบ 2 หลัก" }
        require(getResult(result.drawDate) == null) { "งวดนี้บันทึกผลรางวัลแล้ว" }

        val winningItems = calculateWinningItems(result)
        insertResult(result)
        if (winningItems.isNotEmpty()) insertWinners(winningItems)
        saveDrawProfitSummary(result.drawDate, result.recordedAt, winningItems)
        return winningItems.size
    }

    @Transaction
    open suspend fun correctResultAndRebuildWinners(
        drawDate: String,
        threeDigit: String,
        twoDigitTop: String,
        twoDigitBottom: String,
        reason: String,
        correctedAt: Long
    ): Int {
        require(LottoRules.isValidThreeDigitNumber(threeDigit)) { "กรุณากรอกผล 3 ตัวให้ครบ 3 หลัก" }
        require(LottoRules.isValidTwoDigitNumber(twoDigitTop)) { "กรุณากรอกผล 2 ตัวบนให้ครบ 2 หลัก" }
        require(LottoRules.isValidTwoDigitNumber(twoDigitBottom)) { "กรุณากรอกผล 2 ตัวล่างให้ครบ 2 หลัก" }
        require(reason.isNotBlank()) { "กรุณาระบุเหตุผลที่แก้ไขผลรางวัล" }
        val previous = getResult(drawDate) ?: throw IllegalArgumentException("ไม่พบผลรางวัลของงวดนี้")
        require(!previous.payoutLocked) {
            "งวดนี้มีการจ่ายรางวัลแล้ว ไม่อนุญาตให้แก้ผลเพื่อป้องกันยอดจ่ายเดิมคลาดเคลื่อน"
        }

        insertCorrection(
            com.lotto.app.data.local.entity.DrawResultCorrectionEntity(
                drawDate = drawDate,
                previousThreeDigit = previous.threeDigit,
                previousTwoDigitTop = previous.twoDigitTop,
                previousTwoDigitBottom = previous.twoDigitBottom,
                newThreeDigit = threeDigit,
                newTwoDigitTop = twoDigitTop,
                newTwoDigitBottom = twoDigitBottom,
                reason = reason.trim(),
                correctedAt = correctedAt
            )
        )
        deleteWinnersForDraw(drawDate)
        updateResult(drawDate, threeDigit, twoDigitTop, twoDigitBottom, correctedAt)
        val corrected = previous.copy(
            threeDigit = threeDigit,
            twoDigitTop = twoDigitTop,
            twoDigitBottom = twoDigitBottom,
            recordedAt = correctedAt
        )
        val winningItems = calculateWinningItems(corrected)
        if (winningItems.isNotEmpty()) insertWinners(winningItems)
        saveDrawProfitSummary(drawDate, correctedAt, winningItems)
        return winningItems.size
    }

    @Query("""
        SELECT wi.drawDate AS drawDate, wi.saleId AS saleId,
               s.customerFirstName AS customerFirstName, s.customerLastName AS customerLastName,
               SUM(wi.prizeAmount) AS totalPrize, MAX(wi.payoutStatus) AS payoutStatus,
               MAX(wi.recipientName) AS recipientName, MAX(wi.paidAt) AS paidAt,
               s.createdAt AS saleCreatedAt,
               GROUP_CONCAT(
                   'เลข ' || wi.number || ' · ' ||
                   CASE wi.prizeType
                       WHEN 'THREE_DIGIT_STRAIGHT' THEN '3 ตัวตรง'
                       WHEN 'THREE_DIGIT_TOD' THEN '3 ตัวโต๊ด'
                       WHEN 'TWO_DIGIT_TOP' THEN '2 ตัวบน'
                       WHEN 'TWO_DIGIT_BOTTOM' THEN '2 ตัวล่าง'
                       ELSE wi.prizeType
                   END || ' · แทง ' || wi.stakeAmount || ' บาท · รางวัล ' || wi.prizeAmount || ' บาท',
                   char(10)
               ) AS itemDetails
        FROM winning_sale_items wi INNER JOIN sales s ON s.saleId = wi.saleId
        WHERE wi.payoutStatus = :status
        GROUP BY wi.drawDate, wi.saleId, s.customerFirstName, s.customerLastName, s.createdAt
        ORDER BY COALESCE(MAX(wi.paidAt), s.createdAt) DESC, wi.saleId DESC
    """)
    abstract fun observeWinningBills(status: String): Flow<List<WinningBillSummary>>

    @Query("SELECT COALESCE(SUM(prizeAmount), 0) FROM winning_sale_items WHERE drawDate = :drawDate")
    abstract suspend fun getTotalPrizeLiability(drawDate: String): Long

    @Query("SELECT payoutStatus FROM winning_sale_items WHERE drawDate = :drawDate AND saleId = :saleId LIMIT 1")
    protected abstract suspend fun getWinningBillStatus(drawDate: String, saleId: Long): String?

    @Query("UPDATE winning_sale_items SET payoutStatus = 'PAID', recipientName = :recipientName, paidAt = :paidAt WHERE drawDate = :drawDate AND saleId = :saleId AND payoutStatus = 'UNPAID'")
    protected abstract suspend fun updateWinningBillPaid(drawDate: String, saleId: Long, recipientName: String, paidAt: Long): Int

    @Transaction
    open suspend fun markWinningBillPaid(drawDate: String, saleId: Long, recipientName: String, paidAt: Long) {
        require(recipientName.isNotBlank()) { "กรุณากรอกชื่อผู้รับรางวัล" }
        require(getWinningBillStatus(drawDate, saleId) == "UNPAID") { "บิลนี้จ่ายรางวัลแล้วหรือไม่พบข้อมูล" }
        require(updateWinningBillPaid(drawDate, saleId, recipientName.trim(), paidAt) > 0) { "บันทึกการจ่ายรางวัลไม่สำเร็จ" }
        lockResultAfterPayout(drawDate)
    }

    @Query("DELETE FROM winning_sale_items WHERE payoutStatus = 'PAID' AND paidAt IS NOT NULL AND paidAt <= :cutoffTimestamp")
    abstract suspend fun deleteExpiredPaidWinningBills(cutoffTimestamp: Long): Int

}
