package com.lotto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lotto.app.data.local.entity.DrawDateChangeEntity
import com.lotto.app.data.local.entity.DrawDateEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DrawCalendarDao {
    @Query("SELECT * FROM draw_dates WHERE drawDate BETWEEN :startDate AND :endDate ORDER BY drawDate")
    abstract fun observeDrawDates(startDate: String, endDate: String): Flow<List<DrawDateEntity>>

    @Query("SELECT * FROM draw_dates ORDER BY drawDate DESC")
    abstract fun observeAllDrawDates(): Flow<List<DrawDateEntity>>

    @Query("SELECT * FROM draw_dates WHERE drawDate = :date LIMIT 1")
    abstract suspend fun getByDate(date: String): DrawDateEntity?

    @Query("SELECT COUNT(*) FROM sales WHERE drawDate = :date")
    protected abstract suspend fun countSalesForDraw(date: String): Int

    @Query("SELECT COUNT(*) FROM draw_results WHERE drawDate = :date")
    protected abstract suspend fun countResultsForDraw(date: String): Int

    @Query("SELECT COUNT(*) FROM winning_sale_items WHERE drawDate = :date")
    protected abstract suspend fun countWinningItemsForDraw(date: String): Int

    @Query("SELECT * FROM draw_date_changes ORDER BY changedAt DESC")
    abstract fun observeAllChanges(): Flow<List<DrawDateChangeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertDrawDate(entity: DrawDateEntity)

    @Insert
    abstract suspend fun insertChange(entity: DrawDateChangeEntity)

    @Query("DELETE FROM draw_dates WHERE drawDate = :date")
    abstract suspend fun deleteDrawDate(date: String)

    @Transaction
    open suspend fun moveDrawDate(oldDate: String, newDate: String, changedAt: Long) {
        require(oldDate != newDate) { "วันออกรางวัลใหม่ต้องไม่ซ้ำวันเดิม" }
        require(getByDate(oldDate) != null) { "ไม่พบงวดที่ต้องการแก้ไข" }
        require(getByDate(newDate) == null) { "มีงวดออกรางวัลในวันที่เลือกแล้ว" }

        val current = getByDate(oldDate) ?: error("ไม่พบงวดที่ต้องการแก้ไข")
        require(countSalesForDraw(oldDate) == 0 && countResultsForDraw(oldDate) == 0 && countWinningItemsForDraw(oldDate) == 0) {
            "งวดนี้มีบิลขายหรือผลรางวัลแล้ว จึงไม่อนุญาตให้เปลี่ยนวัน เพื่อป้องกันข้อมูลเดิมคลาดเคลื่อน"
        }
        insertChange(
            DrawDateChangeEntity(
                drawDate = newDate,
                previousDate = oldDate,
                newDate = newDate,
                changedAt = changedAt,
                source = current.source
            )
        )
        deleteDrawDate(oldDate)
        insertDrawDate(
            current.copy(
                drawDate = newDate,
                updatedAt = changedAt,
                source = "USER_EDITED"
            )
        )
    }
}
