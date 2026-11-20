#!/usr/bin/env python3
"""Makes the spoken setup questions for Hindi, Odia, Telugu, Kannada and Tamil with the app's
own voices (Indic Tiny TTS, the same voices and revisions the app downloads), so every
question is read aloud from the first screen, before any download on the phone.

    pip install numpy onnxruntime
    python tools/tts/make_onboarding_audio.py            (run from the android folder)

It reads the sentences from tools/onboarding_audio_script.md, downloads each voice once
(about 91 MB each, kept in tools/tts/voices/), and writes WAV files to
app/src/main/assets/onboarding_audio/<language>/<name>.wav. English is skipped: every phone
has an English voice. Re-run after changing a sentence; a native speaker's recording with the
same file name (any of .mp3 .m4a .ogg .wav) can replace any file later.

Options: --voice male, --only or,te (some languages), --force (redo existing files)."""
import argparse, os, re, sys, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
ANDROID = os.path.dirname(os.path.dirname(HERE))

# Same voices and pinned revisions as Catalog in app/.../models/Packs.kt.
VOICES = {
    "hi": ("hindi", "1fb1b108ee6ffb73ee74bd68b283ec13eb39e957"),
    "or": ("odia", "cc5e90366f144ddbf50a3ea99dd832f6ac839b19"),
    "te": ("telugu", "fb552545f06d76d7b5d6365827b69c0bda465767"),
    "kn": ("kannada", "73714eb01cdebc79367f7d1ddf2f4c73adff2be6"),
    "ta": ("tamil", "c3a32ece466031c8a18e72047ab37f5c143cd2b0"),
}
FILES = ["config.json", "onnx/text_encoder.onnx", "onnx/flow_decoder.onnx", "onnx/vocoder.onnx"]


def sentences(script_path):
    """{language code: [(file name without extension, sentence)]} from the recording script."""
    out, code = {}, None
    for line in open(script_path, encoding="utf-8"):
        m = re.search(r"folder `onboarding_audio/([a-z]+)/`", line)
        if m:
            code = m.group(1); out[code] = []; continue
        m = re.match(r"\|\s*`([a-z_]+)\.mp3`\s*\|\s*(.+?)\s*\|\s*$", line)
        if m and code:
            out[code].append((m.group(1), m.group(2)))
    return out


def fetch_voice(code):
    name, rev = VOICES[code]
    folder = os.path.join(HERE, "voices", code)
    for f in FILES:
        dest = os.path.join(folder, *f.split("/"))
        if os.path.exists(dest) and os.path.getsize(dest) > 0:
            continue
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        url = f"https://huggingface.co/sulabhkatiyar/indic-tiny-tts-{name}/resolve/{rev}/{f}"
        print(f"  downloading {name} {f} ...", flush=True)
        urllib.request.urlretrieve(url, dest + ".part")
        os.replace(dest + ".part", dest)
    return folder


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--voice", default="female", choices=["female", "male"])
    ap.add_argument("--only", default="", help="comma-separated codes, e.g. or,te")
    ap.add_argument("--force", action="store_true")
    a = ap.parse_args()

    from tiny_tts_ref import TinyTTS, save_wav  # needs numpy and onnxruntime

    table = sentences(os.path.join(ANDROID, "tools", "onboarding_audio_script.md"))
    codes = [c for c in VOICES if not a.only or c in a.only.split(",")]
    for code in codes:
        rows = table.get(code, [])
        if not rows:
            print(f"{code}: no sentences in the script, skipped"); continue
        out_dir = os.path.join(ANDROID, "app", "src", "main", "assets", "onboarding_audio", code)
        os.makedirs(out_dir, exist_ok=True)
        todo = [(k, t) for k, t in rows if a.force or not any(
            os.path.exists(os.path.join(out_dir, k + ext)) for ext in (".wav", ".mp3", ".m4a", ".ogg"))]
        if not todo:
            print(f"{code}: all {len(rows)} files already there"); continue
        print(f"{code}: {len(todo)} files")
        tts = TinyTTS(fetch_voice(code))
        for key, text in todo:
            wav = tts.synthesize(text, code, a.voice, seed=0)
            save_wav(wav, tts.sr, os.path.join(out_dir, key + ".wav"))
            print(f"  {code}/{key}.wav  {len(wav) / tts.sr:.1f} s")
    print("Done. Rebuild the app: .\\gradlew.bat :app:installDebug")


if __name__ == "__main__":
    main()
