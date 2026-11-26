"""Shared pieces of the pipeline: identifiers, small storage helpers, the train/eval split.

Two stores, both private:
- raw: what the upload service received (incoming/<install>/<batch>.zip sealed, .json receipt;
  deleted/<install> tombstones);
- work: the pipeline's own state, opened and checked:

    index/<install>.jsonl            every kept exchange of one phone (text and metadata)
    audio/<install>/<id>.wav         its voice clips
    state/ingested/<install>/<batch> batches already read
    state/quarantine.json            phones held back from training, and why
    state/cleared.json               phones a person checked and released
    state/labels.jsonl               labels a person accepted
    state/aml.json                   snapshots registered as Azure ML data assets
    snapshots/<id>/...               frozen exports (see export.py); snapshots/latest names the newest
    reports/<id>.md, reports/latest.md
    audit/<time>.json                one record per run: counts only
"""
from __future__ import annotations

import hashlib
import json
import re
import unicodedata
from datetime import datetime, timezone

LANGS = ("hi", "or", "te", "kn", "ta", "en")
SAMPLE_ID = re.compile(r"^[0-9a-f]{16}$")
INSTALL_ID = re.compile(r"^i[0-9a-f]{24}$")
OUTCOMES = {"answered", "not_understood", "saved", "corrected", "rejected", "dismissed", "confirm"}
# Versions of the "Help improve HisaabSaathi?" question this pipeline knows (Improve.CONSENT_VERSION).
KNOWN_CONSENT = {1, 2, 3}  # 3: on by default from app 0.9.12, told once, can be turned off
# From this version on, the worker's yes also covers weekly counts (core/Usage.kt in the app).
COUNTS_FROM_CONSENT = 2
WEEK = re.compile(r"^20\d\d-W(0[1-9]|[1-4]\d|5[0-3])$")
# What the app counts each week (Usage.Event); nothing else is kept.
USAGE_KEYS = {
    "entries_saved", "entries_by_voice", "entries_typed", "entries_from_photo", "entries_fixed_before_saving",
    "said_wrong", "entries_edited_later", "entries_deleted", "questions_answered", "not_understood",
    "photos_taken", "evidence_packs_shared", "reports_shared", "days_used",
}


def now() -> datetime:
    return datetime.now(timezone.utc)


def stamp(t: datetime | None = None) -> str:
    return (t or now()).strftime("%Y%m%dT%H%M%SZ")


def read_bytes(store, key: str) -> bytes | None:
    return b"".join(store.chunks(key)) if store.exists(key) else None


def read_json(store, key: str, default):
    b = read_bytes(store, key)
    return json.loads(b) if b is not None else default


def write_json(store, key: str, obj) -> None:
    store.put(key, json.dumps(obj, ensure_ascii=False, indent=1, sort_keys=True).encode())


def read_jsonl(store, key: str) -> list[dict]:
    b = read_bytes(store, key)
    return [json.loads(l) for l in b.decode("utf-8").splitlines() if l.strip()] if b else []


def jsonl(rows) -> bytes:
    return "".join(json.dumps(r, ensure_ascii=False, sort_keys=True) + "\n" for r in rows).encode()


def norm(text: str) -> str:
    t = unicodedata.normalize("NFC", text).lower()
    return re.sub(r"\s+", " ", re.sub(r"[^\w\s]", " ", t)).strip()


def split_of(install: str) -> str:
    """About one phone in ten is held out for evaluation, always the same ones: a speaker is
    never both trained on and tested on."""
    return "eval" if int(hashlib.sha256(install.encode()).hexdigest()[:8], 16) % 10 == 0 else "train"


def installs(work) -> list[str]:
    return sorted(k[len("index/"):-len(".jsonl")] for k in work.keys("index/") if k.endswith(".jsonl"))
