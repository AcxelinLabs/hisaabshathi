"""From what workers shared to things the team (or an agent) can improve the app with.

    python -m hisaab_improve.pipeline ingest <work_dir>    checked batches -> one folder per sample
    python -m hisaab_improve.pipeline report <work_dir>    where the app fails, per language -> report.md
    python -m hisaab_improve.pipeline export <work_dir>    cases.jsonl, to_label.jsonl, asr_manifest.jsonl
    python -m hisaab_improve.pipeline publish-hf <work_dir> --repo AcxelinLabs/hisaabsaathi-voices
                                                           (optional) the same, to the team's private dataset

The storage is chosen as for the service (HISAAB_STORAGE and the rest; see app.Config).
<work_dir> must be private storage the team controls (an encrypted disk, a private bucket
mount). Worker speech and text never go into the code repository: the app's tests read
cases.jsonl from the path in HISAAB_REAL_CASES.

Every run first removes everything derived from installs that asked for deletion.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import shutil
import sys
import unicodedata
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

from .storage import Storage

LANGS = {"hi", "or", "te", "kn", "ta", "en"}
SAMPLE_ID = re.compile(r"^[0-9a-f]{16}$")
INSTALL_ID = re.compile(r"^i[0-9a-f]{24}$")
OUTCOMES = {"answered", "not_understood", "saved", "corrected", "rejected", "dismissed", "confirm"}
MAX_UNZIPPED = 200 * 1024 * 1024
MAX_CLIP = 2 * 1024 * 1024  # 20 s at 16 kHz 16-bit is 640 KB


# ---------------------------------------------------------------- ingest

def _tombstones(storage: Storage) -> set[str]:
    return {k.split("/", 1)[1] for k in storage.keys("deleted/") if "/" in k}


def purge(work: Path, installs: set[str]) -> int:
    """Removes every sample of these installs from the work folder."""
    n = 0
    for d in (work / "samples").glob("*/*"):
        if d.is_dir() and d.name in installs:
            n += sum(1 for p in d.glob("*.json"))
            shutil.rmtree(d)
    return n


def _valid(s: dict) -> bool:
    return (isinstance(s, dict) and SAMPLE_ID.match(str(s.get("id", ""))) is not None and s.get("lang") in LANGS
            and s.get("source") in {"voice", "typed"} and s.get("outcome") in OUTCOMES
            and isinstance(s.get("heard"), str) and len(s["heard"]) < 2000)


def _is_wav(b: bytes) -> bool:
    return len(b) > 44 and b[:4] == b"RIFF" and b[8:12] == b"WAVE"


def read_batch(data: bytes) -> tuple[list[dict], dict[str, bytes]]:
    """The samples and clips in one batch zip. Names inside the zip are never used as paths."""
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        if sum(i.file_size for i in z.infolist()) > MAX_UNZIPPED:
            raise ValueError("batch unpacks too large")
        names = set(z.namelist())
        samples = []
        if "samples.jsonl" in names:
            for line in z.read("samples.jsonl").decode("utf-8").splitlines():
                if line.strip():
                    try:
                        s = json.loads(line)
                    except ValueError:
                        continue
                    if _valid(s):
                        samples.append(s)
        clips = {}
        for s in samples:
            name = f"audio/{s['id']}.wav"
            if s.get("hasAudio") and name in names and z.getinfo(name).file_size <= MAX_CLIP:
                b = z.read(name)
                if _is_wav(b):
                    clips[s["id"]] = b
    return samples, clips


def ingest(storage: Storage, work: Path) -> dict:
    work.mkdir(parents=True, exist_ok=True)
    done_file = work / "ingested.txt"
    done = set(done_file.read_text().split()) if done_file.exists() else set()
    dead = _tombstones(storage)
    stats = Counter(purged=purge(work, dead), samples=0, clips=0, batches=0)
    for key in sorted(storage.keys("incoming/")):
        parts = key.split("/")
        if len(parts) != 3 or not key.endswith(".json") or key in done:
            continue
        install = parts[1]
        if install in dead or not INSTALL_ID.match(install):
            continue
        meta = json.loads(b"".join(storage.chunks(key)))
        data = b"".join(storage.chunks(key[:-5] + ".zip"))
        if hashlib.sha256(data).hexdigest() != meta.get("sha256"):
            stats["damaged"] += 1
            continue
        try:
            samples, clips = read_batch(data)
        except (ValueError, zipfile.BadZipFile):
            stats["unreadable"] += 1
            continue
        for s in samples:
            d = work / "samples" / s["lang"] / install
            d.mkdir(parents=True, exist_ok=True)
            s = {**s, "install": install, "batch": meta.get("batch", ""), "received_at": meta.get("received_at", "")}
            if s["id"] in clips:
                (d / f"{s['id']}.wav").write_bytes(clips[s["id"]])
            (d / f"{s['id']}.json").write_text(json.dumps(s, ensure_ascii=False), encoding="utf-8")
            stats["samples"] += 1
            stats["clips"] += int(s["id"] in clips)
        done.add(key)
        stats["batches"] += 1
    done_file.write_text("\n".join(sorted(done)) + "\n")
    return dict(stats)


def samples(work: Path) -> list[dict]:
    out = []
    for p in sorted((work / "samples").glob("*/*/*.json")):
        s = json.loads(p.read_text(encoding="utf-8"))
        wav = p.with_suffix(".wav")
        s["audio_path"] = wav.relative_to(work).as_posix() if wav.exists() else ""  # relative: the folder can move
        out.append(s)
    return out


# ---------------------------------------------------------------- report

FAILED = ("not_understood", "rejected", "corrected", "dismissed")


def norm(text: str) -> str:
    t = unicodedata.normalize("NFC", text).lower()
    return re.sub(r"\s+", " ", re.sub(r"[^\w\s]", " ", t)).strip()


def report(work: Path) -> str:
    all_ = samples(work)
    by_lang = defaultdict(list)
    for s in all_:
        by_lang[s["lang"]].append(s)
    lines = ["# What workers shared: where the app fails", "",
             f"{len(all_)} exchanges from {len({s['install'] for s in all_})} phones.", ""]
    lines += ["| Language | Exchanges | With voice | Answered | Saved | Fixed | Said wrong | Not understood | Closed | Failure rate |",
              "|---|---|---|---|---|---|---|---|---|---|"]
    for lang in sorted(by_lang):
        ss = [s for s in by_lang[lang] if s["outcome"] != "confirm"]
        c = Counter(s["outcome"] for s in ss)
        n = len(ss) or 1
        fail = sum(c[o] for o in FAILED)
        lines.append(f"| {lang} | {len(ss)} | {sum(1 for s in ss if s.get('hasAudio'))} | {c['answered']} | {c['saved']} | "
                     f"{c['corrected']} | {c['rejected']} | {c['not_understood']} | {c['dismissed']} | {fail * 100 // n}% |")
    lines.append("")
    for lang in sorted(by_lang):
        lines += [f"## {lang}", ""]
        q = Counter(s.get("quality") or "no clip" for s in by_lang[lang] if s["source"] == "voice")
        if q:
            lines += ["Recording quality: " + ", ".join(f"{k} {v}" for k, v in q.most_common()), ""]
        for outcome, title in (("not_understood", "Not understood"), ("rejected", "Worker said it was wrong"), ("corrected", "Worker fixed it")):
            groups = defaultdict(list)
            for s in by_lang[lang]:
                if s["outcome"] == outcome:
                    groups[norm(s["heard"])].append(s)
            if not groups:
                continue
            lines += [f"### {title} ({sum(map(len, groups.values()))})", ""]
            for text, ss in sorted(groups.items(), key=lambda kv: -len(kv[1]))[:30]:
                s = ss[0]
                extra = f" → kept `{s.get('kept')}`" if outcome == "corrected" else ""
                lines.append(f"- {len(ss)}× “{s['heard']}” — understood `{s['understood']}`{extra}")
            lines.append("")
    text = "\n".join(lines) + "\n"
    (work / "report.md").write_text(text, encoding="utf-8")
    return text


# ---------------------------------------------------------------- export

def export(work: Path) -> dict:
    """
    cases.jsonl        sentences with a known right answer, for the app's RealCasesTest:
                       "gate": true  the worker confirmed it (saved as understood): must stay right;
                       "gate": false a target: the worker's fix, or an answered question (unconfirmed).
    to_label.jsonl     sentences with no known answer yet (not understood, said wrong): for a person,
                       or an agent whose labels a person checks.
    asr_manifest.jsonl clips for speech-model work: "text" is what the app's model heard, so it is
                       only a draft transcript; "trust" says how far the worker confirmed it.
    """
    stats = Counter(cases=0, to_label=0, clips=0)
    with open(work / "cases.jsonl", "w", encoding="utf-8") as cases, \
            open(work / "to_label.jsonl", "w", encoding="utf-8") as to_label, \
            open(work / "asr_manifest.jsonl", "w", encoding="utf-8") as asr:
        for s in samples(work):
            base = {"id": s["id"], "lang": s["lang"], "text": s["heard"]}
            o = s["outcome"]
            if o == "saved":
                case = {**base, "expect": s["understood"], "gate": True, "why": "worker saved it as understood"}
            elif o == "corrected" and s.get("kept"):
                case = {**base, "expect": "ENTRY " + s["kept"], "gate": False, "why": "worker fixed it"}
            elif o == "answered" and not s["understood"].startswith("MODEL"):
                case = {**base, "expect": s["understood"], "gate": False, "why": "answered, not confirmed"}
            else:
                case = None
            if case:
                cases.write(json.dumps(case, ensure_ascii=False) + "\n"); stats["cases"] += 1
            elif o in ("not_understood", "rejected"):
                to_label.write(json.dumps({**base, "understood": s["understood"], "outcome": o, "audio": s["audio_path"]}, ensure_ascii=False) + "\n")
                stats["to_label"] += 1
            if s["audio_path"] and s.get("quality") == "good":
                trust = "confirmed" if o in ("saved", "confirm") else "unverified"
                asr.write(json.dumps({"audio_filepath": s["audio_path"], "duration": s.get("audioSeconds", 0), "text": s["heard"],
                                      "lang": s["lang"], "trust": trust, "engine": s.get("engine", "")}, ensure_ascii=False) + "\n")
                stats["clips"] += 1
    return dict(stats)


# ---------------------------------------------------------------- Hugging Face (optional)

HF_FILES = ["samples/*", "cases.jsonl", "to_label.jsonl", "asr_manifest.jsonl", "report.md"]


def publish_hf(work: Path, repo: str, api=None) -> None:
    """
    Mirrors the work folder's samples and exports to a **private** Hugging Face dataset, then
    squashes the dataset's history: a plain upload would keep deleted installs' files in old
    commits, and "delete what I shared" must delete them for real. Other files in the dataset
    (its README, prompts/) are left alone. Refuses a public dataset.
    """
    if api is None:
        from huggingface_hub import HfApi

        api = HfApi()
    if not api.dataset_info(repo).private:
        raise SystemExit(f"{repo} is public: worker data may only go to a private dataset")
    api.upload_folder(repo_id=repo, repo_type="dataset", folder_path=str(work), allow_patterns=HF_FILES,
                      delete_patterns=HF_FILES, commit_message="Sync from the improve pipeline")
    api.super_squash_history(repo_id=repo, repo_type="dataset")


def main(argv=None):  # pragma: no cover
    from .app import Config  # the same storage settings as the service
    from .storage import AzureStorage, LocalStorage, S3Storage

    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("step", choices=["ingest", "report", "export", "all", "publish-hf"])
    p.add_argument("work_dir", type=Path)
    p.add_argument("--repo", default="AcxelinLabs/hisaabsaathi-voices")
    a = p.parse_args(argv)
    if a.step == "publish-hf":
        publish_hf(a.work_dir, a.repo)
        print("published to", a.repo)
        return 0
    if a.step in ("ingest", "all"):
        c = Config.from_env()
        storage = (S3Storage(c.s3_bucket, c.s3_region) if c.storage == "s3"
                   else AzureStorage(c.azure_container, c.azure_connection_string, c.azure_account_url) if c.storage == "azure"
                   else LocalStorage(c.local_dir, c.public_url, None))
        print("ingest:", ingest(storage, a.work_dir))
    if a.step in ("report", "all"):
        report(a.work_dir); print("report:", a.work_dir / "report.md")
    if a.step in ("export", "all"):
        print("export:", export(a.work_dir))


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
