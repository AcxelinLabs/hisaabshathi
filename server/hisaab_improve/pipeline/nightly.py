"""One unattended run: what the scheduled job does every night.

1. ingest: new batches in, deletions carried out (and snapshots that held them retired);
2. export: phones screened, a new snapshot frozen, the failure report written;
3. Hugging Face: the snapshot mirrored to the private dataset (if configured);
4. tidy: raw batches older than HISAAB_RAW_DAYS that were read are deleted;
5. audit: a record of the run (counts only) in the work store;
6. tell the team: a short summary to a webhook (counts only, never workers' words).
"""
from __future__ import annotations

import json
import os
import urllib.request

from .common import now, stamp, write_json
from .export import export
from .ingest import expire_raw, ingest


def summary_text(run: dict) -> str:
    i, e = run["ingest"], run["export"]["counts"]
    lines = [
        f"HisaabSaathi pipeline {run['run']}: snapshot {run['export']['snapshot']}",
        f"New: {i.get('batches', 0)} batches, {i.get('exchanges', 0)} exchanges, {i.get('clips', 0)} clips; "
        f"dropped {i.get('exchanges_dropped', 0)} (malformed, no consent, or identifying numbers).",
        f"Deletions: {i.get('purged_phones', 0)} phones, {i.get('purged_snapshots_retired', 0)} snapshots retired.",
        f"Snapshot: {e.get('train_exchanges', 0)} train / {e.get('eval_exchanges', 0)} eval exchanges; "
        f"{e.get('phones_held_back', 0)} phones held back for checking.",
    ]
    for k in ("batches_damaged", "batches_unreadable", "batches_waiting_for_key"):
        if i.get(k):
            lines.append(f"Attention: {i[k]} {k.replace('_', ' ')}.")
    if run.get("hf"):
        lines.append(f"Hugging Face: {json.dumps(run['hf'])}")
    if run.get("hf_error"):
        lines.append(f"Hugging Face sync failed: {run['hf_error']}")
    return "\n".join(lines)


def notify(url: str, text: str) -> None:
    req = urllib.request.Request(url, data=json.dumps({"text": text}).encode(), headers={"Content-Type": "application/json"})
    urllib.request.urlopen(req, timeout=30).close()


def run(raw, work, keys: dict, hf_repo: str | None = None, hf_api=None, raw_days: int | None = None,
        webhook: str | None = None) -> dict:
    started = now()
    result = {"run": stamp(started), "ingest": ingest(raw, work, keys)}
    result["export"] = export(work)
    if hf_repo:
        from .hfsync import sync

        try:
            result["hf"] = sync(work, hf_repo, hf_api)
        except Exception as e:  # the next run tries again; everything else still counts
            result["hf_error"] = f"{type(e).__name__}: {e}"[:300]
    if raw_days:
        result["raw_expired"] = expire_raw(raw, work, raw_days, started.strftime("%Y%m%d"))
    result["seconds"] = round((now() - started).total_seconds(), 1)
    write_json(work, f"audit/{result['run']}.json", {**result, "export": {k: v for k, v in result["export"].items() if k != "installs"}})
    if webhook:
        try:
            notify(webhook, summary_text(result))
        except Exception as e:
            result["notify_error"] = type(e).__name__
    return result


def from_env() -> dict:
    e = os.environ.get
    return {
        "hf_repo": e("HISAAB_HF_DATASET") if e("HF_TOKEN") else None,
        "raw_days": int(e("HISAAB_RAW_DAYS", "0")) or None,
        "webhook": e("HISAAB_NOTIFY_WEBHOOK") or None,
    }
