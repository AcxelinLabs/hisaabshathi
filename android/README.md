# HisaabSaathi — Android MVP

Voice-first, offline wage ledger for daily-wage and piece-rate workers (Hindi, Odia, Telugu, Kannada, Tamil, English).
Design notes: `../docs/superpowers/specs/2026-11-16-android-mvp-design.md`.

## What works

- Speak (or type) an entry → on-device speech-to-text → structured fields → confirmation
  card with spoken readback → saved only after the worker confirms.
- Work (hours, pieces, rate), advances, wages received, expenses with categories.
- Today / week / month / year summaries, on screen and read aloud; voice questions
  ("इस हफ्ते कितना कमाया").
- Savings goal, minimum-wage comparison, PDF report, tamper-evident evidence pack.
- 3 PM and 9 PM local reminders. Bill / advance-slip photo → amount (on-device OCR).
- Ledger encrypted with SQLCipher; key held by the Android Keystore; no cloud backup.

## On-device models (downloaded once, then offline)

| Job | Model | Size | Licence |
|---|---|---|---|
| Voice activity | Silero VAD v5 | 2 MB | MIT |
| Speech-to-text | AI4Bharat IndicConformer (Hindi, Odia, Telugu, Kannada, Tamil), int8, via sherpa-onnx | ~197 MB each | MIT |
| Understanding (optional) | Qwen2.5-0.5B Instruct fine-tuned by Acxelin Labs, 8-bit, via LiteRT-LM | 513 MB | Apache-2.0 |
| OCR | ML Kit text recognition (Latin + Devanagari), bundled | in APK | Google ML Kit terms |
| Text-to-speech | Indic Tiny TTS (Hindi, Odia, Telugu, Kannada, Tamil; Matcha + Vocos), run on ONNX Runtime 1.29 in its own `:voice` process; falls back to the phone's TTS for English or if the voice fails | ~91 MB each | CC-BY-4.0 (voices), MIT (Vocos) |

How an utterance is understood (`core/Understand.kt`), in order:

1. **Ledger entry or ledger question**: rule-based grammar (`core/RuleBasedExtractor.kt`).
2. **Greeting or simple question** ("what is your name", "is my data safe", "how do I use
   this"): fixed replies in all six languages (`core/SmallTalk.kt`).
3. **Anything else**: if the language-model pack is installed it may answer briefly;
   otherwise the app says what it can do and how to phrase an entry. It never stays silent.

The language model does not write entries. The app finds the numbers that were said and the
model only labels each one (hours, pieces, rate, wage, advance, expense…). An answer that
does not label exactly the spoken numbers is thrown away (`core/Slm.kt`). All arithmetic is
plain code.

## Speech model benchmarks (18 Nov 2026, on a PC, not a phone)

Speech-to-text on 40 real FLEURS recordings per language (word / character error rate):

| Language | IndicConformer 120M (in the app) | IndicConformer 600M |
|---|---|---|
| Hindi | 12.9% / 6.3% | 14.7% / 7.2% |
| Odia | 20.7% / 6.5% | 29.4% / 13.8% |
| Telugu | 26.3% / 11.1% | 27.9% / 11.6% |
| Kannada | 22.5% / 8.8% | 22.5% / 9.2% |
| Tamil | 24.1% / 9.3% | 27.5% / 10.0% |
| English (NeMo) | 12.1% / 6.8% | — |

The larger model was not better, so the app keeps the smaller one. Fine-tuning speech
recognition for workers' own speech needs real, consented recordings and a GPU; training on
synthetic voices risks making real speech worse.

Voices: every sentence was spoken and then transcribed back by the recogniser (character
error rate, female / male voice; speed is seconds of compute per second of audio, 4 threads):
Hindi 4.2% / 5.6%, Odia 5.2% / 8.4%, Telugu 2.8% / 2.2%, Kannada 1.2% / 0%, Tamil 2.0% / 2.0%;
speed 0.07–0.10. The Kotlin player matches the Python reference to 1.9e-7 RMS
(`core/TinyTts.kt`, `tools/tts/`). Numbers are spelled out in each language first
(`core/NumberSpeller.kt`).

## Test corpus

`core/src/test/resources/corpus.jsonl` holds 638 prompts in Hindi, Odia, Telugu, Kannada,
Tamil, English and Hindi in Latin letters: entries as spoken words and as digits, ledger
questions, greetings and simple questions, and out-of-scope requests. `CorpusTest` runs every
one through the app logic. Regenerate with `python3 tools/make_corpus.py`. The entry
sentences are templates filled from the app's own number tables, so they test grammar and
keywords, not real speech-recognition output.

`tools/eval_slm.py` runs the app's exact prompts through a Qwen2.5 GGUF on a PC and
`CorpusTest.scoreRecordedModelAnswers` scores the answers (`core/build/slm-report.txt`).

### Language model results (16–17 Nov 2026, on a PC with llama.cpp / PyTorch, not on a phone)

| Model | Entry sentences labelled exactly right | Simple-question replies |
|---|---|---|
| Rules only (what the app uses) | 638 / 638 corpus prompts | fixed replies, all six languages |
| Qwen2.5-0.5B, stock | 16 / 240 (7%) | wrong script or nonsense in Odia, Telugu, Kannada, Tamil |
| Qwen2.5-1.5B, stock | 33 / 120 (28%) | right script, often wrong ("India's capital is Greece") |
| Qwen2.5-0.5B, LoRA fine-tuned (`tools/finetune_slm.py`) | 133 / 140 (95%) | not fine-tuned for chat |

The fine-tuned model was trained on sentences built from the same templates as the corpus
(with no overlapping sentences), so 95% shows it can learn the task, not how it does on real
speech.

### The fine-tuned model in the app

- Merged and converted to 8-bit LiteRT-LM with litert-torch (`--transpose_kv_cache=true`,
  generic model type with Qwen's chat template including its default system turn). File:
  `hisaabsaathi-qwen05-ft-q8.litertlm`, 513 MB, sha256
  `35547b01dffa191303e1d8ca1e4e2c75abbc5c8f330cc957e905d0e9dc3c7a7f`.
- In the LiteRT-LM runtime (the same C++ engine as the Android library, 0.19) it labels
  78/80 held-out prompts right, median 0.8 s a sentence on 4 PC cores
  (`tools/check_litertlm.py`). Before conversion: 133/140.
- Runs in its own `:llm` process; repacked weights are cached in the app's cache folder
  (about 500 MB, which Android may clear when storage is low).
- The app asks it only when the rules are unsure or found nothing, and keeps its answer only
  if it labels exactly the numbers that were said, so it cannot override a confident rule
  result or add an amount.
- Hosted at https://huggingface.co/QubitMan/hisaabsaathi-model (Apache-2.0). The app
  offers it during first-time setup and under Settings → Offline packs; downloads resume
  if interrupted, and a file of the wrong size is never loaded. "Add model file from phone"
  in Settings works without internet. The link is `HISAAB_MODEL_URL` in `app/build.gradle.kts`.
- Not yet checked on a phone: LiteRT-LM loading the model, speed and memory on 3–4 GB
  devices. LiteRT-LM ships only arm64 and x86_64 libraries, so 32-bit phones run on rules alone.

## Look

Deep ledger green and marigold, styled after a hand-written wage slip; type is Anek (SIL OFL), one
family covering all six scripts (`app/src/main/res/font`).

## Build and test

```bash
./gradlew :core:test                 # logic tests (number words, extraction, ledger, hash chain)
```

```bash
./gradlew :app:testDebugUnitTest     # drives the real screens end to end; screenshots in app/build/screens
```

```bash
./gradlew :app:assembleDebug         # APKs in app/build/outputs/apk/debug (use arm64-v8a for most phones)
```

Needs JDK 17+ and the Android SDK (platform 35). Set `sdk.dir` in `local.properties`.

## Not yet verified on a real phone

This was built without an emulator. Unit and screen tests pass and the speech models were
checked on Linux, but microphone capture, model speed and memory on 3–4 GB phones, the
language model, camera/OCR, notifications and the downloadable voices (their separate
process and runtime) need testing on devices.

## Known gaps

- Odia, Telugu, Kannada and Tamil strings and number words (`core/i18n`) need native-speaker review.
- `app/src/main/assets/wage_pack_in.json` has central (unskilled, Oct 2026), Odisha (Oct 2026),
  Telangana (June 2026, low confidence) and Delhi (unskilled) rates, taken from secondary
  summaries of the notifications. Karnataka, Tamil Nadu, Andhra Pradesh, Kerala, Maharashtra,
  Gujarat and West Bengal have no confirmed figure yet and fall back to the central rate.
  Check every figure against the gazette notification before the pilot.
- No Odia-script OCR.
- The APK carries two ONNX Runtime builds (the speech engine's 1.28.2 and 1.29 for the voices,
  renamed `libortvoice.so`), about 32 MB extra. The speech engine's build lacks the `Mish`
  operation the voices need.
- ML Kit's Google data transport may send anonymous usage metrics; not yet switched off.
- Release build is signed with the debug key. No Play Store listing yet.
