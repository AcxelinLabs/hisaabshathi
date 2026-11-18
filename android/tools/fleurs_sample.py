#!/usr/bin/env python3
"""Takes n random clips from a FLEURS test parquet file: writes 16 kHz WAVs and a jsonl index.
usage: fleurs_sample.py <file.parquet> <lang> <out_dir> <n>"""
import io, json, os, random, sys
import pyarrow.parquet as pq, soundfile as sf, librosa, numpy as np
path, lang, out, n = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
os.makedirs(out, exist_ok=True)
pf = pq.ParquetFile(path)
rows = []
for g in range(pf.num_row_groups):
    t = pf.read_row_group(g, columns=["id", "transcription", "audio"]).to_pylist()
    rows += t
random.seed(42); pick = random.sample(rows, min(n, len(rows)))
with open(f"{out}/{lang}.jsonl", "w", encoding="utf-8") as o:
    for i, r in enumerate(pick):
        a, sr = sf.read(io.BytesIO(r["audio"]["bytes"]), dtype="float32")
        if a.ndim > 1: a = a.mean(axis=1)
        if sr != 16000: a = librosa.resample(a, orig_sr=sr, target_sr=16000)
        f = f"{lang}_{i:03d}.wav"; sf.write(f"{out}/{f}", a, 16000)
        o.write(json.dumps({"file": f, "text": r["transcription"]}, ensure_ascii=False) + "\n")
print(lang, len(pick), "clips")
