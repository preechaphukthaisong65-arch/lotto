package com.lotto.app.domain.rules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetentionClockPolicyTest {
    @Test
    fun acceptsWallClockProgressWithinDeviceUptime() {
        assertTrue(
            RetentionClockPolicy.isSafeForAutomaticDeletion(
                nowEpochMillis = 1_000_000L + 60L * 60L * 1000L,
                highWaterEpochMillis = 1_000_000L,
                elapsedRealtimeMillis = 2L * 60L * 60L * 1000L
            )
        )
    }

    @Test
    fun rejectsForwardClockJumpBeyondDeviceUptime() {
        assertFalse(
            RetentionClockPolicy.isSafeForAutomaticDeletion(
                nowEpochMillis = 1_000_000L + 24L * 60L * 60L * 1000L,
                highWaterEpochMillis = 1_000_000L,
                elapsedRealtimeMillis = 60L * 60L * 1000L
            )
        )
    }

    @Test
    fun rejectsBackwardClockAndMissingCheckpoint() {
        assertFalse(
            RetentionClockPolicy.isSafeForAutomaticDeletion(
                nowEpochMillis = 900_000L,
                highWaterEpochMillis = 1_000_000L,
                elapsedRealtimeMillis = 60_000L
            )
        )
        assertFalse(
            RetentionClockPolicy.isSafeForAutomaticDeletion(
                nowEpochMillis = 1_000_000L,
                highWaterEpochMillis = 0L,
                elapsedRealtimeMillis = 60_000L
            )
        )
    }
}
