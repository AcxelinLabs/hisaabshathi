"""Scores a speech model on a manifest: WER, CER and number-word recall.

usage: evalset.py <manifest> <model> [n]
  model = onnx:<model.onnx>:<tokens.txt>   (the app's sherpa-onnx model)
        | torch:<ckpt_dir>[:<finetuned.pt>]
"""
import json, re, sys, time, unicodedata, collections
import numpy as np, soundfile as sf

NUM = set("""शून्य एक दो तीन चार पांच पाँच छह छः छे सात आठ नौ दस ग्यारह बारह तेरह चौदह पंद्रह पन्द्रह सोलह सत्रह अठारह उन्नीस बीस
इक्कीस बाईस तेईस चौबीस पच्चीस छब्बीस सत्ताईस अट्ठाईस उनतीस तीस इकतीस बत्तीस तैंतीस चौंतीस पैंतीस छत्तीस सैंतीस अड़तीस उनतालीस चालीस
इकतालीस बयालीस तैंतालीस चवालीस पैंतालीस छियालीस सैंतालीस अड़तालीस उनचास पचास इक्यावन बावन तिरपन चौवन पचपन छप्पन सत्तावन अट्ठावन उनसठ साठ
इकसठ बासठ तिरसठ चौंसठ पैंसठ छियासठ सड़सठ अड़सठ उनहत्तर सत्तर इकहत्तर बहत्तर तिहत्तर चौहत्तर पचहत्तर छिहत्तर सतहत्तर अठहत्तर उन्यासी अस्सी
इक्यासी बयासी तिरासी चौरासी पचासी छियासी सत्तासी अट्ठासी नवासी नब्बे इक्यानबे बानबे तिरानबे चौरानबे पंचानबे छियानबे सत्तानबे अट्ठानबे निन्यानबे
सौ हज़ार हजार लाख करोड़ डेढ़ ढाई सवा साढ़े आधा रुपये रुपया रुपए""".split())

def norm(s):
    s = unicodedata.normalize("NFC", s.lower()).replace("़", "")   # ignore nukta differences
    s = "".join(" " if unicodedata.category(c)[0] in "PS" else c for c in s)
    return re.sub(r"\s+", " ", s).strip()
NUMN = {norm(w) for w in NUM}

def edits(a, b):
    prev = list(range(len(b) + 1))
    for i in range(1, len(a) + 1):
        cur = [i] + [0] * len(b)
        for j in range(1, len(b) + 1):
            cur[j] = min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] != b[j - 1]))
        prev = cur
    return prev[-1]

def recognizer(spec):
    kind, *rest = spec.split(":")
    if kind == "onnx":
        import sherpa_onnx
        rec = sherpa_onnx.OfflineRecognizer.from_nemo_ctc(model=rest[0], tokens=rest[1], num_threads=4)
        import os
        pad = np.zeros(int(os.environ.get("PAD", "0")), np.float32)   # leading samples (frame alignment)
        def run(a):
            s = rec.create_stream(); s.accept_waveform(16000, np.concatenate([pad, a])); rec.decode_stream(s); return s.result.text
        return run
    import torch, sys as _s; _s.path.insert(0, __file__.rsplit("/", 1)[0])
    from icmodel import ICModel
    torch.set_num_threads(4)
    m = ICModel(rest[0], "hi").eval()
    if len(rest) > 1: m.load_state_dict(torch.load(rest[1], map_location="cpu", weights_only=True), strict=False)
    def run(a):
        with torch.no_grad():
            w = torch.from_numpy(a)[None]; l = torch.tensor([len(a)])
            f, fl = m.features(w, l); lp, el = m(f, fl)
            return m.decode_ids(lp[0, :el[0]].argmax(-1).tolist())
    return run

def score(manifest, spec, n=None, show=0):
    rows = [json.loads(l) for l in open(manifest, encoding="utf-8")][:n]
    run = recognizer(spec)
    we = wn = ce = cn = nh = nt = 0; t0 = time.time(); audio = 0; out = []
    for r in rows:
        a, sr = sf.read(r["audio"], dtype="float32"); audio += len(a) / 16000
        hyp, ref = norm(run(a)), norm(r["text"])
        we += edits(hyp.split(), ref.split()); wn += len(ref.split())
        ce += edits(hyp.replace(" ", ""), ref.replace(" ", "")); cn += len(ref.replace(" ", ""))
        rn = collections.Counter(w for w in ref.split() if w in NUMN); hn = collections.Counter(w for w in hyp.split() if w in NUMN)
        nt += sum(rn.values()); nh += sum((rn & hn).values())
        out.append({"ref": ref, "hyp": hyp})
    return {"n": len(rows), "wer": round(we / wn, 4), "cer": round(ce / cn, 4),
            "num_recall": round(nh / nt, 4) if nt else None, "num_words": nt, "rtf": round((time.time() - t0) / audio, 3)}, out

if __name__ == "__main__":
    res, out = score(sys.argv[1], sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else None)
    print(json.dumps({"manifest": sys.argv[1], "model": sys.argv[2], **res}, ensure_ascii=False))
    for o in out[:3]: print("  REF", o["ref"][:110], "\n  HYP", o["hyp"][:110])
