package th.co.lotto.data

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
    val scheduledOpenEpochMillis: Long? = null
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
    val enabled: Boolean
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

@Entity(tableName = "audit_events", indices = [Index(value = ["entityId"]), Index(value = ["createdAtEpochMillis"])])
data class AuditEventEntity(
    @PrimaryKey val eventId: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val details: String,
    val createdAtEpochMillis: Long
)
