"""Scores a NeMo-CTC ONNX model through sherpa-onnx (whatever version this interpreter has)."""
import json, re, sys, unicodedata, collections, numpy as np, soundfile as sf, sherpa_onnx
model, tokens, manifest = sys.argv[1:4]
n = int(sys.argv[4]) if len(sys.argv) > 4 else None
exec(open(__file__.rsplit("/", 1)[0] + "/evalset.py").read().split("def recognizer")[0].split("import numpy as np, soundfile as sf")[1])
rec = sherpa_onnx.OfflineRecognizer.from_nemo_ctc(model=model, tokens=tokens, num_threads=4)
rows = [json.loads(l) for l in open(manifest, encoding="utf-8")][:n]
we = wn = nh = nt = 0
for r in rows:
    a = sf.read(r["audio"], dtype="float32")[0]
    s = rec.create_stream(); s.accept_waveform(16000, a); rec.decode_stream(s)
    hyp, ref = norm(s.result.text), norm(r["text"])
    we += edits(hyp.split(), ref.split()); wn += len(ref.split())
    rn = collections.Counter(w for w in ref.split() if w in NUMN); hn = collections.Counter(w for w in hyp.split() if w in NUMN)
    nt += sum(rn.values()); nh += sum((rn & hn).values())
print(json.dumps({"engine": sherpa_onnx.__version__, "set": manifest, "n": len(rows), "wer": round(we / wn, 4), "num_recall": round(nh / nt, 4) if nt else None}))
