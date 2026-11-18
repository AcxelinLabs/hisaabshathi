#!/usr/bin/env python3
"""Tests Indic Tiny TTS voices on the sentences the app actually speaks: speed, and how
intelligible the voice is, measured by transcribing it with the app's speech recogniser and
comparing with the text (character error rate; lower is clearer).

usage: bench_tts.py <tts_root> <stt_dir> <out_dir>"""
import json, os, re, sys, time, unicodedata
import numpy as np, librosa, soundfile as sf, sherpa_onnx

root, stt, out = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(out, exist_ok=True)
LANGS = {"hi": "hindi", "or": "odia", "te": "telugu", "kn": "kannada", "ta": "tamil", "en": "english"}
# Sentences the app speaks (readback, weekly answer, simple-question reply, minimum-wage line), from Strings.kt
TEXT = {
    "hi": ["120 पीस। रेट 5 रुपये। कमाई 600 रुपये। एडवांस लिया 300 रुपये। क्या यह सही है?",
           "इस हफ्ते आपने 600 रुपये कमाए।", "मैं हिसाब साथी हूँ। मैं आपकी कमाई, एडवांस और खर्च का हिसाब रखता हूँ।"],
    "or": ["120 ପିସ୍। ଦର 5 ଟଙ୍କା। ରୋଜଗାର 600 ଟଙ୍କା। ଆଡଭାନ୍ସ ନେଲେ 300 ଟଙ୍କା। ଏହା ଠିକ୍ କି?",
           "ଏହି ସପ୍ତାହରେ ଆପଣ 600 ଟଙ୍କା ରୋଜଗାର କଲେ।", "ମୁଁ ହିସାବ ସାଥୀ। ମୁଁ ଆପଣଙ୍କ ରୋଜଗାର, ଆଡଭାନ୍ସ ଓ ଖର୍ଚ୍ଚର ହିସାବ ରଖେ।"],
    "te": ["120 పీసులు. రేటు 5 రూపాయలు. సంపాదన 600 రూపాయలు. అడ్వాన్స్ 300 రూపాయలు. ఇది సరైనదేనా?",
           "ఈ వారం మీరు 600 రూపాయలు సంపాదించారు.", "నేను హిసాబ్ సాథీని. మీ సంపాదన, అడ్వాన్స్, ఖర్చుల లెక్క నేను రాసి పెడతాను."],
    "kn": ["120 ಪೀಸ್. ರೇಟ್ 5 ರೂಪಾಯಿ. ಸಂಪಾದನೆ 600 ರೂಪಾಯಿ. ಅಡ್ವಾನ್ಸ್ 300 ರೂಪಾಯಿ. ಇದು ಸರಿನಾ?",
           "ಈ ವಾರ ನೀವು 600 ರೂಪಾಯಿ ದುಡಿದಿದ್ದೀರಿ.", "ನಾನು ಹಿಸಾಬ್ ಸಾಥಿ. ನಿಮ್ಮ ಸಂಪಾದನೆ, ಅಡ್ವಾನ್ಸ್, ಖರ್ಚಿನ ಲೆಕ್ಕ ನಾನು ಇಡುತ್ತೇನೆ."],
    "ta": ["120 பீஸ். ரேட் 5 ரூபாய். சம்பாத்தியம் 600 ரூபாய். அட்வான்ஸ் 300 ரூபாய். இது சரியா?",
           "இந்த வாரம் நீங்க 600 ரூபாய் சம்பாதிச்சீங்க.", "நான் ஹிசாப் சாத்தி. உங்க சம்பாத்தியம், அட்வான்ஸ், செலவு கணக்கை நான் வச்சுக்கிறேன்."],
    "en": ["120 pieces. Rate 5 rupees. Earned 600 rupees. Advance taken 300 rupees. Is this right?",
           "This week you earned 600 rupees.", "I am HisaabSaathi. I keep track of your earnings, advances and expenses."],
}

def norm(s):
    s = unicodedata.normalize("NFC", s.lower())
    s = "".join(" " if unicodedata.category(c)[0] in "PS" else c for c in s)
    return re.sub(r"\s+", "", s)

def edits(a, b):
    prev = list(range(len(b) + 1))
    for i in range(1, len(a) + 1):
        cur = [i] + [0] * len(b)
        for j in range(1, len(b) + 1):
            cur[j] = min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] != b[j - 1]))
        prev = cur
    return prev[-1]

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "tts"))
from tiny_tts_ref import TinyTTS, save_wav
from numspell import spell_numbers

results = []
for code, name in LANGS.items():
    if code == "en": continue  # English uses the phone's own voice
    tts = TinyTTS(f"{root}/m_{name}", threads=4)
    rec = sherpa_onnx.OfflineRecognizer.from_nemo_ctc(model=f"{stt}/{code}/model.int8.onnx", tokens=f"{stt}/tokens.txt", num_threads=4)
    for voice in tts.cfg["voices"]:
        e = n = 0; syn = aud = 0.0
        for i, text in enumerate(TEXT[code]):
            t = time.time(); wav = tts.synthesize(text, code, voice); syn += time.time() - t; aud += len(wav) / tts.sr
            save_wav(wav, tts.sr, f"{out}/{code}_{voice}_{i}.wav")
            a16 = librosa.resample(wav, orig_sr=tts.sr, target_sr=16000)
            s = rec.create_stream(); s.accept_waveform(16000, a16); rec.decode_stream(s)
            ref = norm(tts.normalize(text, code))
            e += edits(norm(s.result.text), ref); n += len(ref)
            if i == 0: print(code, voice, "| said:", tts.normalize(text, code), "| heard:", s.result.text, flush=True)
        results.append({"lang": code, "voice": voice, "cer": round(e / n, 4), "rtf": round(syn / aud, 3)})
        print(json.dumps(results[-1]), flush=True)
json.dump(results, open(f"{out}/results.json", "w"), indent=1)
