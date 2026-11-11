package com.lotto.app.domain.rules

import com.lotto.app.data.repository.SettingsRepository

/**
 * Per-prize-category purchase caps shared by all numbers in that category. Limits reset per draw.
 * Limits are global per prize type and are evaluated independently for each draw.
 */
object FullNumberLimitPolicy {
    const val KEY_THREE_DIGIT_STRAIGHT = "full_limit_THREE_DIGIT_STRAIGHT"
    const val KEY_THREE_DIGIT_TOD = "full_limit_THREE_DIGIT_TOD"
    const val KEY_TWO_DIGIT_TOP = "full_limit_TWO_DIGIT_TOP"
    const val KEY_TWO_DIGIT_BOTTOM = "full_limit_TWO_DIGIT_BOTTOM"

    val settingKeys = mapOf(
        PrizeType.THREE_DIGIT_STRAIGHT.name to KEY_THREE_DIGIT_STRAIGHT,
        PrizeType.THREE_DIGIT_TOD.name to KEY_THREE_DIGIT_TOD,
        PrizeType.TWO_DIGIT_TOP.name to KEY_TWO_DIGIT_TOP,
        PrizeType.TWO_DIGIT_BOTTOM.name to KEY_TWO_DIGIT_BOTTOM
    )

    val labels = mapOf(
        PrizeType.THREE_DIGIT_STRAIGHT.name to "3 ตัวตรง",
        PrizeType.THREE_DIGIT_TOD.name to "3 ตัวโต๊ด",
        PrizeType.TWO_DIGIT_TOP.name to "2 ตัวบน",
        PrizeType.TWO_DIGIT_BOTTOM.name to "2 ตัวล่าง"
    )

    suspend fun load(repository: SettingsRepository): Map<String, Long> =
        settingKeys.mapValues { (_, key) ->
            repository.get(key)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        }
}
