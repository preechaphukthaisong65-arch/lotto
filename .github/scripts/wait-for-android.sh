#!/usr/bin/env bash
set -euo pipefail

DEVICE="${ANDROID_SERIAL:-emulator-5554}"
adb -s "$DEVICE" wait-for-device

echo "Waiting for Android framework services..."
ready=0
for attempt in $(seq 1 120); do
  booted="$(adb -s "$DEVICE" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  package_ready="$(adb -s "$DEVICE" shell service check package 2>/dev/null | grep -c 'found' || true)"
  activity_ready="$(adb -s "$DEVICE" shell service check activity 2>/dev/null | grep -c 'found' || true)"
  if [[ "$booted" == "1" && "$package_ready" -gt 0 && "$activity_ready" -gt 0 ]]; then
    ready=1
    break
  fi
  sleep 2
done

if [[ "$ready" -ne 1 ]]; then
  echo "::error::Android framework did not become ready in time."
  adb devices -l || true
  adb -s "$DEVICE" shell getprop | grep -E 'boot|init.svc' || true
  adb -s "$DEVICE" shell service list || true
  exit 1
fi

adb -s "$DEVICE" shell pm list packages >/dev/null
echo "Android package and activity services are ready."
