"""How does each Indian-language model write English number words? usage: english_in_indic.py <models> <manifest> <out>"""
import json, sys, soundfile as sf, sherpa_onnx
M, man, out = sys.argv[1:4]
rows = [json.loads(l) for l in open(man)]
res = [{"ref": r["text"]} for r in rows]
for lang in ["hi", "or", "te", "kn", "ta"]:
    rec = sherpa_onnx.OfflineRecognizer.from_nemo_ctc(model=f"{M}/{lang}/model.int8.onnx", tokens=f"{M}/tokens.txt", num_threads=3)
    for r, o in zip(rows, res):
        a, sr = sf.read(r["audio"], dtype="float32"); s = rec.create_stream(); s.accept_waveform(sr, a); rec.decode_stream(s); o[lang] = s.result.text
    del rec; print(lang, "done", flush=True)
json.dump(res, open(out, "w"), ensure_ascii=False, indent=0)
