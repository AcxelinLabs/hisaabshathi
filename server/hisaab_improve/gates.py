"""Automatic gates: a change to what the app understands or hears (new rules, a fine-tuned
model, a new speech model) ships only if no language gets worse.

    python -m hisaab_improve.gates <baseline.json> <candidate.json> [--tolerance 0.005]

Each file is {metric: {language: value}}, for example what the app's RealCasesTest writes
(android/core/build/real-cases-metrics.json) for the shipped version and for the change, on
the same snapshot. Rules:
- every metric and language in the baseline must be in the candidate;
- metrics named *_wer or *_cer are better lower; all others better higher;
- no language may be worse than the baseline by more than the tolerance;
- metrics named gate_* must be exactly 1.0 (cases a worker confirmed must all stay right).
Exit code 0 passes; 1 fails, with the reasons printed.
"""
from __future__ import annotations

import argparse
import json
import sys


def lower_is_better(name: str) -> bool:
    return name.endswith("_wer") or name.endswith("_cer")


def compare(baseline: dict, candidate: dict, tolerance: float = 0.0) -> list[str]:
    problems = []
    for name, langs in baseline.items():
        got = candidate.get(name)
        if got is None:
            problems.append(f"{name}: missing from the candidate")
            continue
        for lang, before in langs.items():
            if lang not in got:
                problems.append(f"{name} {lang}: missing from the candidate")
                continue
            after = got[lang]
            worse = (after - before) if lower_is_better(name) else (before - after)
            if worse > tolerance:
                problems.append(f"{name} {lang}: worse, {before:.4f} -> {after:.4f}")
    for name, langs in candidate.items():
        if name.startswith("gate_"):
            for lang, v in langs.items():
                if v < 1.0:
                    problems.append(f"{name} {lang}: {v:.4f}, must be 1.0 (confirmed cases broken)")
    return problems


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("baseline")
    p.add_argument("candidate")
    p.add_argument("--tolerance", type=float, default=0.0)
    a = p.parse_args(argv)
    with open(a.baseline, encoding="utf-8") as f:
        base = json.load(f)
    with open(a.candidate, encoding="utf-8") as f:
        cand = json.load(f)
    problems = compare(base, cand, a.tolerance)
    for name in sorted(cand):
        for lang in sorted(cand[name]):
            b = base.get(name, {}).get(lang)
            print(f"{name:28} {lang}  {'-' if b is None else f'{b:.4f}'} -> {cand[name][lang]:.4f}")
    print("\nPASS" if not problems else "\nFAIL\n" + "\n".join(problems))
    return 0 if not problems else 1


if __name__ == "__main__":
    sys.exit(main())
