#!/usr/bin/env python3
"""Benchmarks on-device speech-recognition models on real human speech (Google FLEURS test
split): word error rate, character error rate and speed, for each language.

usage: bench_stt.py <lang> <fleurs_dir> <n> <name=model.onnx:tokens.txt> [...]
Writes one JSON line per model to stdout."""
import io, json, os, random, re, sys, time, unicodedata
import numpy as np, soundfile as sf, librosa, sherpa_onnx

lang, fdir, n = sys.argv[1], sys.argv[2], int(sys.argv[3])
models = [a.split("=", 1) for a in sys.argv[4:]]
clips = [json.loads(l) for l in open(f"{fdir}/{lang}.jsonl", encoding="utf-8")][:n]

def norm(s):
    s = unicodedata.normalize("NFC", s.lower())
    s = "".join(" " if unicodedata.category(c)[0] in "PS" else c for c in s)
    return re.sub(r"\s+", " ", s).strip()

def edits(a, b):
    prev = list(range(len(b) + 1))
    for i in range(1, len(a) + 1):
        cur = [i] + [0] * len(b)
        for j in range(1, len(b) + 1):
            cur[j] = min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] != b[j - 1]))
        prev = cur
    return prev[-1]

for name, spec in models:
    model, tokens = spec.split(":")
    t0 = time.time()
    rec = sherpa_onnx.OfflineRecognizer.from_nemo_ctc(model=model, tokens=tokens, num_threads=4, decoding_method="greedy_search")
    load = time.time() - t0
    we = wn = ce = cn = 0; audio_s = dec_s = 0.0; samples = []
    for c in clips:
        a, sr = sf.read(f"{fdir}/{c['file']}", dtype="float32")
        if sr != 16000: a = librosa.resample(a, orig_sr=sr, target_sr=16000)
        s = rec.create_stream(); s.accept_waveform(16000, a)
        t = time.time(); rec.decode_stream(s); dec_s += time.time() - t; audio_s += len(a) / 16000
        hyp, ref = norm(s.result.text), norm(c["text"])
        we += edits(hyp.split(), ref.split()); wn += len(ref.split())
        ce += edits(hyp.replace(" ", ""), ref.replace(" ", "")); cn += len(ref.replace(" ", ""))
        if len(samples) < 2: samples.append({"ref": ref, "hyp": hyp})
    print(json.dumps({"lang": lang, "model": name, "clips": len(clips), "wer": round(we / wn, 4), "cer": round(ce / cn, 4),
                      "rtf": round(dec_s / audio_s, 4), "load_s": round(load, 1), "size_mb": round(os.path.getsize(model) / 1e6),
                      "samples": samples}, ensure_ascii=False), flush=True)
    del rec
