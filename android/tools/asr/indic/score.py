"""Scores decoded sets: WER/CER (tools/asr/evalset.py normalisation), plus the app's own
number reading and understanding (PipelineEvalTest output joined by id).
usage: score.py <pipeline.out.jsonl> <set.hyp.jsonl>... ; prints one JSON line per set (and per group)."""
import json, sys, collections
import os; sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from evalset import norm, edits
pipe = {r["id"]: r for r in map(json.loads, open(sys.argv[1], encoding="utf-8"))}
def money(u):  # amounts in an Understand result like "ADVANCE 200; WORK 500 h8"
    return sorted(p.split()[1] for p in u.split("; ")) if u and u[0].isupper() and not u.startswith(("ASK", "TALK", "UNKNOWN")) else []
def score(rows):
    c = collections.Counter()
    for r in rows:
        ref, hyp = norm(r["text"]), norm(r["hyp"]); p = pipe[r["id"]]
        c["we"] += edits(hyp.split(), ref.split()); c["wn"] += len(ref.split())
        c["ce"] += edits(hyp.replace(" ", ""), ref.replace(" ", "")); c["cn"] += len(ref.replace(" ", ""))
        rw, hw = collections.Counter(p["ref"]["num_words"]), collections.Counter(p["hyp"]["num_words"])
        c["nw"] += sum(rw.values()); c["nw_hit"] += sum((rw & hw).values()); c["nw_extra"] += sum((hw - rw).values())
        rv, hv = collections.Counter(p["ref"]["values"]), collections.Counter(p["hyp"]["values"])
        c["nv"] += sum(rv.values()); c["nv_hit"] += sum((rv & hv).values())
        if rv: c["clips_num"] += 1; c["clips_num_exact"] += rv == hv
        elif hv: c["clips_spurious_num"] += 1
        rm, hm = money(p["ref"]["understood"]), money(p["hyp"]["understood"])
        if rm:
            c["entry_clips"] += 1; c["entry_amount_ok"] += rm == hm; c["entry_full_ok"] += p["ref"]["understood"] == p["hyp"]["understood"]
        elif hm: c["entry_spurious"] += 1
    n = len(rows); f = lambda a, b: round(c[a] / c[b], 4) if c[b] else None
    return {"n": n, "speakers": len({r.get("speaker") for r in rows}), "hours": None, "wer": f("we", "wn"), "cer": f("ce", "cn"),
            "num_word_recall": f("nw_hit", "nw"), "num_words": c["nw"], "num_words_extra": c["nw_extra"],
            "value_recall": f("nv_hit", "nv"), "values": c["nv"],
            "clips_with_numbers": c["clips_num"], "clips_all_values_right": f("clips_num_exact", "clips_num"),
            "clips_no_number_but_hyp_has": c["clips_spurious_num"],
            "entry_clips": c["entry_clips"], "entry_amount_right": f("entry_amount_ok", "entry_clips"), "entry_exact": f("entry_full_ok", "entry_clips"),
            "entry_spurious": c["entry_spurious"]}
URBAN = {"Khordha", "Hyderabad", "Bangalore", "Chennai", "Vishakapattanam"}
for path in sys.argv[2:]:
    rows = [json.loads(l) for l in open(path, encoding="utf-8")]
    name = path.split("/")[-1].replace(".hyp.jsonl", "")
    print(json.dumps({"set": name, **score(rows)}, ensure_ascii=False))
    if name.startswith("va_"):
        for g, sub in [("rural", [r for r in rows if r["district"] not in URBAN]), ("city", [r for r in rows if r["district"] in URBAN])]:
            if sub: print(json.dumps({"set": f"{name}/{g}", **score(sub)}, ensure_ascii=False))

# Value recall leaving out 1 ("one" is mostly the article "a" here, and its colloquial
# spellings, e.g. Odia ଗୋଟେ and Tamil ஒன்னு, are missing from the app's number tables).
print("# value recall excluding 1")
for path in sys.argv[2:]:
    rows = [json.loads(l) for l in open(path, encoding="utf-8")]; hit = tot = 0
    for r in rows:
        p = pipe[r["id"]]; rv = collections.Counter(v for v in p["ref"]["values"] if v != 1.0); hv = collections.Counter(v for v in p["hyp"]["values"] if v != 1.0)
        tot += sum(rv.values()); hit += sum((rv & hv).values())
    print(json.dumps({"set": path.split("/")[-1].replace(".hyp.jsonl", ""), "values_not_1": tot, "value_recall_not_1": round(hit / tot, 4) if tot else None}))
