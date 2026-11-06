package th.co.lotto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawSourceComparisonTest {
    private fun result(source: String, date: String, first: String, bottom: String) = PublicDrawResult(
        source, date, first, first.takeLast(3), bottom, "https://example.test/results", 1L
    )

    @Test fun exactFirstPrizeLastTwoAndDrawDateMatch() {
        val comparison = DrawSourceComparison("2026-11-01",
            result("LOTTERYCHECKS", "2026-11-01", "004615", "03"),
            result("HUAYREVIEW", "2026-11-01", "004615", "03"))
        assertTrue(comparison.bothAvailable)
        assertTrue(comparison.datesMatch)
        assertTrue(comparison.numbersMatch)
        assertTrue(comparison.matches)
        assertFalse(comparison.mismatch)
    }

    @Test fun oneSourceReportingAnotherDateIsNotComparable() {
        val comparison = DrawSourceComparison("2026-11-01",
            result("LOTTERYCHECKS", "2026-11-01", "004615", "03"),
            result("HUAYREVIEW", "2026-11-16", "004615", "03"))
        assertFalse(comparison.datesMatch)
        assertFalse(comparison.matches)
        assertFalse(comparison.mismatch)
    }

    @Test fun bothSourcesReportingSameButWrongDateIsNotComparable() {
        val comparison = DrawSourceComparison("2026-11-01",
            result("LOTTERYCHECKS", "2026-11-16", "004615", "03"),
            result("HUAYREVIEW", "2026-11-16", "004615", "03"))
        assertFalse(comparison.datesMatch)
        assertFalse(comparison.matches)
        assertFalse(comparison.mismatch)
    }

    @Test fun leadingZeroDifferencesAreNotNormalizedAway() {
        val comparison = DrawSourceComparison("2026-11-01",
            result("LOTTERYCHECKS", "2026-11-01", "004615", "03"),
            result("HUAYREVIEW", "2026-11-01", "104615", "03"))
        assertTrue(comparison.datesMatch)
        assertTrue(comparison.mismatch)
    }

    @Test fun missingSourceIsNotAValidMatch() {
        val comparison = DrawSourceComparison("2026-11-01",
            result("LOTTERYCHECKS", "2026-11-01", "004615", "03"), null)
        assertFalse(comparison.bothAvailable)
        assertFalse(comparison.datesMatch)
        assertFalse(comparison.matches)
        assertFalse(comparison.mismatch)
    }
}
