"""List failing/erroring test cases from surefire XML reports.

Usage: failures.py <reports-dir> [--detail]
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

reports = sys.argv[1]
detail = "--detail" in sys.argv
rows = []
for path in sorted(glob.glob(os.path.join(reports, "TEST-*.xml"))):
    root = ET.parse(path).getroot()
    for tc in root.iter("testcase"):
        for kind in ("failure", "error"):
            el = tc.find(kind)
            if el is not None:
                msg = (el.get("message") or "").replace("\n", " ")[:300]
                typ = el.get("type") or ""
                rows.append((tc.get("classname"), tc.get("name"), kind, typ, msg,
                             (el.text or "")))
for cls, name, kind, typ, msg, text in rows:
    print(f"{kind[0].upper()} {cls}#{name} :: {typ} :: {msg}")
    if detail:
        lines = [l for l in text.splitlines() if l.strip()][:25]
        print("    " + "\n    ".join(lines))
print(f"total {len(rows)}")
