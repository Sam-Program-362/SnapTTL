#!/usr/bin/env python3
"""Surface CI failures as GitHub Actions annotations.

Usage: python3 tools/ci_test_failures.py <dir> [<dir> ...]

Handles two report formats found under ``app/build``:

* JUnit XML (``TEST-*.xml``) — failed/errored test cases.
* Lint XML (``lint-results-*.xml``) — issues with severity Error/Warning.

GitHub truncates annotation bodies, so stacks are compressed to a frame trail.
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


def surface_junit(path: Path) -> None:
    try:
        tree = ET.parse(path)
    except (ET.ParseError, OSError):
        return
    for tc in tree.iter("testcase"):
        failures = list(tc.iter("failure")) + list(tc.iter("error"))
        for fail in failures:
            msg = (fail.get("message") or "") + " " + (fail.text or "")
            print(f"::error::FAILED {tc.get('classname')}.{tc.get('name')} :: {compress(msg)}")


def surface_lint(path: Path) -> None:
    try:
        tree = ET.parse(path)
    except (ET.ParseError, OSError):
        return
    for issue in tree.iter("issue"):
        severity = (issue.get("severity") or "error").lower()
        if severity not in ("error", "fatal", "warning", "informational"):
            continue
        kind = "error" if severity in ("error", "fatal") else "warning"
        location = issue.find("location")
        where = ""
        if location is not None:
            where = f" @ {location.get('file', '?')}:{location.get('line', '?')}"
        print(f"::{kind}::LINT [{issue.get('id')}] {issue.get('message')}{where}"[:1500])


def sniff_and_surface(path: Path) -> None:
    if path.name.startswith("TEST-"):
        surface_junit(path)
    elif path.name.startswith("lint-results"):
        surface_lint(path)


def main(argv: list[str]) -> int:
    roots = argv[1:] or ["app/build"]
    found = False
    for root in roots:
        base = Path(root)
        for pattern in ("TEST-*.xml", "lint-results*.xml"):
            for path in base.rglob(pattern):
                found = True
                sniff_and_surface(path)
    if not found:
        print("::warning::no TEST-*.xml or lint-results*.xml found to surface")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
