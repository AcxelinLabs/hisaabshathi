"""Reads new batches from the raw store into the work store, and carries out deletions."""
from __future__ import annotations

import hashlib
import io
import json
import zipfile
from collections import Counter

from ..crypto import SealError, open_sealed
from ..storage import delete_prefix
from .common import INSTALL_ID, jsonl, read_bytes, read_json, read_jsonl, write_json
from .screening import sensitive, valid

MAX_UNZIPPED = 200 * 1024 * 1024
MAX_CLIP = 2 * 1024 * 1024  # 20 s at 16 kHz 16-bit is 640 KB


def tombstones(raw) -> set[str]:
    return {k.split("/", 1)[1] for k in raw.keys("deleted/") if "/" in k}


def purge(work, dead: set[str]) -> dict:
    """Removes every trace of these phones from the work store, and retires any snapshot that
    included them (a frozen export must not keep a deleted phone's data)."""
    stats = Counter()
    for install in sorted(dead):
        if work.exists(f"index/{install}.jsonl"):
            stats["phones"] += 1
            stats["exchanges"] += len(read_jsonl(work, f"index/{install}.jsonl"))
            work.delete(f"index/{install}.jsonl")
        stats["files"] += delete_prefix(work, f"audio/{install}/") + delete_prefix(work, f"state/ingested/{install}/")
    for name in ("state/quarantine.json", "state/cleared.json"):
        d = read_json(work, name, {})
        if dead & d.keys():
            write_json(work, name, {k: v for k, v in d.items() if k not in dead})
    labels = read_jsonl(work, "state/labels.jsonl")
    if any(l.get("install") in dead for l in labels):
        work.put("state/labels.jsonl", jsonl(l for l in labels if l.get("install") not in dead))
    for key in list(work.keys("snapshots/")):
        if key.endswith("/manifest.json") and dead & set(read_json(work, key, {}).get("installs", [])):
            delete_prefix(work, key[: -len("manifest.json")])
            stats["snapshots_retired"] += 1
    return dict(stats)


def read_batch(data: bytes) -> tuple[list[dict], dict[str, bytes], int]:
    """Samples and clips of one opened batch, and how many samples were dropped. Names inside
    the zip are never used as paths."""
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        if sum(i.file_size for i in z.infolist()) > MAX_UNZIPPED:
            raise ValueError("batch unpacks too large")
        names = set(z.namelist())
        samples, dropped = [], 0
        if "samples.jsonl" in names:
            for line in z.read("samples.jsonl").decode("utf-8").splitlines():
                if not line.strip():
                    continue
                try:
                    s = json.loads(line)
                except ValueError:
                    dropped += 1
                    continue
                if valid(s) and not any(sensitive(s.get(f)) for f in ("heard", "kept", "reply")):
                    samples.append(s)
                else:
                    dropped += 1
        clips = {}
        for s in samples:
            name = f"audio/{s['id']}.wav"
            if s.get("hasAudio") and name in names and z.getinfo(name).file_size <= MAX_CLIP:
                b = z.read(name)
                if len(b) > 44 and b[:4] == b"RIFF" and b[8:12] == b"WAVE":
                    clips[s["id"]] = b
    return samples, clips, dropped


def ingest(raw, work, keys: dict) -> dict:
    """Reads each checked batch once: verifies its fingerprint, opens the seal, drops anything
    malformed, without consent, or holding identifying numbers, and files the rest by phone.
    Deletions are carried out first."""
    dead = tombstones(raw)
    stats = Counter({f"purged_{k}": v for k, v in purge(work, dead).items()})
    for key in sorted(raw.keys("incoming/")):
        parts = key.split("/")
        if len(parts) != 3 or not key.endswith(".json"):
            continue
        install, batch = parts[1], parts[2][:-5]
        if install in dead or not INSTALL_ID.match(install) or work.exists(f"state/ingested/{install}/{batch}"):
            continue
        meta = read_json(raw, key, {})
        data = read_bytes(raw, key[:-5] + ".zip") or b""
        if hashlib.sha256(data).hexdigest() != meta.get("sha256"):
            stats["batches_damaged"] += 1
            work.put(f"state/ingested/{install}/{batch}", b"damaged")
            continue
        try:
            samples, clips, dropped = read_batch(open_sealed(data, keys))
        except SealError as e:
            if "does not hold" in str(e):
                stats["batches_waiting_for_key"] += 1  # tried again next run, once the key is configured
                continue
            stats["batches_unreadable"] += 1
            work.put(f"state/ingested/{install}/{batch}", b"unreadable")
            continue
        except (ValueError, zipfile.BadZipFile):
            stats["batches_unreadable"] += 1
            work.put(f"state/ingested/{install}/{batch}", b"unreadable")
            continue
        index = read_jsonl(work, f"index/{install}.jsonl")
        seen = {r["id"] for r in index}
        for s in samples:
            if s["id"] in seen:
                continue
            row = {**s, "install": install, "batch": batch, "received_at": meta.get("received_at", "")}
            if s["id"] in clips:
                row["audio_sha256"] = hashlib.sha256(clips[s["id"]]).hexdigest()
                work.put(f"audio/{install}/{s['id']}.wav", clips[s["id"]])
                stats["clips"] += 1
            else:
                row["hasAudio"] = False
            index.append(row)
            seen.add(s["id"])
            stats["exchanges"] += 1
        stats["exchanges_dropped"] += dropped
        work.put(f"index/{install}.jsonl", jsonl(index))
        work.put(f"state/ingested/{install}/{batch}", b"ok")
        stats["batches"] += 1
    return dict(stats)


def expire_raw(raw, work, days: int, today: str) -> int:
    """Deletes raw batches more than [days] old that were already read (by the date in their name)."""
    from datetime import datetime, timedelta

    cutoff = (datetime.strptime(today, "%Y%m%d") - timedelta(days=days)).strftime("%Y%m%d")
    n = 0
    for key in list(raw.keys("incoming/")):
        parts = key.split("/")
        if len(parts) != 3:
            continue
        batch = parts[2].rsplit(".", 1)[0]
        if batch[:8] < cutoff and work.exists(f"state/ingested/{parts[1]}/{batch}"):
            raw.delete(key)
            n += 1
    return n
