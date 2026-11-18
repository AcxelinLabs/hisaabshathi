"""Does digital silence before speech make the model drop the first word? usage: onset.py <models> <manifest>..."""
import json, sys, numpy as np, soundfile as sf, sherpa_onnx
M = sys.argv[1]; rng = np.random.default_rng(0); recs = {}
def rec(lang):
    if lang not in recs:
        recs[lang] = sherpa_onnx.OfflineRecognizer.from_nemo_ctc(model=f"{M}/{lang}/model.int8.onnx", tokens=f"{M}/tokens.txt", num_threads=2)
    return recs[lang]
def run(r, a):
    s = r.create_stream(); s.accept_waveform(16000, a.astype(np.float32)); r.decode_stream(s); return s.result.text.split()
stats = {}
for man in sys.argv[2:]:
    for l in open(man, encoding="utf-8"):
        row = json.loads(l); lang = row["audio"].split("/")[-1][:2]; r = rec(lang)
        a, sr = sf.read(row["audio"], dtype="float32"); assert sr == 16000
        z = np.zeros(8000, np.float32); tail = np.zeros(32000, np.float32)
        variants = {"as recorded": a, "0.5s digital silence": np.concatenate([z, a, tail])}
        variants["same + dither 1e-5"] = variants["0.5s digital silence"] + rng.normal(0, 1e-5, len(variants["0.5s digital silence"])).astype(np.float32)
        variants["same + dither 1e-4"] = variants["0.5s digital silence"] + rng.normal(0, 1e-4, len(variants["0.5s digital silence"])).astype(np.float32)
        base = run(r, a)
        for k, v in variants.items():
            h = run(r, v); st = stats.setdefault(k, [0, 0, 0])
            st[0] += 1; st[1] += bool(h and base and h[0] == base[0]); st[2] += len(h)
print(json.dumps(stats, ensure_ascii=False, indent=0))
