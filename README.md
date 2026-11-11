# Lotto

Android application project.

## Current scope

- Main menu and app screens for lottery sales, reports, history, credit accounts, and settings.
- Kotlin, Jetpack Compose, Material 3, and Room database.
- A scheduled GitHub Actions bot checks the official GLO draw-calendar FAQ daily at 00:00 Thailand time.
- The bot checks GLO's `robots.txt` before requesting the official page and fails closed if access permission cannot be verified.
- The bot writes its latest result to `data/bot/glo-calendar-status.json`.
- The bot does not infer a published calendar from the recurring 1st/16th rule. If the official page contains only general rules and no dated schedule, the report explicitly says that the published calendar's end year cannot be determined from that page.
- The bot does not bypass CAPTCHA, access controls, or website restrictions.

## Bot workflow

- Workflow: `.github/workflows/glo-calendar-monitor.yml`
- Checker: `scripts/check_glo_calendar.py`
- Schedule: daily at 17:00 UTC (00:00 Asia/Bangkok), plus manual run.
- Latest report: `data/bot/glo-calendar-status.json`
