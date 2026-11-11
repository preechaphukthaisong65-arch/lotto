package com.lotto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lotto.app.data.local.entity.DrawDateEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DrawCalendarDao {
    @Query("SELECT * FROM draw_dates WHERE source = 'BOT_VERIFIED' AND drawDate BETWEEN :startDate AND :endDate ORDER BY drawDate")
    abstract fun observeDrawDates(startDate: String, endDate: String): Flow<List<DrawDateEntity>>

    @Query("SELECT * FROM draw_dates WHERE source = 'BOT_VERIFIED' ORDER BY drawDate")
    abstract fun observeBotVerifiedDrawDates(): Flow<List<DrawDateEntity>>

    /** Read-only access for historical reports. These rows must never drive current sale windows. */
    @Query("SELECT * FROM draw_dates ORDER BY drawDate DESC")
    abstract fun observeAllDrawDates(): Flow<List<DrawDateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertBotVerifiedDrawDate(entity: DrawDateEntity)
}
