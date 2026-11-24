#!/usr/bin/env python3
"""Makes the spoken language menu for the first setup screen: each line in its own language and
its own voice, one after the other, in one file:

    "हिंदी के लिए एक दबाइए।  ଓଡ଼ିଆ ପାଇଁ ଦୁଇ ଦବାନ୍ତୁ। … For English, press six."

    pip install numpy onnxruntime
    python tools/tts/make_language_prompt.py            (run from the android folder)

The voices are the app's own (Indic Tiny TTS, same pinned revisions as make_onboarding_audio.py,
plus the optional English voice). Writes app/src/main/assets/onboarding_audio/pick_language.wav, and pick_language.txt with when
each language starts and ends (the screen follows it).
The numbers must match the numbers on the language buttons (Lang order in core/Model.kt).
A native speaker's recording with the same name can replace it later.
"""
import os, sys
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
ANDROID = os.path.dirname(os.path.dirname(HERE))

from make_onboarding_audio import VOICES, fetch_voice  # noqa: E402

# Optional English voice, as in Catalog.voiceEnglish (models/Packs.kt).
VOICES = dict(VOICES, en=("english", "eea5d4a578c0f18173825a037487cf9c47ed4247"))

# Same order and numbers as the buttons. Numbers are written as words so every voice says them.
LINES = [
    ("hi", "हिंदी के लिए एक दबाइए।"),
    ("or", "ଓଡ଼ିଆ ପାଇଁ ଦୁଇ ଦବାନ୍ତୁ।"),
    ("te", "తెలుగు కోసం మూడు నొక్కండి."),
    ("kn", "ಕನ್ನಡಕ್ಕಾಗಿ ನಾಲ್ಕು ಒತ್ತಿ."),
    ("ta", "தமிழுக்கு ஐந்து அழுத்துங்கள்."),
    ("en", "For English, press six."),
]
GAP_S = 0.45


def main():
    import make_onboarding_audio as m
    m.VOICES.update(VOICES)
    from tiny_tts_ref import TinyTTS, save_wav

    parts, sr, spans, at = [], None, [], 0
    for code, text in LINES:
        tts = TinyTTS(fetch_voice(code))
        wav = tts.synthesize(text, code, "female", seed=0).astype(np.float32)
        if sr is None:
            sr = tts.sr
        elif tts.sr != sr:  # resample to the first voice's rate (simple linear; speech only)
            n = int(len(wav) * sr / tts.sr)
            wav = np.interp(np.linspace(0, len(wav) - 1, n), np.arange(len(wav)), wav).astype(np.float32)
        peak = float(np.max(np.abs(wav))) or 1.0
        parts += [wav * (0.9 / peak), np.zeros(int(GAP_S * sr), np.float32)]
        spans.append((code, round(at * 1000 / sr), round((at + len(wav)) * 1000 / sr)))
        at += len(wav) + int(GAP_S * sr)
        print(f"  {code}: {len(wav) / sr:.1f} s  {text}")
    out = os.path.join(ANDROID, "app", "src", "main", "assets", "onboarding_audio", "pick_language.wav")
    save_wav(np.concatenate(parts), sr, out)
    # When each language is spoken, so the screen can show that line and light up its button.
    with open(out[:-4] + ".txt", "w", encoding="utf-8") as f:
        f.write("# language start_ms end_ms, in pick_language.wav\n")
        for code, a, b in spans:
            f.write(f"{code} {a} {b}\n")
    print("wrote", out, "and its timings")


if __name__ == "__main__":
    main()
