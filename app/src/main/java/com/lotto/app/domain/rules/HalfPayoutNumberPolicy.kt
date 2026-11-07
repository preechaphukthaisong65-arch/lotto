package com.lotto.app.domain.rules
import com.lotto.app.data.repository.SettingsRepository
object HalfPayoutNumberPolicy {
    private const val KEY_PREFIX = "half_payout_"
    val settingKeys = mapOf(
        PrizeType.THREE_DIGIT_STRAIGHT.name to KEY_PREFIX + PrizeType.THREE_DIGIT_STRAIGHT.name,
        PrizeType.THREE_DIGIT_TOD.name to KEY_PREFIX + PrizeType.THREE_DIGIT_TOD.name,
        PrizeType.TWO_DIGIT_TOP.name to KEY_PREFIX + PrizeType.TWO_DIGIT_TOP.name,
        PrizeType.TWO_DIGIT_BOTTOM.name to KEY_PREFIX + PrizeType.TWO_DIGIT_BOTTOM.name
    )
    val labels = NoAcceptNumberPolicy.labels
    fun parse(value: String, type: PrizeType): Set<String> {
        val digits = if (type == PrizeType.THREE_DIGIT_STRAIGHT || type == PrizeType.THREE_DIGIT_TOD) 3 else 2
        return value.split(",").map { it.trim() }
            .filter { it.length == digits && it.all { digit -> digit in '0'..'9' } }.toSet()
    }
    suspend fun load(repository: SettingsRepository): Map<String, Set<String>> =
        settingKeys.mapValues { (prizeType, key) -> parse(repository.get(key).orEmpty(), PrizeType.valueOf(prizeType)) }
}
