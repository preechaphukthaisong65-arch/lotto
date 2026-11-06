package th.co.lotto.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "draw_source_checks",
    indices = [Index(value = ["periodId", "sourceId"]), Index(value = ["periodId", "fetchedAtEpochMillis"])]
)
data class DrawSourceCheckEntity(
    @PrimaryKey val checkId: String,
    val periodId: String,
    val sourceId: String,
    val drawDateIso: String,
    val firstPrizeNumber: String,
    val threeTopNumber: String,
    val twoBottomNumber: String,
    val sourceUrl: String,
    val fetchedAtEpochMillis: Long
)
