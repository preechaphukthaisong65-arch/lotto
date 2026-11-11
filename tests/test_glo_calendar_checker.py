import unittest

from scripts.check_glo_calendar import extract_explicit_dates, extract_text


class GloCalendarCheckerTests(unittest.TestCase):
    def test_extracts_explicit_buddhist_draw_date(self):
        text = "ประกาศกำหนดวันออกรางวัล งวดวันที่ 1 มกราคม 2570"
        dates = extract_explicit_dates(text)
        self.assertEqual(len(dates), 1)
        self.assertEqual(dates[0]["date_be"], "01/01/2570")
        self.assertEqual(dates[0]["date_iso"], "2027-01-01")

    def test_does_not_invent_year_for_recurring_rule(self):
        text = "ออกรางวัลทุกวันที่ 1 และ 16 ของทุกเดือน"
        self.assertEqual(extract_explicit_dates(text), [])

    def test_ignores_unrelated_dates_without_draw_context(self):
        text = "เผยแพร่เมื่อวันที่ 1 มกราคม 2570"
        self.assertEqual(extract_explicit_dates(text), [])

    def test_extracts_text_from_html(self):
        self.assertEqual(extract_text("<h1>ปฏิทิน</h1><p>งวดวันที่ 1</p>"), "ปฏิทิน งวดวันที่ 1")


if __name__ == "__main__":
    unittest.main()
