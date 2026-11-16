# HisaabSaathi Android MVP — design

Date: 2026-11-16. Source of truth for scope: `DOCS/HisaabSaathi_Proposal_Phases_1-3.md`
(Architecture v1.0, Milestone 1.1, plus the parts of 1.2/1.3 and Screen 4 that fit).

## Intent

A daily-wage or piece-rate worker speaks one sentence in Hindi or Odia and gets a
confirmed, encrypted ledger entry on their own phone, with spoken summaries. It must work
with no internet after a one-time model download, on a 3–4 GB RAM Android 10+ phone.
The Mozilla application (due 20 Nov 2026) needs working technology in real hands, so the
goal is an installable app the team can put on pilot phones this week.

## Goals

- Voice entry → on-device speech-to-text → structured fields → card + spoken readback →
  confirm → encrypted save.
- Spoken and on-screen daily / weekly / monthly / annual summaries; voice questions
  ("is hafte kitna kamaya?").
- The four screens of the proposal: Home, Voice confirmation, Reports & savings goal,
  Know My Wages & dispute vault. Plus ledger list, onboarding, privacy/settings.
- 3 PM and 9 PM local reminders.
- Scan a bill or advance slip (on-device OCR) into an entry.
- PDF report export and a tamper-evident evidence pack (SHA-256 hash chain).
- Hindi, Odia and English UI and speech.

## Non-goals (this MVP)

iOS, cloud backup/sync, voice-support queue, credit bureau/marketplace, US edition,
fine-tuned adapters, telemetry of any kind.

## Anti-goals

- Any ledger data, audio or photo leaving the phone without the worker tapping Share.
- Arithmetic done by a language model. Totals come from plain code.
- An entry saved without the worker confirming it.

## Approaches considered

1. **Native Kotlin + sherpa-onnx + MediaPipe LLM Inference (chosen).** One prebuilt
   native library (sherpa-onnx) gives voice-activity detection, speech-to-text and
   text-to-speech; MediaPipe runs the small language model. No C++ build of our own, so
   it can be built and shipped this week.
2. **whisper.cpp + llama.cpp via our own JNI/NDK build** (what the proposal names).
   Most control and the smallest models, but days of native build work, and Whisper has
   no Odia. Rejected for the deadline and for Odia.
3. **Android's built-in speech recogniser and TTS only.** Smallest app, but not reliably
   offline, quality and Odia support depend on the phone, and it weakens the
   "our own small on-device models" case. Kept only as a fallback.

## Decisions that differ from the proposal (my calls, to be confirmed by the team)

| Proposal | MVP | Why |
|---|---|---|
| Whisper-Small / Vosk for STT | AI4Bharat IndicConformer (CTC, int8, ~197 MB per language) via sherpa-onnx | Whisper does not support Odia; IndicConformer covers 22 Indian languages, MIT licence |
| Qwen2.5-1.5B Q4 via llama.cpp (~950 MB) | Qwen2.5-0.5B (547 MB) by default, Qwen2.5-1.5B (1.6 GB) on ≥6 GB phones, via MediaPipe/LiteRT | Published, ungated, Apache-2.0 LiteRT builds exist; 1.5B at q8 does not fit a 3 GB phone |
| SLM does all extraction | Rule-based grammar always available; SLM used when its pack is installed; every SLM number must appear in the transcript or the result is discarded | Works on the lowest-end phones and prevents invented amounts |
| MMS-TTS for Hindi/Odia | Piper Hindi voice via sherpa-onnx; Android system TTS as fallback and for Odia | MMS-TTS and the only Odia Piper voice are non-commercial licences |
| PaddleOCR | ML Kit on-device text recognition (Latin + Devanagari) | Named as an option in the proposal; no model conversion needed. No Odia-script OCR yet |

## Structure

```
android/
  core/   pure Kotlin (JVM), unit-tested: number words (hi/or/en), rule-based extractor,
          SLM prompt + JSON validation, ledger maths, reports, hash chain, wage
          comparison, localized phrases
  app/    Android (Compose): speech, model packs, SLM, encrypted Room/SQLCipher store,
          screens, reminders, OCR, PDF/evidence export
```

Data: one `entries` table. Entry types: WORK (hours, pieces, rate, earned), ADVANCE,
PAYMENT (wages received), EXPENSE (category). Money is stored in paise. Each entry
carries `prevHash`/`hash`. Due = earned − advances − payments.

Storage: SQLCipher database; 32-byte random key wrapped by an Android Keystore AES-GCM
key. Settings in DataStore (not sensitive).

Network: used only to download model packs from Hugging Face, on the worker's request.

## Verification possible here

This build machine has no emulator (no KVM). What can be verified: unit tests of all
`core` logic, a full `assembleDebug` build producing an APK, and the speech models
themselves through sherpa-onnx's Python package on Linux. On-device behaviour (audio,
model speed and memory on 3 GB phones, UI) must be tested by the team on real phones.

## Left to the team

Native review of Odia strings and number words, verified state minimum-wage figures,
licence decision for an Odia voice, Play Store listing and signing key, pilot.
