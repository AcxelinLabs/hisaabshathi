"""Compares English speech models (sherpa-onnx) on Indian-accented English test sets.

usage: en_eval.py <name> <kind> <dir> [threads]
  kind: ctc (model*.onnx + tokens.txt) | tdt (encoder/decoder/joiner) | moonshine
"""
import glob, json, os, re, sys, time
import soundfile as sf, sherpa_onnx
from num2words import num2words
sys.path.insert(0, os.path.dirname(__file__))
from evalset import edits

name, kind, d = sys.argv[1:4]
th = int(sys.argv[4]) if len(sys.argv) > 4 else 2
f = lambda pat: sorted(glob.glob(f"{d}/{pat}"))[0]
if kind == "ctc":
    rec = sherpa_onnx.OfflineRecognizer.from_nemo_ctc(model=f("model*.onnx"), tokens=f"{d}/tokens.txt", num_threads=th)
elif kind == "tdt":
    rec = sherpa_onnx.OfflineRecognizer.from_transducer(encoder=f("encoder*.onnx"), decoder=f("decoder*.onnx"), joiner=f("joiner*.onnx"),
                                                        tokens=f"{d}/tokens.txt", num_threads=th, model_type="nemo_transducer")
else:
    rec = sherpa_onnx.OfflineRecognizer.from_moonshine(preprocessor=f"{d}/preprocess.onnx", encoder=f("encode*.onnx"),
                                                       uncached_decoder=f("uncached*.onnx"), cached_decoder=f("cached*.onnx"),
                                                       tokens=f"{d}/tokens.txt", num_threads=th)

def norm(s):
    s = s.lower().replace("%", " percent ")
    s = re.sub(r"\d+", lambda m: " " + num2words(int(m.group())) + " ", s)
    s = re.sub(r"[^a-z' ]+", " ", s.replace("-", " "))
    return re.sub(r"\s+", " ", s.replace("'", "")).strip()

size = sum(os.path.getsize(p) for p in glob.glob(f"{d}/*.onnx")) / 1e6
for t in ["svarah", "karya", "fleurs"]:
    rows = [json.loads(l) for l in open(f"splits/en_{t}.jsonl", encoding="utf-8")]
    we = wn = 0; audio = 0.0; t0 = time.time(); ex = []
    for r in rows:
        a, sr = sf.read(r["audio"], dtype="float32"); audio += len(a) / sr
        s = rec.create_stream(); s.accept_waveform(sr, a); rec.decode_stream(s)
        hyp, ref = norm(s.result.text), norm(r["text"])
        we += edits(hyp.split(), ref.split()); wn += len(ref.split())
        if len(ex) < 2: ex.append((ref, hyp))
    line = {"model": name, "set": t, "n": len(rows), "wer": round(we / wn, 4), "rtf": round((time.time() - t0) / audio, 3), "mb": round(size)}
    print(json.dumps(line), flush=True); open("en_results.jsonl", "a").write(json.dumps(line) + "\n")
