package com.lotto.app.domain.rules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {
    @Test
    fun doesNotLockBeforeTimeout() {
        assertFalse(AppLockPolicy.shouldLock(10_000L, 10_000L + AppLockPolicy.INACTIVITY_TIMEOUT_MS - 1L))
    }

    @Test
    fun locksAtExactTimeout() {
        assertTrue(AppLockPolicy.shouldLock(10_000L, 10_000L + AppLockPolicy.INACTIVITY_TIMEOUT_MS))
    }

    @Test
    fun ignoresInvalidOrBackwardsMonotonicTimestamps() {
        assertFalse(AppLockPolicy.shouldLock(-1L, 500_000L))
        assertFalse(AppLockPolicy.shouldLock(500_000L, 499_999L))
    }
}
