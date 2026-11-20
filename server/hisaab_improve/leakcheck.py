"""Run on an agent's change before it leaves the private environment (before any push or pull
request). It fails if the change

- contains a worker's words: any shared run of 5 words, or any whole sentence of 12+
  characters, from the snapshot's cases or sentences to label (after lower-casing and
  dropping punctuation). Tests must use paraphrases;
- touches what the app collects, how it asks, how data is sent or kept, or this pipeline.
  Those changes need a person.

    git diff origin/main... > change.diff
    python -m hisaab_improve.leakcheck change.diff <snapshot folder> [...]

Findings name the file and the sample id, never the words themselves (this output may be
shared). Exit code 1 if anything was found.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

from .pipeline.common import norm

PROTECTED = (
    "android/core/src/main/kotlin/com/acxelin/hisaabsaathi/core/Improve.kt",
    "android/core/src/main/kotlin/com/acxelin/hisaabsaathi/core/Seal.kt",
    "android/core/src/main/kotlin/com/acxelin/hisaabsaathi/core/ShareClient.kt",
    "android/core/src/main/kotlin/com/acxelin/hisaabsaathi/core/Strings.kt",  # holds the consent text
    "android/core/src/main/kotlin/com/acxelin/hisaabsaathi/core/i18n/",
    "android/app/src/main/kotlin/com/acxelin/hisaabsaathi/work/Sharing.kt",
    "android/app/src/main/kotlin/com/acxelin/hisaabsaathi/data/",
    "android/app/src/main/kotlin/com/acxelin/hisaabsaathi/ui/SetupScreens.kt",
    "android/app/src/main/AndroidManifest.xml",
    "android/app/build.gradle.kts",
    "server/",
    "docs/improve-agent.md",
    "docs/improve-loop.md",
    ".github/",
)
SHINGLE = 5
MIN_CHARS = 12


def parse_diff(diff: str) -> tuple[set[str], dict[str, str]]:
    """Files touched (both sides of renames) and the added text per file."""
    touched, added, current = set(), {}, None
    for line in diff.splitlines():
        if line.startswith("diff --git "):
            m = re.match(r"diff --git a/(.+?) b/(.+)$", line)
            if m:
                touched.update(m.groups())
                current = m.group(2)
        elif line.startswith("+++ ") or line.startswith("--- "):
            continue
        elif line.startswith("+") and current:
            added[current] = added.get(current, "") + line[1:] + "\n"
    return touched, added


def worker_texts(folders: list[Path]) -> list[tuple[str, str]]:
    out = []
    for folder in folders:
        for p in folder.rglob("*.jsonl"):
            for line in p.read_text(encoding="utf-8").splitlines():
                if line.strip():
                    r = json.loads(line)
                    if r.get("text"):
                        out.append((r.get("id", "?"), norm(r["text"])))
    return out


def check(diff: str, texts: list[tuple[str, str]]) -> list[str]:
    touched, added = parse_diff(diff)
    findings = [f"protected file changed: {f}" for f in sorted(touched) if any(f == p or f.startswith(p) for p in PROTECTED)]
    shingles = {}
    for sid, t in texts:
        words = t.split()
        for i in range(len(words) - SHINGLE + 1):
            shingles.setdefault(" ".join(words[i:i + SHINGLE]), sid)
    for f, text in added.items():
        n = norm(text)
        nw = n.split()
        hit = next((sid for sid, t in texts if len(t) >= MIN_CHARS and t in n), None)
        if hit is None:
            hit = next((shingles[" ".join(nw[i:i + SHINGLE])] for i in range(len(nw) - SHINGLE + 1)
                        if " ".join(nw[i:i + SHINGLE]) in shingles), None)
        if hit is not None:
            findings.append(f"worker words in {f} (sample {hit})")
    return findings


def main(argv=None) -> int:
    argv = argv if argv is not None else sys.argv[1:]
    if len(argv) < 2:
        print(__doc__)
        return 2
    diff = sys.stdin.read() if argv[0] == "-" else Path(argv[0]).read_text(encoding="utf-8", errors="replace")
    findings = check(diff, worker_texts([Path(a) for a in argv[1:]]))
    print("\n".join(findings) if findings else "leak check: nothing found")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
