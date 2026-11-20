"""People check labels and phones; nothing an agent proposes counts until a person accepts it.

    review-sheet <out.csv>                     sentences to label (with any proposals) -> a sheet for Excel
    review-apply <in.csv> --reviewer <name>    accepted rows become labels (checked format)
    clear <install> --reviewer <name>          releases a held-back phone after looking at it
"""
from __future__ import annotations

import csv
import io

from .common import jsonl, now, read_bytes, read_json, read_jsonl, write_json
from .export import LABEL

COLUMNS = ["id", "lang", "text", "outcome", "app_understood", "proposed_label", "accept", "correct_label"]


def sheet(work, proposals: dict[str, str] | None = None) -> str:
    sid = (read_bytes(work, "snapshots/latest") or b"").decode()
    rows = []
    for split in ("train", "eval"):
        rows += read_jsonl(work, f"snapshots/{sid}/{split}/to_label.jsonl")
    done = {l["id"] for l in read_jsonl(work, "state/labels.jsonl")}
    out = io.StringIO()
    w = csv.DictWriter(out, COLUMNS)
    w.writeheader()
    for r in rows:
        if r["id"] in done:
            continue
        w.writerow({"id": r["id"], "lang": r["lang"], "text": r["text"], "outcome": r["outcome"], "app_understood": r["understood"],
                    "proposed_label": (proposals or {}).get(r["id"], ""), "accept": "", "correct_label": ""})
    return out.getvalue()


def apply(work, csv_text: str, reviewer: str) -> dict:
    """Rows with accept = yes take proposed_label; rows with correct_label take that. Bad labels are skipped."""
    if not reviewer.strip():
        raise ValueError("a reviewer name is needed")
    owner = {}
    for k in work.keys("index/"):
        for r in read_jsonl(work, k):
            owner[r["id"]] = r["install"]
    labels = read_jsonl(work, "state/labels.jsonl")
    have = {l["id"] for l in labels}
    stats = {"accepted": 0, "skipped": 0}
    for row in csv.DictReader(io.StringIO(csv_text.lstrip("﻿"))):
        label = (row.get("correct_label") or "").strip() or (
            (row.get("proposed_label") or "").strip() if (row.get("accept") or "").strip().lower() in ("y", "yes") else "")
        sid = (row.get("id") or "").strip()
        if not label:
            continue
        if not LABEL.match(label) or sid not in owner or sid in have:
            stats["skipped"] += 1
            continue
        labels.append({"id": sid, "install": owner[sid], "label": label, "reviewer": reviewer.strip(), "at": now().isoformat(timespec="seconds")})
        have.add(sid)
        stats["accepted"] += 1
    work.put("state/labels.jsonl", jsonl(labels))
    return stats


def clear(work, install: str, reviewer: str) -> None:
    if not reviewer.strip():
        raise ValueError("a reviewer name is needed")
    cleared = read_json(work, "state/cleared.json", {})
    cleared[install] = {"by": reviewer.strip(), "at": now().isoformat(timespec="seconds")}
    write_json(work, "state/cleared.json", cleared)
