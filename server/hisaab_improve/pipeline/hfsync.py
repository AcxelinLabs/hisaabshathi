"""Mirrors the newest snapshot to the team's private Hugging Face dataset.

Layout in the dataset (its README and prompts/ are the team's and are left alone):

    data/manifest.json, data/report.md
    data/train/*.jsonl, data/eval/*.jsonl
    audio/<install>/<id>.wav       the clips the snapshot's asr_manifest lists

Only what changed is uploaded. Whenever something is removed (a phone asked for deletion, or
dropped out), the dataset's history is squashed too: deleted data must not survive in old commits.
Refuses a dataset that is not private.
"""
from __future__ import annotations

import hashlib

from .common import read_bytes, read_json, read_jsonl, write_json

MANAGED = ("data/", "audio/")


def wanted(work, sid: str) -> dict[str, tuple[str, str]]:
    """path in dataset -> (sha256, key in work store)."""
    out = {}
    base = f"snapshots/{sid}/"
    for key in work.keys(base):
        rel = key[len(base):]
        if rel.startswith("agent/"):
            continue
        out[f"data/{rel}"] = (hashlib.sha256(read_bytes(work, key)).hexdigest(), key)
    index = {}
    for split in ("train", "eval"):
        for r in read_jsonl(work, f"{base}{split}/asr_manifest.jsonl"):
            install = r["audio_filepath"].split("/")[1]
            if install not in index:
                index[install] = {x["id"]: x for x in read_jsonl(work, f"index/{install}.jsonl")}
            row = index[install].get(r["id"])
            if row and row.get("audio_sha256"):
                out[r["audio_filepath"]] = (row["audio_sha256"], r["audio_filepath"])
    return out


def sync(work, repo: str, api=None, batch: int = 200) -> dict:
    if api is None:
        from huggingface_hub import HfApi

        api = HfApi()
    if not api.dataset_info(repo).private:
        raise SystemExit(f"{repo} is public: worker data may only go to a private dataset")
    from huggingface_hub import CommitOperationAdd, CommitOperationDelete

    sid = (read_bytes(work, "snapshots/latest") or b"").decode()
    if not sid:
        return {"skipped": "no snapshot"}
    have = read_json(work, "state/hf_synced.json", {})
    want = wanted(work, sid)
    adds = [p for p, (sha, _) in want.items() if have.get(p) != sha]
    deletes = [p for p in have if p not in want]
    ops = [CommitOperationDelete(path_in_repo=p) for p in deletes]
    ops += [CommitOperationAdd(path_in_repo=p, path_or_fileobj=read_bytes(work, want[p][1])) for p in adds]
    for i in range(0, len(ops), batch):
        api.create_commit(repo_id=repo, repo_type="dataset", operations=ops[i:i + batch],
                          commit_message=f"Snapshot {sid} ({i // batch + 1})")
    if deletes:
        api.super_squash_history(repo_id=repo, repo_type="dataset")
    write_json(work, "state/hf_synced.json", {p: sha for p, (sha, _) in want.items()})
    return {"snapshot": sid, "added": len(adds), "deleted": len(deletes), "history_squashed": bool(deletes)}
