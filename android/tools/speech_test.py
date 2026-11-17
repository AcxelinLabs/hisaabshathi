#!/usr/bin/env python3
"""End-to-end speech test on a PC, mirroring the app's listening pipeline.

1. Picks corpus sentences per language (entries, ledger questions, simple questions,
   out-of-scope requests) and speaks them with Meta's MMS text-to-speech (test use only;
   MMS is CC-BY-NC and is not shipped in the app).
2. Plays each one into a copy of the app's capture loop: 16 kHz audio in 512-sample
   windows, Silero VAD v5 with the app's settings, the app's end-of-speech rule
   (1.3 s of silence after speech, give up after 6 s without speech).
3. Transcribes with the same IndicConformer / NeMo models and sherpa-onnx version as the
   app, and writes core/src/test/resources/speech_transcripts.jsonl for
   SpeechCorpusTest, which runs the app's own understanding logic on every transcript.

Scenarios per sentence: clean, background noise (10 dB SNR), slow speech, and a long
pause before speaking. Plus silence-only clips, which must come out as "heard nothing".

usage (from android/): speech_test.py <stt_models_dir> <vad.onnx> [per_language]
"""
import json, random, sys, time
import numpy as np, torch, librosa, sherpa_onnx
from transformers import VitsModel, AutoTokenizer

stt_dir, vad_path = sys.argv[1], sys.argv[2]
per_lang = int(sys.argv[3]) if len(sys.argv) > 3 else 14
SR, WIN = 16000, 512
random.seed(1); np.random.seed(1); torch.manual_seed(1)
MMS = {"hi": "hin", "or": "ory", "te": "tel", "kn": "kan", "ta": "tam", "en": "eng"}

corpus = [json.loads(l) for l in open("core/src/test/resources/corpus.jsonl", encoding="utf-8")]
def pick(lang):
    rows = [c for c in corpus if c["lang"] == lang and c.get("form") != "digits"]
    by = {}
    for c in rows: by.setdefault(c["kind"], []).append(c)
    want = {"entries": per_lang - 7, "query": 2, "chat": 4, "unknown": 1}
    out = []
    for k, n in want.items(): out += random.sample(by.get(k, []), min(n, len(by.get(k, []))))
    return out

def recognizer(lang):
    model = f"{stt_dir}/{lang}/model.int8.onnx"
    tokens = f"{stt_dir}/en/tokens.txt" if lang == "en" else f"{stt_dir}/tokens.txt"
    return sherpa_onnx.OfflineRecognizer.from_nemo_ctc(model=model, tokens=tokens, num_threads=2, decoding_method="greedy_search")

def capture(audio):
    """The app's record() loop: returns the samples it would keep, or None for 'heard nothing'."""
    cfg = sherpa_onnx.VadModelConfig()
    cfg.silero_vad.model = vad_path; cfg.silero_vad.threshold = 0.5; cfg.silero_vad.min_silence_duration = 0.5
    cfg.silero_vad.min_speech_duration = 0.25; cfg.silero_vad.window_size = WIN; cfg.silero_vad.max_speech_duration = 20
    cfg.sample_rate = SR; cfg.num_threads = 1
    vad = sherpa_onnx.VoiceActivityDetector(cfg, buffer_size_in_seconds=60)
    heard, quiet, count = False, 0, 0
    end_silence = int(1.3 * SR / WIN)
    for i in range(0, len(audio) - WIN + 1, WIN):
        vad.accept_waveform(audio[i:i + WIN]); count += WIN
        if vad.is_speech_detected(): heard, quiet = True, 0
        elif heard:
            quiet += 1
            if quiet >= end_silence: break
        elif count > 6 * SR: return None
    if not heard or count < SR // 4: return None
    return audio[:count]

def speak(model, tok, text, speed=1.0):
    with torch.no_grad():
        model.config.speaking_rate = speed
        wav = model(**tok(text, return_tensors="pt")).waveform[0].numpy()
    return librosa.resample(wav, orig_sr=model.config.sampling_rate, target_sr=SR).astype(np.float32)

def scenario(wav, kind):
    pad = lambda s: np.zeros(int(s * SR), np.float32)
    if kind == "clean": a = np.concatenate([pad(0.5), wav, pad(2.0)])
    elif kind == "noise":
        a = np.concatenate([pad(0.5), wav, pad(2.0)])
        p = np.mean(wav ** 2); a = a + np.random.normal(0, np.sqrt(p / 10), len(a)).astype(np.float32)
    elif kind == "slow": a = np.concatenate([pad(0.5), wav, pad(2.0)])  # wav already spoken slowly
    elif kind == "late": a = np.concatenate([pad(3.0), wav, pad(2.0)])
    return a.astype(np.float32)

out = open("core/src/test/resources/speech_transcripts.jsonl", "w", encoding="utf-8")
t0 = time.time()
for lang, code in MMS.items():
    tok = AutoTokenizer.from_pretrained(f"facebook/mms-tts-{code}"); tts = VitsModel.from_pretrained(f"facebook/mms-tts-{code}")
    rec = recognizer(lang)
    for c in pick(lang):
        base = speak(tts, tok, c["text"]); slow = speak(tts, tok, c["text"], 0.8)
        for kind in ("clean", "noise", "slow", "late"):
            kept = capture(scenario(slow if kind == "slow" else base, kind))
            if kept is None: text = None
            else:
                s = rec.create_stream(); s.accept_waveform(SR, kept); rec.decode_stream(s); text = s.result.text.strip()
            out.write(json.dumps({"lang": lang, "scenario": kind, "said": c["text"], "heard": text, "expected": c}, ensure_ascii=False) + "\n"); out.flush()
    # silence and room noise only: the app must say "heard nothing", never act
    for kind, a in (("silence", np.zeros(8 * SR, np.float32)), ("room-noise", np.random.normal(0, 0.003, 8 * SR).astype(np.float32))):
        kept = capture(a)
        out.write(json.dumps({"lang": lang, "scenario": kind, "said": "", "heard": None if kept is None else "(captured)", "expected": {"kind": "silence"}}, ensure_ascii=False) + "\n")
    print(f"{lang} done at {time.time() - t0:.0f}s", flush=True)
