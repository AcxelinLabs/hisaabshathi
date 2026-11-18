"""Decode a test set with the app's model, exactly as Speech.kt does: NeMo CTC, greedy, whole clip.
usage: decode.py <testset.jsonl> <model.onnx> <tokens.txt> <out.jsonl>
testset rows: {"id","wav","text",...}; writes rows + "hyp" + "secs"."""
import json, sys, time, soundfile as sf, sherpa_onnx
ts, model, tokens, out = sys.argv[1:5]
rec = sherpa_onnx.OfflineRecognizer.from_nemo_ctc(model=model, tokens=tokens, num_threads=2, decoding_method="greedy_search")
t0 = time.time(); audio = 0
with open(out, "w", encoding="utf-8") as w:
    for line in open(ts, encoding="utf-8"):
        r = json.loads(line); a, sr = sf.read(r["wav"], dtype="float32"); assert sr == 16000
        s = rec.create_stream(); s.accept_waveform(16000, a); rec.decode_stream(s)
        r["hyp"] = s.result.text.strip(); audio += len(a) / 16000
        w.write(json.dumps(r, ensure_ascii=False) + "\n")
print(json.dumps({"engine": sherpa_onnx.__version__, "set": ts, "audio_h": round(audio / 3600, 2), "rtf": round((time.time() - t0) / audio, 3)}))
