#!/usr/bin/env python3
"""Work with the voice recordings from HisaabSaathi Recorder (recorder/).

Every session the recorder exports is a zip laid out like the shared dataset
(Hugging Face: AcxelinLabs/hisaabsaathi-voices, private):

    data/<lang>/<session>/audio/<question>__<speaker>__<take>.wav   16 kHz mono
    data/<lang>/<session>/metadata.csv        one row per recording
    data/<lang>/<session>/recordings.xlsx     the same, for Excel
    data/<lang>/<session>/session.json        agent, speaker details, phone
    data/<lang>/<session>/consent.wav         the speaker's spoken yes

Commands (run from android/):

  voices.py unpack <zips or folder> <dataset>   unzip recorder exports into one dataset folder
  voices.py check <dataset>                     counts per language / speaker / place; missing or broken files
  voices.py table <dataset> <out.csv>           one table of every recording (for Excel or pandas)
  voices.py transcribe <dataset> [--models DIR] run the app's own speech models on every recording and write
                                                core/src/test/resources/voice_heard.jsonl; then
                                                ./gradlew :core:test --tests '*VoiceQuestionsTest*' scores what the
                                                app understood (core/build/voice-questions-report.txt)

Get the dataset from Hugging Face with:  hf download AcxelinLabs/hisaabsaathi-voices --repo-type dataset --local-dir <dataset>
"""
import csv, collections, io, json, os, sys, wave, zipfile

COLUMNS = None  # read from each metadata.csv


def sessions(root):
    """Every session folder (one with a metadata.csv) under root/data."""
    base = os.path.join(root, "data")
    for lang in sorted(os.listdir(base)) if os.path.isdir(base) else []:
        for s in sorted(os.listdir(os.path.join(base, lang))):
            d = os.path.join(base, lang, s)
            if os.path.isfile(os.path.join(d, "metadata.csv")):
                yield d


def rows(root):
    for d in sessions(root):
        with open(os.path.join(d, "metadata.csv"), encoding="utf-8", newline="") as f:
            for r in csv.DictReader(f):
                r["path"] = os.path.relpath(os.path.join(d, r["file_name"]), root)
                yield r


def unpack(sources, root):
    zips = []
    for s in sources:
        if os.path.isdir(s):
            zips += [os.path.join(s, n) for n in sorted(os.listdir(s)) if n.endswith(".zip")]
        else:
            zips.append(s)
    for z in zips:
        with zipfile.ZipFile(z) as zf:
            names = zf.namelist()
            # Only the recorder's own layout, and nothing that could escape the folder.
            bad = [n for n in names if not n.startswith("data/") or ".." in n.split("/") or n.startswith("/")]
            if bad:
                print(f"SKIP {z}: unexpected paths {bad[:3]}"); continue
            zf.extractall(root)
            print(f"ok   {os.path.basename(z)}: {sum(n.endswith('.wav') and '/audio/' in n for n in names)} recordings")


def check(root):
    by_lang = collections.Counter(); speakers = collections.defaultdict(set); places = collections.Counter()
    genders = collections.Counter(); questions = collections.defaultdict(set); problems = []
    total_s = 0.0
    for r in rows(root):
        p = os.path.join(root, r["path"])
        lang = r["lang"]
        by_lang[lang] += 1; speakers[lang].add(r["speaker"]); places[r["place"]] += 1; genders[r["gender"]] += 1
        questions[lang].add(r["prompt_id"])
        try:
            with wave.open(p) as w:
                if (w.getframerate(), w.getnchannels(), w.getsampwidth()) != (16000, 1, 2):
                    problems.append(f"{r['path']}: not 16 kHz mono 16-bit")
                secs = w.getnframes() / w.getframerate(); total_s += secs
                if secs < 0.5:
                    problems.append(f"{r['path']}: only {secs:.1f} s")
        except FileNotFoundError:
            problems.append(f"{r['path']}: missing")
        except wave.Error as e:
            problems.append(f"{r['path']}: {e}")
    for d in sessions(root):
        if not os.path.isfile(os.path.join(d, "consent.wav")):
            problems.append(f"{os.path.relpath(d, root)}: no consent recording")
    print(f"{sum(by_lang.values())} recordings, {total_s / 3600:.2f} hours")
    for lang, n in sorted(by_lang.items()):
        print(f"  {lang}: {n} recordings, {len(speakers[lang])} speakers, {len(questions[lang])} of 300 questions")
    print("  gender:", dict(genders)); print("  place:", dict(places))
    print(f"{len(problems)} problems" + (":" if problems else ""))
    for p in problems[:50]:
        print("  " + p)


def table(root, out):
    rs = list(rows(root))
    if not rs:
        print("no recordings"); return
    with open(out, "w", encoding="utf-8-sig", newline="") as f:  # utf-8-sig: Excel shows Indian scripts correctly
        w = csv.DictWriter(f, fieldnames=list(rs[0].keys())); w.writeheader(); w.writerows(rs)
    print(f"{len(rs)} rows -> {out}")


# The app's speech packs (app/.../models/Packs.kt): same files, same engine settings.
INDIC = "parismitaglobalsolutions/indicconformer-sherpa-onnx"
OWN = "AcxelinLabs/hisaabsaathi-model"


def model_files(models, lang):
    from huggingface_hub import hf_hub_download
    if lang == "en":
        need = [(OWN, "speech/en-parakeet/model.int8.onnx"), (OWN, "speech/en-parakeet/tokens.txt")]
    else:
        need = [(INDIC, f"{lang}/model.int8.onnx"), (INDIC, "tokens.txt")]
    paths = []
    for repo, name in need:
        local = os.path.join(models, repo.split("/")[1], name)
        if not os.path.exists(local):
            hf_hub_download(repo, name, local_dir=os.path.join(models, repo.split("/")[1]))
        paths.append(local)
    return paths


def transcribe(root, models):
    import numpy as np, sherpa_onnx
    out = []
    recognizers = {}
    for r in rows(root):
        lang = r["lang"]
        if lang not in recognizers:
            model, tokens = model_files(models, lang)
            recognizers[lang] = sherpa_onnx.OfflineRecognizer.from_nemo_ctc(model=model, tokens=tokens, num_threads=2, decoding_method="greedy_search")
        with wave.open(os.path.join(root, r["path"])) as w:
            pcm = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768
        s = recognizers[lang].create_stream(); s.accept_waveform(16000, pcm); recognizers[lang].decode_stream(s)
        heard = s.result.text.strip()
        out.append({"rec": os.path.basename(r["path"]), "id": r["prompt_id"], "lang": lang, "speaker": r["speaker"], "place": r["place"], "heard": heard})
        print(f"{r['prompt_id']}  {heard}")
    dest = "core/src/test/resources/voice_heard.jsonl"
    with open(dest, "w", encoding="utf-8") as f:
        for o in out:
            f.write(json.dumps(o, ensure_ascii=False) + "\n")
    print(f"{len(out)} recordings -> {dest}\nnow: ./gradlew :core:test --tests '*VoiceQuestionsTest*' and read core/build/voice-questions-report.txt")


def main(a):
    if len(a) >= 3 and a[0] == "unpack":
        unpack(a[1:-1], a[-1])
    elif len(a) == 2 and a[0] == "check":
        check(a[1])
    elif len(a) == 3 and a[0] == "table":
        table(a[1], a[2])
    elif len(a) >= 2 and a[0] == "transcribe":
        models = a[a.index("--models") + 1] if "--models" in a else os.path.expanduser("~/.cache/hisaabsaathi-models")
        transcribe(a[1], models)
    else:
        print(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
