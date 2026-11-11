#!/usr/bin/env python3
"""Daily, robots.txt-respecting check of the official GLO draw-calendar FAQ.

This checker deliberately does not infer a published calendar from the recurring
1st/16th rule. It reports an explicit date range only when a dated draw schedule
is actually present in the official page content.
"""
from __future__ import annotations

import hashlib
import json
import re
import sys
import urllib.error
import urllib.robotparser
import urllib.request
from datetime import datetime, timezone
from html.parser import HTMLParser
from pathlib import Path
from zoneinfo import ZoneInfo

SOURCE_URL = "https://www.glo.or.th/service/faq/award"
ROBOTS_URL = "https://www.glo.or.th/robots.txt"
OUTPUT = Path("data/bot/glo-calendar-status.json")
USER_AGENT = "LottoCalendarMonitor/1.0 (+https://github.com/preechaphukthaisong65-arch/lotto)"
TIMEOUT_SECONDS = 20
THAI_MONTHS = {
    "มกราคม": 1, "กุมภาพันธ์": 2, "มีนาคม": 3, "เมษายน": 4,
    "พฤษภาคม": 5, "มิถุนายน": 6, "กรกฎาคม": 7, "สิงหาคม": 8,
    "กันยายน": 9, "ตุลาคม": 10, "พฤศจิกายน": 11, "ธันวาคม": 12,
}
DATE_RE = re.compile(
    r"(?:วันที่|งวดวันที่|งวดประจำวันที่)\s*(\d{1,2})\s*"
    r"(มกราคม|กุมภาพันธ์|มีนาคม|เมษายน|พฤษภาคม|มิถุนายน|กรกฎาคม|สิงหาคม|กันยายน|ตุลาคม|พฤศจิกายน|ธันวาคม)"
    r"(?:\s*(?:พ\.ศ\.?\s*)?(\d{4}))?"
)

class TextExtractor(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.parts: list[str] = []

    def handle_data(self, data: str) -> None:
        cleaned = " ".join(data.split())
        if cleaned:
            self.parts.append(cleaned)


def fetch(url: str) -> tuple[bytes, str]:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": USER_AGENT, "Accept": "text/html,application/xhtml+xml"},
    )
    with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
        body = response.read(2_000_001)
        if len(body) > 2_000_000:
            raise RuntimeError("Source page exceeded the 2 MB safety limit")
        content_type = response.headers.get("Content-Type", "")
        if "text/html" not in content_type.lower() and "text/plain" not in content_type.lower():
            raise RuntimeError(f"Unexpected content type: {content_type}")
        charset = response.headers.get_content_charset() or "utf-8"
        return body, charset


def robots_allows() -> tuple[bool, str]:
    parser = urllib.robotparser.RobotFileParser()
    parser.set_url(ROBOTS_URL)
    request = urllib.request.Request(ROBOTS_URL, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            content = response.read(256_000).decode("utf-8", errors="replace")
        parser.parse(content.splitlines())
        allowed = parser.can_fetch(USER_AGENT, SOURCE_URL)
        return allowed, "robots.txt permits access" if allowed else "robots.txt disallows access"
    except Exception as exc:
        # Fail closed: do not scrape if the site's access policy cannot be checked.
        return False, f"could not verify robots.txt: {type(exc).__name__}: {exc}"


def extract_text(html: str) -> str:
    parser = TextExtractor()
    parser.feed(html)
    return " ".join(parser.parts)


def extract_explicit_dates(text: str) -> list[dict[str, object]]:
    found: dict[str, dict[str, object]] = {}
    for match in DATE_RE.finditer(text):
        # Require nearby schedule context to avoid unrelated dates in page footers.
        context = text[max(0, match.start() - 100): min(len(text), match.end() + 100)]
        if not re.search(r"ออกรางวัล|งวด", context):
            continue
        day = int(match.group(1))
        month = THAI_MONTHS[match.group(2)]
        raw_year = match.group(3)
        if raw_year is None:
            continue
        year = int(raw_year)
        if year < 2400:
            year += 543
        try:
            iso_date = datetime(year - 543, month, day).date().isoformat()
        except ValueError:
            continue
        found[iso_date] = {
            "date_be": f"{day:02d}/{month:02d}/{year}",
            "date_iso": iso_date,
            "context": context,
        }
    return [found[key] for key in sorted(found)]


def main() -> int:
    checked_at = datetime.now(timezone.utc)
    checked_th = checked_at.astimezone(ZoneInfo("Asia/Bangkok"))
    report: dict[str, object] = {
        "schema_version": 1,
        "checker": "LottoCalendarMonitor/1.0",
        "checked_at_utc": checked_at.isoformat(),
        "checked_at_thailand": checked_th.isoformat(),
        "source_url": SOURCE_URL,
        "source_name": "สำนักงานสลากกินแบ่งรัฐบาล (GLO) - FAQ ภารกิจการออกรางวัล",
        "access_status": "UNKNOWN",
        "verification_status": "NOT_VERIFIED",
        "published_calendar_detected": False,
        "latest_explicit_draw_date": None,
        "explicit_draw_dates": [],
        "official_rule_text": None,
        "source_sha256": None,
        "message_th": "",
    }

    allowed, policy_message = robots_allows()
    report["robots_policy"] = policy_message
    if not allowed:
        report["access_status"] = "SKIPPED_ROBOTS_OR_POLICY_UNAVAILABLE"
        report["message_th"] = "ข้ามการตรวจ เพราะ robots.txt ไม่อนุญาตหรือไม่สามารถตรวจสอบนโยบายการเข้าถึงได้"
        return write_report(report, 0)

    try:
        body, charset = fetch(SOURCE_URL)
        html = body.decode(charset, errors="replace")
        text = extract_text(html)
        report["access_status"] = "SUCCESS"
        report["source_sha256"] = hashlib.sha256(body).hexdigest()

        rule_match = re.search(
            r"สำนักงานฯ\s*ออกรางวัลเดือนละ 2 ครั้ง.*?(?=เริ่มถ่ายทอด|เริ่มออกรางวัลเวลา|$)",
            text,
        )
        if rule_match:
            report["official_rule_text"] = rule_match.group(0).strip()

        dates = extract_explicit_dates(text)
        report["explicit_draw_dates"] = dates
        if dates:
            report["published_calendar_detected"] = True
            report["latest_explicit_draw_date"] = dates[-1]
            report["verification_status"] = "EXPLICIT_DATED_DRAWS_FOUND"
            report["message_th"] = (
                "พบวันที่ออกรางวัลแบบระบุวันเดือนปีบนหน้า GLO ที่ตรวจสอบ "
                "ระบบแสดงวันที่ล่าสุดที่พบ แต่ยังต้องตรวจประกาศต้นฉบับก่อนนำไปใช้จริง"
            )
        elif report["official_rule_text"]:
            report["verification_status"] = "OFFICIAL_RULE_ONLY_NO_DATED_CALENDAR"
            report["message_th"] = (
                "หน้า FAQ ทางการระบุหลักเกณฑ์วันออกรางวัล แต่ไม่พบตารางปฏิทินที่ระบุวันเดือนปีล่วงหน้า "
                "จึงยังสรุปไม่ได้ว่ากองสลากประกาศปฏิทินไปถึงปีใด"
            )
        else:
            report["verification_status"] = "PAGE_CHANGED_REVIEW_REQUIRED"
            report["message_th"] = "หน้าเว็บเปลี่ยนรูปแบบหรือไม่พบข้อความหลักเกณฑ์ ต้องตรวจสอบด้วยคน"
    except Exception as exc:
        report["access_status"] = "FAILED"
        report["verification_status"] = "SOURCE_UNAVAILABLE"
        report["message_th"] = f"ตรวจหน้า GLO ไม่สำเร็จ: {type(exc).__name__}: {exc}"

    return write_report(report, 0 if report["access_status"] == "SUCCESS" else 1)


def write_report(report: dict[str, object], exit_code: int) -> int:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
