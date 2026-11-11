package com.lotto.app.domain.rules

/**
 * Uses elapsedRealtime values only. Wall-clock changes must never extend an unlocked session.
 */
object AppLockPolicy {
    const val INACTIVITY_TIMEOUT_MS = 3 * 60 * 1000L

    fun shouldLock(lastInteractionElapsedRealtime: Long, nowElapsedRealtime: Long): Boolean {
        if (lastInteractionElapsedRealtime < 0L || nowElapsedRealtime < lastInteractionElapsedRealtime) {
            return false
        }
        return nowElapsedRealtime - lastInteractionElapsedRealtime >= INACTIVITY_TIMEOUT_MS
    }
}
