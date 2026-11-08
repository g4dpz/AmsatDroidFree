#!/usr/bin/env python3
"""Merge one GitHub traffic snapshot (referrers or popular paths) into a CSV.

Usage: traffic_ranked.py <response.json> <output.csv> <key-field> <snapshot-date>

GitHub returns these two endpoints only as a total for the trailing 14 days, with no per-day
breakdown. So each row records the 14-day total *as of* the snapshot date:

    snapshot,<key-field>,count,uniques

Running twice on the same date replaces that date's rows (idempotent); earlier dates are kept, so
the history survives beyond GitHub's window. To estimate what a source contributed on a given day,
compare consecutive snapshots.
"""
import csv
import json
import sys


def main(response_path, out_path, key_field, snapshot):
    items = json.load(open(response_path))
    if not isinstance(items, list):
        sys.exit(f"unexpected response (not a list): {str(items)[:200]}")

    rows = {}  # (snapshot, key) -> (count, uniques)
    try:
        with open(out_path, newline="") as f:
            reader = csv.reader(f)
            next(reader, None)  # header
            for row in reader:
                if len(row) >= 4:
                    rows[(row[0], row[1])] = (row[2], row[3])
    except FileNotFoundError:
        pass

    # Replace everything previously recorded for this snapshot date.
    rows = {k: v for k, v in rows.items() if k[0] != snapshot}
    for item in items:
        rows[(snapshot, item[key_field])] = (str(item["count"]), str(item["uniques"]))

    with open(out_path, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["snapshot", key_field, "count", "uniques"])
        for (snap, key) in sorted(rows):
            count, uniques = rows[(snap, key)]
            writer.writerow([snap, key, count, uniques])


if __name__ == "__main__":
    if len(sys.argv) != 5:
        sys.exit(__doc__)
    main(*sys.argv[1:])
