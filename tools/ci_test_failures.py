#!/usr/bin/env python3
"""Emit JUnit XML test failures as GitHub Actions error annotations.

Usage: python3 tools/ci_test_failures.py <test-results-dir> [<test-results-dir> ...]

Parses every TEST-*.xml under the given directories and prints one
``::error::`` annotation per failed/errored test case so failures are visible
without downloading log artifacts. GitHub truncates annotation bodies, so the
stack is compressed to a frame trail (class.method:file:line).
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

FRAME_RE = re.compile(r"at ([\w.$]+)\.([\w$<>]+)\(([\w.]+):(\d+)\)")


def compress(text: str) -> str:
    """Compact message + first stack frames into an annotation-sized string."""
    text = " ".join(text.split())
    frames = FRAME_RE.findall(text)
    head = text.split(" at ")[0][:220]
    if not frames:
        return head
    trail = " <- ".join(f"{c.rsplit('.', 1)[-1]}.{m}:{l}" for c, m, _, l in frames[:10])
    return f"{head} :: {trail}"[:1500]


def surface(path: Path) -> None:
    try:
        tree = ET.parse(path)
    except (ET.ParseError, OSError):
        return
    for tc in tree.iter("testcase"):
        failures = list(tc.iter("failure")) + list(tc.iter("error"))
        for fail in failures:
            msg = (fail.get("message") or "") + " " + (fail.text or "")
            print(f"::error::FAILED {tc.get('classname')}.{tc.get('name')} :: {compress(msg)}")


def main(argv: list[str]) -> int:
    roots = argv[1:] or ["app/build/test-results"]
    found = False
    for root in roots:
        for path in Path(root).rglob("TEST-*.xml"):
            found = True
            surface(path)
    if not found:
        print("::warning::no TEST-*.xml files found to surface")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
