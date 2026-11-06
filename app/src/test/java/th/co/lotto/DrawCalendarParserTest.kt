package th.co.lotto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DrawCalendarParserTest {
    private val today = LocalDate.of(2026, 11, 6)

    @Test
    fun parsesLotteryChecksUpcomingFullThaiDates() {
        val html = """
            <h2>งวดถัดไป</h2>
            <p>วันจันทร์ที่ 16 พฤศจิกายน 2569</p>
            <p>วันอังคารที่ 1 ธันวาคม 2569</p>
            <p>วันพฤหัสบดีที่ 17 ธันวาคม 2569</p>
            <h2>หวยลาวพัฒนา</h2>
        """.trimIndent()
        assertEquals(listOf(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 17)),
            DrawCalendarService.parseLotteryChecks(html, today))
    }

    @Test
    fun parsesHuayReviewTableWithMonthYearRows() {
        val html = """
            <table>
              <tr><td>พ.ย. 2569</td><td>อาทิตย์ 1 พ.ย. · จันทร์ 16 พ.ย.</td></tr>
              <tr><td>ธ.ค. 2569</td><td>อังคาร 1 ธ.ค. · พฤหัสบดี 17 ธ.ค.</td></tr>
            </table>
        """.trimIndent()
        assertEquals(listOf(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 17)),
            DrawCalendarService.parseHuayReview(html, 2569, today))
    }

    @Test
    fun rejectsPastDatesAndInvalidCalendarDates() {
        val html = """
            <table><tr><td>ต.ค. 2569</td><td>พฤหัสบดี 1 ต.ค.</td></tr>
            <tr><td>พ.ย. 2569</td><td>อาทิตย์ 1 พ.ย. · จันทร์ 31 พ.ย.</td></tr></table>
        """.trimIndent()
        assertTrue(DrawCalendarService.parseHuayReview(html, 2569, today).isEmpty())
    }
}
