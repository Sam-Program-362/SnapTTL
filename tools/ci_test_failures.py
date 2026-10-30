#!/usr/bin/env python3
"""Emit JUnit XML test failures as GitHub Actions error annotations.

Usage: python3 tools/ci_test_failures.py <test-results-dir> [<test-results-dir> ...]

Parses every TEST-*.xml under the given directories and prints one
``::error::`` annotation per failed/errored test case so failures are visible
without downloading log artifacts.
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def surface(path: Path) -> None:
    try:
        tree = ET.parse(path)
    except (ET.ParseError, OSError):
        return
    for tc in tree.iter("testcase"):
        failures = list(tc.iter("failure")) + list(tc.iter("error"))
        for fail in failures:
            msg = ((fail.get("message") or "") + " " + (fail.text or ""))
            msg = " ".join(msg.split())[:1600]
            print(f"::error::FAILED {tc.get('classname')}.{tc.get('name')} :: {msg}")


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
