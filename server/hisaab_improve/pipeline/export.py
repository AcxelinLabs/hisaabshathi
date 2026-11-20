"""Screens phones, then freezes what training and testing may use into a snapshot.

snapshots/<id>/
    manifest.json        what is in it: phones, counts, thresholds, code version
    train/ and eval/     cases.jsonl, to_label.jsonl, asr_manifest.jsonl (eval = held-out phones)
    agent/               what an AI agent may read: train only, text only, no phone ids
    report.md            where the app fails, per language

A snapshot is never changed. If a phone in it asks for deletion, the whole snapshot is
retired (ingest.purge) and the next run makes a new one without it.
"""
from __future__ import annotations

import hashlib
import os
import re
from collections import Counter, defaultdict

from .. import __version__
from ..storage import delete_prefix
from .common import installs, jsonl, norm, now, read_bytes, read_json, read_jsonl, split_of, stamp, write_json
from .screening import THRESHOLDS, phone_flags, shared_recordings

PER_PHONE_CAP = 300  # no single phone may make up much of what the app learns from
KEEP_SNAPSHOTS = 7
LABEL = re.compile(r"^(ASK (DAY|WEEK|MONTH|YEAR|YESTERDAY|LAST_WEEK|LAST_MONTH) (ALL|EARNED|ADVANCE|EXPENSE|DUE|MIN_WAGE)"
                   r"|TALK [A-Z_]+|NONE|ENTRY (WORK|ADVANCE|EXPENSE|WAGE_RECEIVED|WAGE_DUE)\b.*)$")
FAILED = ("not_understood", "rejected", "corrected", "dismissed")


def screen(work) -> dict:
    """Updates the quarantine list. Returns {install: reasons} for phones held back now."""
    by_install = {i: read_jsonl(work, f"index/{i}.jsonl") for i in installs(work)}
    shared = shared_recordings(by_install)
    quarantine = read_json(work, "state/quarantine.json", {})
    cleared = read_json(work, "state/cleared.json", {})
    for install, rows in by_install.items():
        reasons = phone_flags(rows, shared)
        if reasons and install not in quarantine:
            quarantine[install] = {"reasons": reasons, "since": now().isoformat(timespec="seconds")}
        elif reasons:
            quarantine[install]["reasons"] = reasons
        elif install in quarantine:
            del quarantine[install]
    write_json(work, "state/quarantine.json", quarantine)
    return {i: q["reasons"] for i, q in quarantine.items() if i not in cleared}


def _rank(sample_id: str) -> str:
    return hashlib.sha256(sample_id.encode()).hexdigest()


def snapshot(work, held_back: dict) -> dict:
    labels = {l["id"]: l for l in read_jsonl(work, "state/labels.jsonl")}
    out = {s: {"cases": [], "to_label": [], "asr_manifest": []} for s in ("train", "eval")}
    agent = {"cases": [], "to_label": []}
    included, counts = [], Counter()
    for install in installs(work):
        if install in held_back:
            counts["phones_held_back"] += 1
            continue
        rows = sorted(read_jsonl(work, f"index/{install}.jsonl"), key=lambda r: _rank(r["id"]))[:PER_PHONE_CAP]
        if not rows:
            continue
        included.append(install)
        split = split_of(install)
        for r in rows:
            base = {"id": r["id"], "lang": r["lang"], "text": r["heard"]}
            o, case = r["outcome"], None
            if r["id"] in labels:
                case = {**base, "expect": labels[r["id"]]["label"], "gate": True, "why": "label checked by a person"}
            elif o == "saved":
                case = {**base, "expect": r["understood"], "gate": True, "why": "worker saved it as understood"}
            elif o == "corrected" and r.get("kept"):
                case = {**base, "expect": "ENTRY " + r["kept"], "gate": False, "why": "worker fixed it"}
            elif o == "answered" and not r["understood"].startswith("MODEL"):
                case = {**base, "expect": r["understood"], "gate": False, "why": "answered, not confirmed"}
            if case:
                out[split]["cases"].append(case)
                if split == "train":
                    agent["cases"].append({k: case[k] for k in ("id", "lang", "text", "expect", "gate")})
            elif o in ("not_understood", "rejected"):
                item = {**base, "understood": r["understood"], "outcome": o}
                out[split]["to_label"].append(item)
                if split == "train":
                    agent["to_label"].append(item)
            if r.get("audio_sha256") and r.get("quality") == "good":
                trust = "confirmed" if o in ("saved", "confirm") else "unverified"
                out[split]["asr_manifest"].append({
                    "id": r["id"], "audio_filepath": f"audio/{install}/{r['id']}.wav", "duration": r.get("audioSeconds", 0),
                    "text": r["heard"], "lang": r["lang"], "trust": trust, "engine": r.get("engine", ""),
                })
            counts[f"{split}_exchanges"] += 1
    sid = f"{stamp()}-{os.urandom(2).hex()}"  # time first, so names sort in order; unique even within a second
    base = f"snapshots/{sid}/"
    for split, files in out.items():
        for name, rows in files.items():
            work.put(f"{base}{split}/{name}.jsonl", jsonl(rows))
            counts[f"{split}_{name}"] = len(rows)
    for name, rows in agent.items():
        work.put(f"{base}agent/{name}.jsonl", jsonl(rows))
    manifest = {
        "snapshot": sid, "created_at": now().isoformat(timespec="seconds"), "code_version": __version__,
        "installs": included, "counts": dict(counts), "per_phone_cap": PER_PHONE_CAP, "thresholds": THRESHOLDS,
    }
    write_json(work, f"{base}manifest.json", manifest)
    work.put(f"{base}report.md", report(work, included, held_back).encode())
    work.put(f"{base}agent/report.md", report(work, [i for i in included if split_of(i) == "train"], held_back, quotes=True).encode())
    work.put("snapshots/latest", sid.encode())
    # Keep the newest few; older snapshots are deleted.
    ids = sorted({k.split("/")[1] for k in work.keys("snapshots/") if k.count("/") >= 2})
    for old in ids[:-KEEP_SNAPSHOTS]:
        delete_prefix(work, f"snapshots/{old}/")
    return manifest


def report(work, included: list[str], held_back: dict, quotes: bool = True) -> str:
    rows = [r for i in included for r in read_jsonl(work, f"index/{i}.jsonl")]
    by_lang = defaultdict(list)
    for r in rows:
        by_lang[r["lang"]].append(r)
    lines = ["# Where the app fails", "", f"{len(rows)} exchanges from {len(included)} phones; "
             f"{len(held_back)} phones held back for a person to check.", "",
             "| Language | Exchanges | With voice | Answered | Saved | Fixed | Said wrong | Not understood | Closed | Failure rate |",
             "|---|---|---|---|---|---|---|---|---|---|"]
    for lang in sorted(by_lang):
        ss = [s for s in by_lang[lang] if s["outcome"] != "confirm"]
        c = Counter(s["outcome"] for s in ss)
        fail = sum(c[o] for o in FAILED)
        lines.append(f"| {lang} | {len(ss)} | {sum(1 for s in ss if s.get('audio_sha256'))} | {c['answered']} | {c['saved']} | "
                     f"{c['corrected']} | {c['rejected']} | {c['not_understood']} | {c['dismissed']} | {fail * 100 // max(len(ss), 1)}% |")
    lines.append("")
    if held_back:
        why = Counter(r for reasons in held_back.values() for r in reasons)
        lines += ["## Phones held back", ""] + [f"- {n}: {r}" for r, n in why.most_common()] + [""]
    if quotes:
        for lang in sorted(by_lang):
            lines += [f"## {lang}", ""]
            for outcome, title in (("not_understood", "Not understood"), ("rejected", "Worker said it was wrong"), ("corrected", "Worker fixed it")):
                groups = defaultdict(list)
                for s in by_lang[lang]:
                    if s["outcome"] == outcome:
                        groups[norm(s["heard"])].append(s)
                if not groups:
                    continue
                lines += [f"### {title} ({sum(map(len, groups.values()))})", ""]
                for _, ss in sorted(groups.items(), key=lambda kv: -len(kv[1]))[:30]:
                    s = ss[0]
                    extra = f" → kept `{s.get('kept')}`" if outcome == "corrected" else ""
                    lines.append(f"- {len(ss)}× “{s['heard']}” — understood `{s['understood']}`{extra}")
                lines.append("")
    return "\n".join(lines) + "\n"


def export(work) -> dict:
    held_back = screen(work)
    m = snapshot(work, held_back)
    work.put("reports/latest.md", read_bytes(work, f"snapshots/{m['snapshot']}/report.md") or b"")
    return m
