#!/usr/bin/env python3
"""Print the unit-test code coverage from a Kover XML report as Markdown.

Usage: coverage-summary.py <reportDebug.xml>

CI appends the output to the run's summary page ($GITHUB_STEP_SUMMARY). It prints the overall line and branch
coverage and one row per package, least covered code last, so the table shows where the unit tests reach and where
they do not (the Compose screens are checked on a device, not by unit tests).
"""
import sys
import xml.etree.ElementTree as ET


def counts(element, kind):
    for counter in element.findall("counter"):
        if counter.get("type") == kind:
            return int(counter.get("covered")), int(counter.get("missed"))
    return 0, 0


def percent(covered, missed):
    total = covered + missed
    return "n/a" if total == 0 else f"{100 * covered / total:.1f}%"


def main(path):
    root = ET.parse(path).getroot()
    line_c, line_m = counts(root, "LINE")
    branch_c, branch_m = counts(root, "BRANCH")
    print("## Unit-test coverage")
    print()
    print(f"- **Lines:** {percent(line_c, line_m)} ({line_c} of {line_c + line_m})")
    print(f"- **Branches:** {percent(branch_c, branch_m)} ({branch_c} of {branch_c + branch_m})")
    print()
    print("| Package | Lines covered | Lines |")
    print("|---|---|---|")
    rows = []
    for package in root.findall("package"):
        covered, missed = counts(package, "LINE")
        if covered + missed:
            rows.append((package.get("name").replace("/", "."), covered, covered + missed))
    for name, covered, total in sorted(rows, key=lambda row: (-row[1] / row[2], -row[2])):
        print(f"| `{name}` | {percent(covered, total - covered)} | {total} |")
    print()
    print("Compose screens are checked on a device, so `ui` and `ui.components` show as mostly uncovered.")
    print("The full HTML report is in the `test-and-lint-reports` artifact.")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
