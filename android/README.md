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
- Help improve HisaabSaathi (0.8.0): opt-in, see below.

## On-device models (downloaded once, then offline)

| Job | Model | Size | Licence |
|---|---|---|---|
| Voice activity | Silero VAD v5 | 2 MB | MIT |
| Speech-to-text | AI4Bharat IndicConformer (Hindi, Odia, Telugu, Kannada, Tamil), int8, via sherpa-onnx | ~197 MB each | MIT |
| Speech-to-text, English | NVIDIA Parakeet TDT-CTC 110M (CTC branch), 8-bit weights by Acxelin Labs, via sherpa-onnx | 174 MB | CC-BY-4.0 |
| Understanding (optional) | Qwen3-0.6B fine-tuned by Acxelin Labs, 8-bit, via LiteRT-LM | 617 MB | Apache-2.0 |
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

## Speech re-test in the app's own engine (18 Nov 2026)

The table further down was measured with Python sherpa-onnx 1.12.40, whose audio frontend
differs from the 1.13.8 engine the app ships; those numbers understate the app. These were
measured with sherpa-onnx 1.13.8 (word error rate; share of spoken numbers recognised).

Hindi, current app model vs the same model fine-tuned on rural speech (upper 9 of 17 layers,
4 passes over 9.5 h: AI4Bharat Rural Women ASR v2, Karya Rajasthani, SRUTI Bhojpuri train
set; test sets have no shared sentences, and Rural Women test speakers are unseen):

| Test set | App model | Fine-tuned |
|---|---|---|
| Rural women, unseen speakers (400) | 13.6%; 90.3% | 12.5%; 90.8% |
| SRUTI Bhojpuri (444) | 37.1%; 84.3% | 32.3%; 83.9% |
| Karya Rajasthani (300) | 10.4%; 87.8% | 11.4%; 92.7% |
| FLEURS read Hindi (150) | 9.2%; 97.8% | 10.1%; 100% |
| Karya Hindi samples (10) | 18.3% | 20.0% |

Mixed: better on rural speakers, worse on read Hindi and Rajasthani. The app keeps the
current Hindi model until real workers' recordings decide it.

English on Indian-accented speech (Svarah 300, Karya conversation 27, FLEURS 40):

| Model | Svarah | Karya | FLEURS | Speed |
|---|---|---|---|---|
| NeMo FastConformer (before 0.6.1) | 16.1% | 67.9% | 11.3% | 0.018 |
| Parakeet TDT-CTC 110M, 8-bit (0.6.1) | 13.6% | 53.3% | 10.7% | 0.012 |

## Odia, Telugu, Kannada, Tamil on unseen speakers (18 Nov 2026, sherpa-onnx 1.13.8)

The app's IndicConformer models, run exactly as `speech/Speech.kt` runs them (NeMo CTC,
greedy, whole clip of at most 20 s). Tools and data: `tools/asr/indic/`. No training yet.

Test sets:

- **Vaani** (ARTPARK-IISc, CC-BY-4.0), main set: transcribed clips from speakers with a
  known ID, rural districts first. Odia 2.5 h / 559 speakers (half from Khordha, as the rural
  districts ran out), Telugu 2.5 h / 607, Kannada 2.5 h / 379 (all rural districts),
  Tamil 0.9 h / 438. Speaker IDs are in `tools/asr/indic/sets/test_speakers.json`; keep
  them, and preferably their districts, out of any fine-tuning.
- **IndicVoices** (AI4Bharat, CC-BY-4.0): its public `valid` split is not held out by
  speaker. 99% of its speakers are also in `train` (Odia 519/523, Telugu 320/327,
  Kannada 218/220, Tamil 546/553), which the app's models were trained on. Only the 2–7
  unseen speakers per language are used (2–9 minutes each), plus a 0.5 h sample of seen
  rural speakers for contrast.

Word error rate / character error rate, and the share of spoken number values (other than
1, which here is mostly the article "a") that the app reads the same from the recogniser's
text as from the reference; count in brackets:

| Language | Vaani, unseen | IndicVoices, unseen | IndicVoices, seen speakers |
|---|---|---|---|
| Odia | 48.3% / 14.0%; 86% (149) | 35.5% / 14.4% (75 clips) | 32.7% / 10.4%; 92% (109) |
| Telugu | 34.2% / 9.2%; 92% (155) | 28.2% / 8.4% (72) | 29.5% / 8.1%; 82% (60) |
| Kannada | 53.1% / 18.2%; 88% (152) | 40.6% / 13.9% (21) | 37.8% / 10.9%; 90% (63) |
| Tamil | 54.2% / 16.7%; 94% (133) | 33.1% / 10.1% (111) | 33.4% / 9.6%; 89% (85) |

Odia Vaani rural districts 51.9% WER vs Khordha 44.6%. Vaani transcripts keep dialect
spellings (ଗୋଟେ, ஒன்னு) that the recogniser writes in standard form, so part of its higher
error rate is spelling, not mishearing.

Full app path: each reference transcript and each recogniser output goes through
`Understand` (`PipelineEvalTest`). Where the reference reads as a ledger entry, how often
the recogniser's text gives the same amounts; and how many clips gave an entry from the
recogniser's text when the reference gave none (the confirmation card catches these):

| Language | Vaani: same amount | Vaani: false entries | IndicVoices seen: same amount |
|---|---|---|---|
| Odia | 75% (60/80) | 11 of 1,477 | 93% (43/46) |
| Telugu | 78% (152/195) | 33 of 1,534 | 73% (22/30) |
| Kannada | 70% (105/151) | 31 of 1,093 | 76% (26/34) |
| Tamil | 71% (57/80) | 7 of 765 | 71% (27/38) |

These sentences describe pictures or everyday life, not ledger entries, so this measures
how numbers survive, not real use. Example of a wrong amount: Tamil அறுநூறு (600) heard as
இரநூறு (200).

Number-reader fixes from this test (`RealSpeechNumbersTest`), all included above:

- Colloquial Odia "one" (ଗୋଟେ, ଗୁଟେ, ଗୋଟାଏ: 506 times in 1,477 Vaani Odia transcripts) and
  ଷୋଳ (16) were not read.
- Odia numbers with a counting word attached (ଦୁଇଜଣ "two people", ଚାରିଟା, କୋଡ଼ିଏଟା) were
  not read.
- Everyday words read as numbers: Tamil நாங்கள் / நாங்க ("we") as 4, which made false
  entries; Kannada ಹೊತ್ତು ("time") as 10; Odia ଏଇଟି / ଏଟି ("this one", also how English
  "eighty" is written) as 80, now only before hundred/thousand/rupee words or an English
  unit; ବାର inside କରିବାର ("of doing") as 12, as a word is now split from a number only
  before a money, hours or pieces word.

Before the fixes, Odia Vaani read 64% of number values the same and gave 41 false
entries; same amount on entries was 57% (25/44).


## Speech model benchmarks (earlier, sherpa-onnx 1.12.40, on a PC)

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

### Understanding questions without the model (19 Nov 2026)

The rules (`core/.../QuestionWords.kt`, `RuleBasedExtractor.query`) read ledger questions in
all six languages with no model:

- **Topic from what the sentence says:** "still owed", "took from the contractor" (advance),
  "where did the money go" (spent), "did I get my full wages" (still owed). Looser clues,
  such as a thing bought, "work" or "came", count only with "how much", and only when the
  word after it is about money ("कितने लोग", how many people, is not).
- **Local and mixed words:** dihadi, peshgi, hajira, dadan, sardar, mestri, mungada,
  munpanam, hisab, income, pending. Word roots ("कमा*", "సంపాద*", "செலவ*") match endings
  that aren't listed.
- **New periods:** yesterday, last week and last month can now be asked about.
- **Fewer false readings:** short words must match exactly, since one letter turns
  "कितने" (how many) into "किसने" (who). Clock-time and price questions ("ट्रेन कितने बजे",
  "petrol price") are left alone, and "बता दो" (please tell) is no longer read as 2.

| Test (typed text, rules alone) | Before | After |
|---|---|---|
| `questions_unseen.jsonl`: 155 questions written after the rules, never tuned on | 61 (39%) | 142 (92%) |
| Everyday questions in it taken as ledger questions | 8 of 31 | 1 of 31 |
| `questions_heldout.jsonl` (167) | 46 | 167 |
| `voice_questions.jsonl` (1,200, `tools/make_voice_questions.py`; tuned on) | 29–62% per language | 100% |
| Corpus (638) / real-speech entries (168) | 638 / 95 | 638 / 95 |

`QuestionRulesTest` keeps the main cases and 52 everyday questions that must be left alone.
The voice set's workbook is for testers who ask the app the questions aloud, to measure speech
and understanding together.

### Choosing the language model (18 Nov 2026)

Three small models were fine-tuned the same way on identical data (`tools/finetune_compare.py`:
LoRA, 800 entry sentences and 400 questions in the training phrasings of
`tools/make_questions.py`) and scored through the app's own code (`ModelCompareTest`, report
in `core/build/model-compare-report.txt`). Tests: 140 held-out corpus prompts; the 168 spoken
entries of the speech test exactly as the speech models wrote them; 167 sentences in
question phrasings never used in training (147 ledger questions, 20 other sentences).

| | Corpus prompts | Real speech, entry right | Questions right / missed | Time per answer (PC) |
|---|---|---|---|---|
| Rules alone | — | 95 / 168 | 46 / 93 missed | — |
| + Qwen3-0.6B | 140 / 140 | 100 | 78 / 29 missed | 1.0 s |
| + Gemma 3 1B | 127 / 140 | 98 | 67 / 47 missed | 1.3 s |
| + Qwen2.5-0.5B | 127 / 140 | 101 | 46 / 93 (answers "none" to everything) | 0.8 s |

No model took an ordinary sentence for a question. Qwen3-0.6B was chosen. It names the
question's period well (60 right, 4 wrong among the questions only it caught) but often picks
the wrong topic (e.g. "did I get all my wages?" taken as "earned" instead of "still owed"), so
the app trusts only the period and reads out the whole summary for it.

### The fine-tuned model in the app

- Qwen3-0.6B, LoRA merged, converted to 8-bit LiteRT-LM with litert-torch
  (`convert_v3_to_tflite`, `--transpose_kv_cache=true`; the tied output weight is added to the
  checkpoint as `lm_head.weight` for the converter), generic model type with the training
  chat template (Qwen3's, thinking turned off, no system turn). File:
  `hisaabsaathi-qwen3-ft-q8.litertlm`, 617 MB (616,873,984 bytes), sha256
  `917030d2567e5ddee1ba53c20d95a575f9ca825aebf2a87e71363f099e2290b2`.
- In the LiteRT-LM runtime (the same C++ engine as the Android library) with the template
  stored in the file, as the app calls it (`tools/litertlm_answers.py`): 138/140 corpus
  prompts; real speech 100/168 with the rules, the same as before conversion; questions 75/167
  (59 caught by the model with the right period, 4 wrong). Median 1.1 s an answer on 4 PC cores.
- Earlier model (0.6.x): Qwen2.5-0.5B fine-tuned for entries only, 513 MB; the app deletes it
  after the update.
- Runs in its own `:llm` process; repacked weights are cached in the app's cache folder
  (about 600 MB, which Android may clear when storage is low).
- The app asks it only when the rules are unsure or found nothing, and keeps its answer only
  if it labels exactly the numbers that were said, so it cannot override a confident rule
  result or add an amount.
- Hosted at https://huggingface.co/AcxelinLabs/hisaabsaathi-model (Apache-2.0). The app
  offers it during first-time setup and under Settings → Offline packs; downloads resume
  if interrupted, and a file of the wrong size is never loaded. "Add model file from phone"
  in Settings works without internet. The link is `HISAAB_MODEL_URL` in `app/build.gradle.kts`.
- Not yet checked on a phone: LiteRT-LM loading the model, speed and memory on 3–4 GB
  devices. LiteRT-LM ships only arm64 and x86_64 libraries, so 32-bit phones run on rules alone.

## Help improve HisaabSaathi (0.8.0)

Real workers' speech is what the models need most, so the app can collect it, only from
workers who agree.

- **Asked once**, as the last setup step (or once after updating), and read aloud. Neither
  answer is preselected, and Start stays disabled until the worker chooses. Settings has the
  same switch.
- **What is kept**, per exchange (`core/.../Improve.kt`, `Sample`): what the speech model heard
  or what was typed; what the app understood (`ASK WEEK EARNED`, `ENTRY WORK 600 h8`,
  `TALK DATE`, `NONE`, `MODEL ASK …`); what it said back; how it ended (`answered`,
  `not_understood`, `saved`, `corrected` with what was kept, `rejected`, `dismissed`, and
  spoken yes/no as `confirm`); language, state, app version, speech engine; and the voice clip
  as 16 kHz WAV with its quality (`good`, `quiet`, `loud`, `silent`, `short`). There is no
  name, phone number, contact or location beyond the chosen state. Scanned bills are not kept.
- **Where it waits:** in the encrypted ledger database (table `samples`), at most 200, oldest
  dropped first. Nothing is kept while the answer is "no" or not given yet.
- **Sending** (`work/Sharing.kt`): WorkManager every 12 hours on Wi-Fi with the battery not low,
  in zips of 25 (`batch.json`, `samples.jsonl`, `audio/<id>.wav`). "Send now" uses any
  connection. Each phone is an anonymous install (random id and token from the service).
- **Turning it off** deletes what is still waiting. **"Delete what I shared"** asks the service
  to delete everything that install sent, retrying until it is online, and the next upload starts
  a new, unrelated install. "Erase all records" also deletes what is waiting.
- **The service** (`server/`) gives the phone short-lived HTTPS upload links to S3 or Azure Blob
  storage and checks each zip's size and SHA-256. FTP is not used: it sends files and passwords
  unencrypted. Build the app with the service's address:
  `./gradlew :app:assembleRelease -Phisaab.shareUrl=https://<service>`. Without it, samples
  wait on the phone.
- The old local correction log is folded in: a worker who says yes has it sent as samples (no
  clips); one who says no has it deleted.

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
process and runtime) need testing on devices. "Help improve" was tested against the real
upload service on a computer (`ShareClientLiveTest`), not yet from a phone over Wi-Fi.

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
