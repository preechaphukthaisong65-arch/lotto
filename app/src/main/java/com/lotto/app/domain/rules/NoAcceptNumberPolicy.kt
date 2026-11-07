package com.lotto.app.domain.rules

import com.lotto.app.data.repository.SettingsRepository

/** Per-prize-type numbers that must never be accepted for sale. */
object NoAcceptNumberPolicy {
    private const val KEY_PREFIX = "no_accept_"

    val settingKeys = mapOf(
        PrizeType.THREE_DIGIT_STRAIGHT.name to KEY_PREFIX + PrizeType.THREE_DIGIT_STRAIGHT.name,
        PrizeType.THREE_DIGIT_TOD.name to KEY_PREFIX + PrizeType.THREE_DIGIT_TOD.name,
        PrizeType.TWO_DIGIT_TOP.name to KEY_PREFIX + PrizeType.TWO_DIGIT_TOP.name,
        PrizeType.TWO_DIGIT_BOTTOM.name to KEY_PREFIX + PrizeType.TWO_DIGIT_BOTTOM.name
    )

    val labels = mapOf(
        PrizeType.THREE_DIGIT_STRAIGHT.name to "3 ตัวตรง",
        PrizeType.THREE_DIGIT_TOD.name to "3 ตัวโต๊ด",
        PrizeType.TWO_DIGIT_TOP.name to "2 ตัวบน",
        PrizeType.TWO_DIGIT_BOTTOM.name to "2 ตัวล่าง"
    )

    fun typeForKey(key: String): PrizeType =
        PrizeType.values().first { settingKeys.getValue(it.name) == key }

    fun parse(value: String, type: PrizeType): Set<String> {
        val digits = if (type == PrizeType.THREE_DIGIT_STRAIGHT || type == PrizeType.THREE_DIGIT_TOD) 3 else 2
        return value.split(",")
            .map { it.trim() }
            .filter { it.length == digits && it.all(Char::isDigit) }
            .toSet()
    }

    suspend fun load(repository: SettingsRepository): Map<String, Set<String>> =
        settingKeys.mapValues { (prizeType, key) ->
            parse(repository.get(key).orEmpty(), PrizeType.valueOf(prizeType))
        }
}
