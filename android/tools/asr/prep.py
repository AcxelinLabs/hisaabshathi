"""Converts a parquet speech dataset into 16 kHz mono WAVs plus a JSONL manifest.

usage: prep.py <parquet> <out_dir> <source> <audio_col> <text_col> [speaker_col|-] [max_s]
"""
import io, json, os, re, sys, unicodedata
import numpy as np, soundfile as sf, torch, torchaudio.functional as AF, pyarrow.parquet as pq

src, out, name, acol, tcol = sys.argv[1:6]
scol = sys.argv[6] if len(sys.argv) > 6 and sys.argv[6] != '-' else None
max_s = float(sys.argv[7]) if len(sys.argv) > 7 else 20.0
os.makedirs(out, exist_ok=True)

def clean(t):
    t = re.sub(r"<[^>]*>", " ", t)          # noise / filler tags
    t = re.sub(r"\[[^\]]*\]", " ", t)       # English glosses
    t = unicodedata.normalize("NFC", t)
    t = "".join(" " if unicodedata.category(c)[0] in "PS" else c for c in t)
    return re.sub(r"\s+", " ", t).strip()

f = pq.ParquetFile(src)
cols = [acol, tcol] + ([scol] if scol else [])
kept = skipped = 0
max_n = int(os.environ.get("MAX_N", "0"))
with open(f"{out}/manifest.jsonl", "a", encoding="utf-8") as man:
    for b in f.iter_batches(batch_size=32, columns=cols):
        d = b.to_pydict()
        if max_n and kept >= max_n: break
        for i in range(len(d[tcol])):
            text = clean(d[tcol][i] or "")
            a = d[acol][i]
            if not text or not a or not a.get("bytes"):
                skipped += 1; continue
            try:
                wav, sr = sf.read(io.BytesIO(a["bytes"]), dtype="float32", always_2d=True)
            except Exception:
                skipped += 1; continue
            wav = wav.mean(1)
            if sr != 16000:
                wav = AF.resample(torch.from_numpy(wav), sr, 16000).numpy()
            dur = len(wav) / 16000
            if dur < 0.5 or dur > max_s:
                skipped += 1; continue
            fn = f"{out}/{name}-{kept:06d}.wav"
            sf.write(fn, wav, 16000, subtype="PCM_16")
            man.write(json.dumps({"audio": fn, "text": text, "dur": round(dur, 2), "source": name,
                                  "speaker": str(d[scol][i]) if scol else ""}, ensure_ascii=False) + "\n")
            kept += 1
print(name, "kept", kept, "skipped", skipped, flush=True)
