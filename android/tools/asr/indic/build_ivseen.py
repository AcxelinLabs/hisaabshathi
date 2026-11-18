"""Contrast set: 0.5 h of rural IndicVoices 'valid' clips from speakers who ARE in IndicVoices
train (the app's models have heard them). Shows how much a seen speaker flatters the score."""
import pyarrow.parquet as pq, glob, json, io, os, random, soundfile as sf
train = json.load(open("iv_train_speakers.json"))
for l in ["odia", "telugu", "kannada", "tamil"]:
    tr = {s for f, ss in train.items() if f.startswith(l + "/") for s in ss}
    rows = [r for f in sorted(glob.glob(f"raw/IndicVoices/{l}/*.parquet")) for r in pq.read_table(f).to_pylist()]
    ok = [r for r in rows if r["speaker_id"] in tr and r["area"] == "Rural" and "<unintelligible>" not in r["text"] and 1 <= r["duration"] <= 20]
    random.Random(1).shuffle(ok); os.makedirs(f"sets/ivseen_{l}", exist_ok=True); h = 0
    with open(f"sets/ivseen_{l}.jsonl", "w") as w:
        for i, r in enumerate(ok):
            if h >= 0.5: break
            p = os.path.abspath(f"sets/ivseen_{l}/{i:05d}.wav"); a, _ = sf.read(io.BytesIO(r["audio_filepath"]["bytes"]), dtype="float32")
            sf.write(p, a, 16000, subtype="PCM_16"); h += len(a) / 16000 / 3600
            w.write(json.dumps({"id": f"ivseen_{l}_{i:05d}", "wav": p, "text": r["text"], "speaker": r["speaker_id"], "district": r["district"], "area": r["area"]}, ensure_ascii=False) + "\n")
    print(l, i, round(h, 2))
