#!/usr/bin/env python3
import re
import sys

text = sys.stdin.read()
patterns = [
    (re.compile(r'(?i)(\b(?:password|passwd|token|secret|api[_-]?key|keystore[_-]?(?:password|pass)|authorization|cookie)\b["\']?\s*[:=]\s*["\']?)[^\s,"\']+'), r'\1[REDACTED]'),
    (re.compile(r'(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+'), 'Bearer [REDACTED]'),
    (re.compile(r'(?i)(https?://[^\s?]+\?[^\s#]*)(?:#[^\s]*)?'), r'\1[QUERY_REDACTED]'),
]
for pattern, replacement in patterns:
    text = pattern.sub(replacement, text)
sys.stdout.write(text)
