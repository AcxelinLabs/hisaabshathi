"""IndicVoices test sets: 'valid' split, speakers absent from IndicVoices train, rural first.
Drops clips with <unintelligible>, under 1 s or over 20 s (the app records at most 20 s)."""
import pyarrow.parquet as pq, glob, json, io, os, random, collections, soundfile as sf
train = json.load(open("iv_train_speakers.json"))
TARGET_H, CAP = 3.0, 25            # hours per language; clips per speaker
summary = {}
for l in ["odia", "telugu", "kannada", "tamil"]:
    tr = {s for f, ss in train.items() if f.startswith(l + "/") for s in ss}
    rows = [r for f in sorted(glob.glob(f"raw/IndicVoices/{l}/*.parquet")) for r in pq.read_table(f).to_pylist()]
    overlap = {r["speaker_id"] for r in rows} & tr
    ok = [r for r in rows if r["speaker_id"] not in tr and "<unintelligible>" not in r["text"] and 1 <= r["duration"] <= 20 and r["text"].strip()]
    by = collections.defaultdict(list)
    for r in ok: by[r["speaker_id"]].append(r)
    spk = sorted(by); random.Random(0).shuffle(spk)
    spk.sort(key=lambda s: by[s][0]["area"] != "Rural")       # rural speakers first
    os.makedirs(f"sets/iv_{l}", exist_ok=True); out = []; h = 0
    for s in spk:
        if h >= TARGET_H: break
        for r in random.Random(s).sample(by[s], min(CAP, len(by[s]))):
            p = f"sets/iv_{l}/{len(out):05d}.wav"
            a, sr = sf.read(io.BytesIO(r["audio_filepath"]["bytes"]), dtype="float32"); assert sr == 16000
            sf.write(p, a, 16000, subtype="PCM_16"); h += len(a) / 16000 / 3600
            out.append({"id": f"iv_{l}_{len(out):05d}", "wav": os.path.abspath(p), "text": r["text"], "speaker": r["speaker_id"],
                        "area": r["area"], "district": r["district"], "gender": r["gender"], "scenario": r["scenario"], "occupation": r["occupation"]})
    json.dump(out, open(f"sets/iv_{l}.json", "w"), ensure_ascii=False)
    with open(f"sets/iv_{l}.jsonl", "w") as w:
        for r in out: w.write(json.dumps(r, ensure_ascii=False) + "\n")
    summary[l] = {"valid_speakers": len({r["speaker_id"] for r in rows}), "valid_speakers_in_train": len(overlap),
                  "clips": len(out), "hours": round(h, 2), "speakers": len({r["speaker"] for r in out}),
                  "rural_share": round(sum(r["area"] == "Rural" for r in out) / len(out), 3),
                  "districts": len({r["district"] for r in out})}
    print(l, summary[l], flush=True)
json.dump(summary, open("sets/iv_summary.json", "w"), indent=1)
