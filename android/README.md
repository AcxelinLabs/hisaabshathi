# HisaabSaathi — Android MVP

Voice-first, offline wage ledger for daily-wage and piece-rate workers (Hindi, Odia, English).
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
| Speech-to-text | AI4Bharat IndicConformer (Hindi, Odia), int8, via sherpa-onnx | ~197 MB each | MIT |
| Understanding (optional) | Qwen2.5-0.5B / 1.5B Instruct, 8-bit, via MediaPipe LLM Inference | 547 MB / 1.6 GB | Apache-2.0 |
| OCR | ML Kit text recognition (Latin + Devanagari), bundled | in APK | Google ML Kit terms |
| Text-to-speech | The phone's own TTS engine | — | — |

Entries are extracted by a rule-based grammar (`core/RuleBasedExtractor.kt`). When the
language-model pack is installed it is consulted for sentences the rules are unsure about,
and its answer is discarded if it contains any number the worker did not say (`core/Slm.kt`).
All arithmetic is plain code.

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

- Odia strings and number words need a native-speaker review.
- `app/src/main/assets/wage_pack_in.json` holds central rates from October 2024 only. It is
  out of date and has no state rates.
- No open-licence Odia text-to-speech voice was found; Odia readback depends on the phone
  having an Odia voice. No Odia-script OCR.
- Release build is signed with the debug key. No Play Store listing yet.
