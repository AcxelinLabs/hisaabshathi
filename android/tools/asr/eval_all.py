"""usage: eval_all.py <label> <model-spec> -> appends one line per test set to results.jsonl"""
import json, sys, glob
sys.path.insert(0, __file__.rsplit("/", 1)[0])
from evalset import score
label, spec = sys.argv[1], sys.argv[2]
for t in sorted(glob.glob("splits/test_*.jsonl")):
    res, out = score(t, spec)
    line = {"label": label, "set": t.split("test_")[1][:-6], **res}
    print(json.dumps(line, ensure_ascii=False), flush=True)
    open("results.jsonl", "a").write(json.dumps(line, ensure_ascii=False) + "\n")
    json.dump(out, open(f"out_{label}_{line['set']}.json", "w"), ensure_ascii=False)
