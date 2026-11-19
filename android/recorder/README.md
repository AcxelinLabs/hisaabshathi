# HisaabSaathi Recorder

A separate Android app for field agents to record workers asking HisaabSaathi questions, in
Hindi, Odia, Telugu, Kannada, Tamil and English. It works with no internet. Each speaker's
recordings are exported as one zip that you upload to the team's private Hugging Face dataset.

Build: `./gradlew :recorder:assembleDebug` → `recorder/build/outputs/apk/debug/recorder-debug.apk`.
The questions come from `tools/make_voice_questions.py` (300 per language), copied into
`recorder/src/main/assets/voice_prompts.jsonl`.

## For field agents

1. Write your name once on the first screen.
2. **New speaker**. Fill in:
   - the language they speak, their gender, age group and state (district is optional);
   - where you are recording: quiet room, noisy room, outdoors, street or market, worksite;
   - how they ask:
     - **Reads the sentence**: for people who read.
     - **Says it in own words**: for people who don't read. You explain the question shown,
       and they ask it the way they would.
   - which questions (all 300, 1–200 everyday, 201–300 new and hard) and how many. 25 to 50
     per speaker takes 5–10 minutes. Each new speaker starts where the last one stopped.
3. **Consent.** Read the text on the screen to them in their language, then record their
   "yes". If they say no, tap "They said no": nothing is kept.
4. **Record each question.** Tap the big button, let them speak, tap again. Play it back if
   unsure, then **Save & next**. Change the place chip if the noise changes. Skip, Back and
   Finish work at any time. Everything is saved as you go, even if the app closes.
5. **Share zip**: send it to the office computer by WhatsApp, Google Drive or email. Delete it
   on the phone only after the office has it.

No names are stored. Each speaker gets a code like `SH8RT`. Recordings stay in the app's
private storage, are not backed up to the cloud, and the app has no internet permission.

## At the office: one place for everything

The private Hugging Face dataset **`AcxelinLabs/hisaabsaathi-voices`** holds every recording.
Each zip already has the dataset's folder layout:

```
README.md                              the dataset card (handoff/VOICES_DATASET_README.md)
prompts/voice_prompts.jsonl            the questions, with what the app should answer
prompts/HisaabSaathi-voice-questions.xlsx
data/<lang>/<session>/audio/<question>__<speaker>__<take>.wav   16 kHz mono 16-bit
data/<lang>/<session>/metadata.csv     one row per recording
data/<lang>/<session>/recordings.xlsx  the same rows, for Excel
data/<lang>/<session>/session.json     agent, speaker details, phone, app version
data/<lang>/<session>/consent.wav      the speaker's spoken yes
```

A session is named `<lang>-<date>-<time>-<speaker>`, for example `hi-20261119-1336-SH8RT`.

Upload a batch of zips (PowerShell, in the folder holding them):

```
Get-ChildItem *.zip | ForEach-Object { Expand-Archive -Path $_.FullName -DestinationPath upload -Force }
hf upload AcxelinLabs/hisaabsaathi-voices upload\data data --repo-type dataset
```

Then move the zips into a `done` folder.

## Columns (metadata.csv and recordings.xlsx)

| Column | Meaning |
|---|---|
| file_name | `audio/...wav`, relative to the session folder |
| prompt_id, lang | question, e.g. `hi_017`, and language code |
| mode | `read` or `own_words` (for own words, prompt_text is what they were asked, not what they said) |
| prompt_text, english, expected | the sentence, its meaning, what the app should answer |
| speaker, gender, age, state, district | the speaker: a code, no name |
| place | quiet_room, noisy_room, outdoors, street_market, worksite |
| agent, session, device, app_version | who recorded it, on which phone, with which app version |
| recorded_at, duration_s | UTC time, length in seconds |

## Checking and scoring (from `android/`)

```
hf download AcxelinLabs/hisaabsaathi-voices --repo-type dataset --local-dir voices
python tools/voices.py check voices          # counts per language, speaker, place; broken or missing files
python tools/voices.py table voices all.csv  # every recording in one table
python tools/voices.py transcribe voices     # the app's own speech models → voice_heard.jsonl
./gradlew :core:test --tests '*VoiceQuestionsTest*'   # what the app understood: core/build/voice-questions-report.txt
```
