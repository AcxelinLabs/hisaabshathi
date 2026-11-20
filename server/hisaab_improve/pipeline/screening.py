"""Checks on what phones sent, before anything is used.

Per exchange (at ingest): the structure is right, the consent version is known, and it holds
no phone, Aadhaar, bank or card number, UPI id or email. The app already refuses to keep such
exchanges; this is the second line.

Per phone (every run): signs that a phone is not a real worker using the app, or is replaying
or flooding, so it cannot steer training. Such phones are quarantined: kept, but left out of
every export until a person looks and clears them.
"""
from __future__ import annotations

import re
import unicodedata
from collections import Counter, defaultdict

from .common import KNOWN_CONSENT, LANGS, OUTCOMES, SAMPLE_ID

LONG_NUMBER = re.compile(r"\d(?:[\s\-.]?\d){9,}")
HANDLE = re.compile(r"[\w.\-]{2,}@[\w.\-]{2,}")

# Unicode blocks of the app's Indian languages.
SCRIPTS = {"hi": (0x0900, 0x097F), "or": (0x0B00, 0x0B7F), "ta": (0x0B80, 0x0BFF), "te": (0x0C00, 0x0C7F), "kn": (0x0C80, 0x0CFF)}

THRESHOLDS = {
    "max_per_day": 600,          # a phone sends at most 24 batches of 25 a day
    "dup_audio_ratio": 0.2,      # byte-identical clips: a recording played again and again
    "dup_audio_min": 10,
    "bad_clip_ratio": 0.6,       # mostly silent or empty clips
    "bad_clip_min": 20,
    "wrong_script_ratio": 0.3,   # text in a different Indian script from the app's language
    "wrong_script_min": 10,
}


def _ascii_digits(text: str) -> str:
    return "".join(str(unicodedata.decimal(c)) if unicodedata.decimal(c, None) is not None else c for c in text)


def sensitive(text: str | None) -> bool:
    """Same rule as the app (Improve.sensitive): 10+ digits, even spaced or in Indian scripts, or a UPI id / email."""
    if not text:
        return False
    return bool(LONG_NUMBER.search(_ascii_digits(text)) or HANDLE.search(text))


def valid(s: dict) -> bool:
    return (isinstance(s, dict) and SAMPLE_ID.match(str(s.get("id", ""))) is not None and s.get("lang") in LANGS
            and s.get("source") in {"voice", "typed"} and s.get("outcome") in OUTCOMES
            and isinstance(s.get("heard"), str) and 0 < len(s["heard"]) < 2000
            and s.get("consent") in KNOWN_CONSENT)


def wrong_script(text: str, lang: str) -> bool:
    """Mostly letters of another Indian script than the app's language (Latin is fine: people type in English letters)."""
    if lang not in SCRIPTS:
        return False
    counts = Counter()
    for c in text:
        if c.isalpha():
            o = ord(c)
            counts[next((l for l, (a, b) in SCRIPTS.items() if a <= o <= b), "other")] += 1
    total = sum(counts.values())
    if total < 3:
        return False
    other_indic = sum(v for k, v in counts.items() if k in SCRIPTS and k != lang)
    return other_indic / total > 0.5


def phone_flags(rows: list[dict], shared_audio: set[str], t=THRESHOLDS) -> list[str]:
    """Reasons to hold this phone back from training; empty if none."""
    reasons = []
    per_day = Counter(str(r.get("received_at", ""))[:10] for r in rows)
    if per_day and max(per_day.values()) > t["max_per_day"]:
        reasons.append(f"more than {t['max_per_day']} exchanges in one day")
    clips = [r for r in rows if r.get("audio_sha256")]
    if len(clips) >= t["dup_audio_min"]:
        c = Counter(r["audio_sha256"] for r in clips)
        dup = sum(n for n in c.values() if n > 1)
        if dup / len(clips) > t["dup_audio_ratio"]:
            reasons.append("the same recording sent again and again")
    if any(r.get("audio_sha256") in shared_audio for r in clips):
        reasons.append("a recording also sent by other phones")
    if len(clips) >= t["bad_clip_min"]:
        bad = sum(1 for r in clips if r.get("quality") in ("silent", "short"))
        if bad / len(clips) > t["bad_clip_ratio"]:
            reasons.append("mostly silent or empty recordings")
    texts = [r for r in rows if r.get("lang") in SCRIPTS]
    if len(texts) >= t["wrong_script_min"]:
        bad = sum(1 for r in texts if wrong_script(r.get("heard", ""), r["lang"]))
        if bad / len(texts) > t["wrong_script_ratio"]:
            reasons.append("text mostly in a different language's script")
    return reasons


def shared_recordings(by_install: dict[str, list[dict]]) -> set[str]:
    """Clip hashes that arrived from more than one phone."""
    owners = defaultdict(set)
    for install, rows in by_install.items():
        for r in rows:
            if r.get("audio_sha256"):
                owners[r["audio_sha256"]].add(install)
    return {h for h, o in owners.items() if len(o) > 1}
