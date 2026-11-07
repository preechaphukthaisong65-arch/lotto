package com.lotto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lotto.app.data.local.entity.DrawResultEntity
import com.lotto.app.data.local.entity.SaleItemEntity
import com.lotto.app.data.local.entity.WinningSaleItemEntity
import com.lotto.app.data.local.model.WinningSaleSummary
import com.lotto.app.domain.rules.LottoRules
import com.lotto.app.domain.rules.PrizeType
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DrawResultDao {
    @Query("SELECT * FROM draw_results ORDER BY drawDate DESC")
    abstract fun observeAllResults(): Flow<List<DrawResultEntity>>

    @Query("SELECT * FROM draw_results WHERE drawDate = :drawDate LIMIT 1")
    abstract suspend fun getResult(drawDate: String): DrawResultEntity?

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

    @Transaction
    open suspend fun saveResultAndFindWinners(result: DrawResultEntity): Int {
        require(LottoRules.isValidThreeDigitNumber(result.threeDigit)) { "กรุณากรอกผล 3 ตัวให้ครบ 3 หลัก" }
        require(LottoRules.isValidTwoDigitNumber(result.twoDigitTop)) { "กรุณากรอกผล 2 ตัวบนให้ครบ 2 หลัก" }
        require(LottoRules.isValidTwoDigitNumber(result.twoDigitBottom)) { "กรุณากรอกผล 2 ตัวล่างให้ครบ 2 หลัก" }
        require(getResult(result.drawDate) == null) { "งวดนี้บันทึกผลรางวัลแล้ว" }

        val winningItems = getItemsForDraw(result.drawDate).mapNotNull { item ->
            val type = runCatching { PrizeType.valueOf(item.prizeType) }.getOrNull()
                ?: return@mapNotNull null
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
        insertResult(result)
        if (winningItems.isNotEmpty()) insertWinners(winningItems)
        return winningItems.size
    }
}
