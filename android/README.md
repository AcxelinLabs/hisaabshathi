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
| Understanding (optional) | Qwen2.5-0.5B / 1.5B Instruct, 8-bit, via MediaPipe LLM Inference | 547 MB / 1.6 GB | Apache-2.0 |
| OCR | ML Kit text recognition (Latin + Devanagari), bundled | in APK | Google ML Kit terms |
| Text-to-speech | The phone's own TTS engine | — | — |

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
speech. It is still below the rules on this corpus, so the app keeps the model switched off
(`Slm.TRUSTED = false`). To ship it, the LoRA adapter must be merged and converted to a
MediaPipe `.task` file (ai-edge-torch); that conversion has not been done.

## Look

Ink blue and marigold, styled after a hand-written wage slip; type is Anek (SIL OFL), one
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
language model, camera/OCR, notifications and text-to-speech voices need testing on devices.

## Known gaps

- Odia, Telugu, Kannada and Tamil strings and number words (`core/i18n`) need native-speaker review.
- `app/src/main/assets/wage_pack_in.json` has central (unskilled, Oct 2026), Odisha (Oct 2026),
  Telangana (June 2026, low confidence) and Delhi (unskilled) rates, taken from secondary
  summaries of the notifications. Karnataka, Tamil Nadu, Andhra Pradesh, Kerala, Maharashtra,
  Gujarat and West Bengal have no confirmed figure yet and fall back to the central rate.
  Check every figure against the gazette notification before the pilot.
- No open-licence Odia text-to-speech voice was found; Odia readback depends on the phone
  having an Odia voice. No Odia-script OCR.
- Release build is signed with the debug key. No Play Store listing yet.
