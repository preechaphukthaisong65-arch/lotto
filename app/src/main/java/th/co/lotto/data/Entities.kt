package th.co.lotto.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "draw_periods")
data class DrawPeriodEntity(
    @PrimaryKey val periodId: String,
    val title: String,
    val drawDateEpochMillis: Long?,
    val status: String,
    val salesCloseEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val scheduledOpenEpochMillis: Long? = null,
    val openedAtEpochMillis: Long? = null
)

@Entity(tableName = "payout_rate_settings")
data class PayoutRateSettingsEntity(
    @PrimaryKey val settingsId: Int = 1,
    /** Multiplier stored in hundredths: 7000 means 70.00 baht per baht. */
    val twoDigitRateHundredths: Long,
    val threeTopRateHundredths: Long,
    val threeBottomRateHundredths: Long,
    val threeToadRateHundredths: Long,
    val configVersion: Long,
    val updatedAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "7000") val twoTopRateHundredths: Long = twoDigitRateHundredths,
    @ColumnInfo(defaultValue = "7000") val twoBottomRateHundredths: Long = twoDigitRateHundredths
)

@Entity(tableName = "customers", indices = [Index(value = ["phone"])])
data class CustomerEntity(
    @PrimaryKey val customerId: String,
    val displayName: String,
    val phone: String?,
    val identityNote: String?,
    val customerType: String,
    val createdAtEpochMillis: Long
)

@Entity(
    tableName = "sale_documents",
    foreignKeys = [
        ForeignKey(entity = DrawPeriodEntity::class, parentColumns = ["periodId"], childColumns = ["periodId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CustomerEntity::class, parentColumns = ["customerId"], childColumns = ["customerId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["periodId"]), Index(value = ["customerId"]), Index(value = ["createdAtEpochMillis"])]
)
data class SaleDocumentEntity(
    @PrimaryKey val documentId: String,
    val periodId: String,
    val customerId: String?,
    val customerType: String,
    val totalAmountSatang: Long,
    val createdAtEpochMillis: Long,
    val deviceTimeZone: String,
    val correctionOfDocumentId: String? = null
)

@Entity(
    tableName = "sale_cancellations",
    foreignKeys = [ForeignKey(entity = SaleDocumentEntity::class, parentColumns = ["documentId"], childColumns = ["documentId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["documentId"], unique = true)]
)
data class SaleCancellationEntity(
    @PrimaryKey val cancellationId: String,
    val documentId: String,
    val originalAmountSatang: Long,
    val reason: String,
    val cancelledAtEpochMillis: Long
)

@Entity(
    tableName = "sale_lines",
    foreignKeys = [ForeignKey(entity = SaleDocumentEntity::class, parentColumns = ["documentId"], childColumns = ["documentId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["documentId"]), Index(value = ["periodId", "numberText", "betCategory"])]
)
data class SaleLineEntity(
    @PrimaryKey val lineId: String,
    val documentId: String,
    val periodId: String,
    val numberText: String,
    val betCategory: String,
    val amountSatang: Long,
    val halfPayout: Boolean
)

@Entity(
    tableName = "payment_entries",
    foreignKeys = [
        ForeignKey(entity = SaleDocumentEntity::class, parentColumns = ["documentId"], childColumns = ["documentId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CustomerEntity::class, parentColumns = ["customerId"], childColumns = ["customerId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["documentId"]), Index(value = ["customerId"]), Index(value = ["paidAtEpochMillis"])]
)
data class PaymentEntryEntity(
    @PrimaryKey val paymentId: String,
    val documentId: String,
    val customerId: String,
    val amountSatang: Long,
    val paidAtEpochMillis: Long,
    val method: String,
    val note: String?
)

@Entity(tableName = "number_limit_rules", indices = [Index(value = ["periodId", "numberLength", "betCategory", "numberText"], unique = true)])
data class NumberLimitRuleEntity(
    @PrimaryKey val ruleId: String,
    val periodId: String,
    val numberLength: Int,
    val betCategory: String,
    val numberText: String,
    val maximumAmountSatang: Long,
    val enabled: Boolean,
    @ColumnInfo(defaultValue = "0") val halfPayout: Boolean = false
)

@Entity(
    tableName = "number_block_rules",
    foreignKeys = [ForeignKey(entity = DrawPeriodEntity::class, parentColumns = ["periodId"], childColumns = ["periodId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["periodId", "blockCategory", "numberText"], unique = true)]
)
data class NumberBlockRuleEntity(
    @PrimaryKey val ruleId: String,
    val periodId: String,
    /** THREE_TOP or THREE_TOAD only */
    val blockCategory: String,
    val numberText: String,
    val blocked: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Entity(tableName = "draw_results", indices = [Index(value = ["periodId"])])
data class DrawResultEntity(
    @PrimaryKey val resultId: String,
    val periodId: String,
    val resultType: String,
    val winningNumber: String,
    val sourceLabel: String,
    val verifiedAtEpochMillis: Long?,
    val importedAtEpochMillis: Long
)

@Entity(
    tableName = "settlement_snapshots",
    foreignKeys = [ForeignKey(entity = DrawPeriodEntity::class, parentColumns = ["periodId"], childColumns = ["periodId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["periodId"], unique = true)]
)
data class SettlementSnapshotEntity(
    @PrimaryKey val settlementId: String,
    val periodId: String,
    val twoDigitRateHundredths: Long,
    val threeTopRateHundredths: Long,
    val threeBottomRateHundredths: Long,
    val threeToadRateHundredths: Long,
    val payoutConfigVersion: Long,
    val createdAtEpochMillis: Long,
    val twoTopRateHundredths: Long = twoDigitRateHundredths,
    val twoBottomRateHundredths: Long = twoDigitRateHundredths
)

@Entity(tableName = "audit_events", indices = [Index(value = ["entityId"]), Index(value = ["createdAtEpochMillis"])])
data class AuditEventEntity(
    @PrimaryKey val eventId: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val details: String,
    val createdAtEpochMillis: Long
)
