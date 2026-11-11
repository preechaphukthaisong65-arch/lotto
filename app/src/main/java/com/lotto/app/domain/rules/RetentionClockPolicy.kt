package com.lotto.app.domain.rules

/**
 * Conservative local sanity check before irreversible retention deletion.
 *
 * This does not establish trusted wall-clock time. It only rejects obvious
 * clock jumps and reboot gaps by requiring wall-clock progress not to exceed
 * the device uptime upper bound since the last persisted high-water mark.
 * A trusted external time source is still required for adversarial assurance.
 */
object RetentionClockPolicy {
    private const val CLOCK_TOLERANCE_MILLIS = 2L * 60L * 1000L

    fun isSafeForAutomaticDeletion(
        nowEpochMillis: Long,
        highWaterEpochMillis: Long,
        elapsedRealtimeMillis: Long
    ): Boolean {
        if (nowEpochMillis <= 0L ||
            highWaterEpochMillis <= 0L ||
            elapsedRealtimeMillis < 0L ||
            nowEpochMillis < highWaterEpochMillis
        ) {
            return false
        }

        val wallClockDelta = nowEpochMillis - highWaterEpochMillis
        val maximumPlausibleDelta = elapsedRealtimeMillis + CLOCK_TOLERANCE_MILLIS
        return wallClockDelta <= maximumPlausibleDelta
    }
}
