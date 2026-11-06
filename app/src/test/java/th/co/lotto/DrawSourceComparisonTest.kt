package th.co.lotto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawSourceComparisonTest {
    private fun result(source: String, first: String, bottom: String) = PublicDrawResult(
        source, "2026-11-01", first, first.takeLast(3), bottom, "https://example.test/results", 1L
    )

    @Test fun exactFirstPrizeAndLastTwoMatch() {
        val comparison = DrawSourceComparison(
            "2026-11-01",
            result("LOTTERYCHECKS", "004615", "03"),
            result("HUAYREVIEW", "004615", "03")
        )
        assertTrue(comparison.bothAvailable)
        assertTrue(comparison.matches)
        assertFalse(comparison.mismatch)
    }

    @Test fun leadingZeroDifferencesAreNotNormalizedAway() {
        val comparison = DrawSourceComparison(
            "2026-11-01",
            result("LOTTERYCHECKS", "004615", "03"),
            result("HUAYREVIEW", "104615", "03")
        )
        assertTrue(comparison.mismatch)
    }

    @Test fun missingSourceIsNotAValidMatch() {
        val comparison = DrawSourceComparison(
            "2026-11-01",
            result("LOTTERYCHECKS", "004615", "03"),
            null
        )
        assertFalse(comparison.bothAvailable)
        assertFalse(comparison.matches)
        assertFalse(comparison.mismatch)
    }
}
