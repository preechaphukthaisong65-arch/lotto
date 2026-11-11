#!/usr/bin/env bash
# Run instrumentation tests and capture diagnostics before the emulator action tears down the device.
set +e
mkdir -p ci-out
: > ci-out/gradle-full.log

test_status=0
bash .github/scripts/wait-for-android.sh
wait_status=$?
if [ "$wait_status" -ne 0 ]; then
  echo "Android readiness check failed (exit $wait_status)." | tee ci-out/gradle-full.log
  test_status=$wait_status
else
  gradle --no-daemon :app:connectedDebugAndroidTest 2>&1 | tee ci-out/gradle-full.log
  test_status=${PIPESTATUS[0]}
fi

DEVICE="${ANDROID_SERIAL:-emulator-5554}"
adb -s "$DEVICE" logcat -d > ci-out/logcat-full.txt 2>&1
logcat_status=$?
if [ "$logcat_status" -ne 0 ]; then
  echo "Unable to collect full logcat (adb exit $logcat_status)." > ci-out/logcat-full.txt
fi

grep -E "AndroidRuntime|FATAL|com\\.lotto|SQLCipher|TestRunner|INSTRUMENTATION|Exception" ci-out/logcat-full.txt \
  | tail -n 300 \
  | python3 .github/scripts/sanitize-ci-log.py > ci-out/logcat-filtered.txt

tail -n 300 ci-out/gradle-full.log | python3 .github/scripts/sanitize-ci-log.py > ci-out/gradle-tail.log

TEST_STATUS="$test_status" python3 - <<'PY'
import glob
import os
import xml.etree.ElementTree as ET

files = glob.glob("app/build/outputs/androidTest-results/**/*.xml", recursive=True)
files += glob.glob("app/build/reports/androidTests/**/*.xml", recursive=True)
files += glob.glob("app/build/test-results/**/*.xml", recursive=True)
files = sorted(set(files))
totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
for path in files:
    try:
        root = ET.parse(path).getroot()
        suites = [root] if root.tag == "testsuite" else list(root.iter("testsuite"))
        for suite in suites:
            for key in totals:
                totals[key] += int(suite.attrib.get(key, 0) or 0)
    except (ET.ParseError, OSError, ValueError):
        continue

status = os.environ.get("TEST_STATUS", "1")
label = "PASSED" if status == "0" else "FAILED"
with open("ci-out/summary.md", "w", encoding="utf-8") as out:
    out.write("# Android instrumentation test result\n\n")
    out.write(f"- **Result:** {label} (exit {status})\n")
    out.write(f"- **Run:** {os.environ.get('GITHUB_SERVER_URL', 'https://github.com')}/{os.environ.get('GITHUB_REPOSITORY', '')}/actions/runs/{os.environ.get('GITHUB_RUN_ID', '')}\n")
    out.write(f"- **Commit:** {os.environ.get('GITHUB_SHA', 'unknown')}\n")
    out.write(f"- **JUnit XML files found:** {len(files)}\n")
    out.write(f"- **Tests:** {totals['tests']} | **Failures:** {totals['failures']} | **Errors:** {totals['errors']} | **Skipped:** {totals['skipped']}\n")
    if not files:
        out.write("\nNo JUnit XML report was found; inspect the Actions artifact and job log.\n")
PY

cat > ci-out/RUN.txt <<EOF
run_id=${GITHUB_RUN_ID:-unknown}
run_attempt=${GITHUB_RUN_ATTEMPT:-unknown}
repository=${GITHUB_REPOSITORY:-unknown}
ref=${GITHUB_REF:-unknown}
sha=${GITHUB_SHA:-unknown}
test_exit_code=$test_status
EOF

exit "$test_status"
