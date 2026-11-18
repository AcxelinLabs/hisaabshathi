"""Vaani test sets: transcribed clips with a known speaker ID, rural districts first,
markup removed. Fetches audio only for the parquet row groups that hold chosen clips."""
import json, re, io, os, random, collections, time, soundfile as sf, numpy as np, pyarrow.parquet as pq
from huggingface_hub import HfFileSystem
from concurrent.futures import ThreadPoolExecutor
URBAN = {"Khordha", "Hyderabad", "Bangalore", "Chennai", "Vishakapattanam"}
TARGET_H, CAP = 2.5, 20
fs = HfFileSystem()
def clean(t):
    t = re.sub(r"\{[^}]*\}|<[^>]*>|\[[^\]]*\]", " ", t)
    return re.sub(r"\s+", " ", t.replace("_", " ")).strip()
meta = json.load(open("vaani_meta.json")); summary = {}
for L, rows in meta.items():
    ok = [r for r in rows if r["speakerID"] != "NA" and "unintelligible" not in r["transcript"] and 1 <= r["duration"] <= 20 and len(clean(r["transcript"]).split()) >= 2]
    by = collections.defaultdict(list)
    for r in ok: by[r["speakerID"]].append(r)
    spk = sorted(by); random.Random(0).shuffle(spk); spk.sort(key=lambda s: by[s][0]["district"] in URBAN)
    chosen = []; h = 0
    for s in spk:
        if h >= TARGET_H: break
        for r in random.Random(s).sample(by[s], min(CAP, len(by[s]))): chosen.append(r); h += r["duration"] / 3600
    groups = collections.defaultdict(list)
    for r in chosen: groups[(r["file"], r["rg"])].append(r)
    def fetch(k):
        for a in range(6):
            try:
                col = pq.ParquetFile(fs.open("datasets/ARTPARK-IISc/Vaani/" + k[0], block_size=1 << 22)).read_row_group(k[1], columns=["audio"]).column(0).to_pylist()
                return k, col
            except Exception as e: err = e; time.sleep(30 * (a + 1))
        raise err
    os.makedirs(f"sets/va_{L.lower()}", exist_ok=True); out = []
    with ThreadPoolExecutor(4) as ex:
        for k, col in ex.map(fetch, list(groups)):
            for r in groups[k]:
                a, sr = sf.read(io.BytesIO(col[r["row"]]["bytes"]), dtype="float32")
                if a.ndim > 1: a = a.mean(1)
                if sr != 16000:
                    n = int(len(a) * 16000 / sr); a = np.interp(np.linspace(0, len(a) - 1, n), np.arange(len(a)), a).astype(np.float32)
                p = f"sets/va_{L.lower()}/{len(out):05d}.wav"; sf.write(p, a, 16000, subtype="PCM_16")
                out.append({"id": f"va_{L.lower()}_{len(out):05d}", "wav": os.path.abspath(p), "text": clean(r["transcript"]), "raw_text": r["transcript"],
                            "speaker": r["speakerID"], "district": r["district"], "state": r["state"], "pincode": r["pincode"], "gender": r["gender"], "src_sr": sr})
    with open(f"sets/va_{L.lower()}.jsonl", "w") as w:
        for r in out: w.write(json.dumps(r, ensure_ascii=False) + "\n")
    summary[L] = {"clips": len(out), "hours": round(sum(r["duration"] for r in chosen) / 3600, 2), "speakers": len({r["speaker"] for r in out}),
                  "rural_share": round(sum(r["district"] not in URBAN for r in out) / len(out), 3),
                  "districts": dict(collections.Counter(r["district"] for r in out)), "row_groups": len(groups), "sr": dict(collections.Counter(r["src_sr"] for r in out))}
    print(L, summary[L], flush=True)
json.dump(summary, open("sets/va_summary.json", "w"), indent=1)
