"""python -m hisaab_improve.pipeline <command>

    nightly                         the whole unattended run (what the scheduled job does)
    ingest | export                 one step
    fetch <dir> [--agent]           the newest snapshot to a local folder (--agent: only what an agent may read)
    review-sheet <out.csv> [--proposals f]  sentences to label, for a person (Excel opens it),
                                    with an agent's proposed_labels.jsonl if given
    review-apply <in.csv> --reviewer NAME
    clear <install> --reviewer NAME release a held-back phone after checking it
    publish-hf                      mirror the newest snapshot to the private Hugging Face dataset

Stores come from the environment:
    raw  (what the service received): HISAAB_STORAGE = local | s3 | azure, as for the service
    work (the pipeline's own):        HISAAB_WORK_DIR (local), HISAAB_WORK_BUCKET (s3), HISAAB_WORK_CONTAINER (azure)
Keys: HISAAB_PRIVATE_KEYS_B64 or HISAAB_PRIVATE_KEY_FILES. Hugging Face: HF_TOKEN and HISAAB_HF_DATASET.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

from ..storage import AzureStorage, LocalStorage, S3Storage
from .common import read_bytes


def stores():
    e = os.environ.get
    kind = e("HISAAB_STORAGE", "local")
    if kind == "s3":
        return S3Storage(e("HISAAB_S3_BUCKET", ""), e("AWS_REGION")), S3Storage(e("HISAAB_WORK_BUCKET", ""), e("AWS_REGION"))
    if kind == "azure":
        cs, url = e("HISAAB_AZURE_CONNECTION_STRING"), e("HISAAB_AZURE_ACCOUNT_URL")
        return AzureStorage(e("HISAAB_AZURE_CONTAINER", ""), cs, url), AzureStorage(e("HISAAB_WORK_CONTAINER", ""), cs, url)
    return LocalStorage(e("HISAAB_LOCAL_DIR", "./data"), "", None), LocalStorage(e("HISAAB_WORK_DIR", "./work"), "", None)


def main(argv=None) -> int:
    p = argparse.ArgumentParser(prog="python -m hisaab_improve.pipeline", description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("command", choices=["nightly", "ingest", "export", "fetch", "review-sheet", "review-apply", "clear", "publish-hf"])
    p.add_argument("arg", nargs="?")
    p.add_argument("--agent", action="store_true")
    p.add_argument("--reviewer", default="")
    p.add_argument("--proposals", default="")
    a = p.parse_args(argv)
    raw, work = stores()
    if a.command in ("nightly", "ingest"):
        from ..crypto import keys_from_env

        keys = keys_from_env()
        if not keys:
            print("no private key configured (HISAAB_PRIVATE_KEYS_B64): sealed batches cannot be opened", file=sys.stderr)
            return 2
        if a.command == "ingest":
            from .ingest import ingest

            print(json.dumps(ingest(raw, work, keys), indent=1))
        else:
            from .nightly import from_env, run, summary_text

            print(summary_text(run(raw, work, keys, **from_env())))
    elif a.command == "export":
        from .export import export

        print(json.dumps(export(work)["counts"], indent=1))
    elif a.command == "fetch":
        sid = (read_bytes(work, "snapshots/latest") or b"").decode()
        base = f"snapshots/{sid}/" + ("agent/" if a.agent else "")
        out = Path(a.arg or "snapshot")
        for key in work.keys(base):
            target = out / key[len(base):]
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(read_bytes(work, key))
        print(f"snapshot {sid} -> {out}")
    elif a.command == "review-sheet":
        from .review import sheet

        proposals = {}
        if a.proposals:
            for line in Path(a.proposals).read_text(encoding="utf-8").splitlines():
                if line.strip():
                    r = json.loads(line)
                    proposals[str(r.get("id", ""))] = str(r.get("label", ""))
        Path(a.arg).write_text(sheet(work, proposals), encoding="utf-8-sig")  # with BOM, so Excel shows Indian scripts
    elif a.command == "review-apply":
        from .review import apply

        print(apply(work, Path(a.arg).read_text(encoding="utf-8-sig"), a.reviewer))
    elif a.command == "clear":
        from .review import clear

        clear(work, a.arg, a.reviewer)
    elif a.command == "publish-hf":
        from .hfsync import sync

        print(sync(work, os.environ.get("HISAAB_HF_DATASET", "AcxelinLabs/hisaabsaathi-voices")))
    return 0


if __name__ == "__main__":
    sys.exit(main())
