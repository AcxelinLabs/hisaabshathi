# HisaabSaathi Recorder

A separate Android app for field agents to record workers asking HisaabSaathi questions, in
Hindi, Odia, Telugu, Kannada, Tamil and English. It works with no internet. Each speaker's
recordings are exported as one zip that you upload to the team's private Hugging Face dataset.

Build: `./gradlew :recorder:assembleDebug` → `recorder/build/outputs/apk/debug/recorder-debug.apk`.
The built-in questions come from `tools/make_voice_questions.py` (1,000 per language), copied into
`recorder/src/main/assets/voice_prompts.jsonl`.

## For field agents

1. Write your name once on the first screen.
2. **New speaker**, in three short steps:
   - **The speaker:** their language, gender and age. These always start empty for a new person.
   - **The place:** state, district (optional), and quiet room, noisy room, outdoors,
     street/market or worksite. Remembered, so the next speaker skips this step. Use
     "Change place" when you move.
   - **The questions:**
     - **Question list:** the built-in HisaabSaathi questions (1,000 per language), a list
       you loaded, or **Record anything**, where the speaker talks freely about their work
       or money and you may type what they said.
     - **Load a list:** "+ Load a list" takes a CSV (with a `text` column, and optionally
       `lang`, `id`, `english`, `expected`), a JSON-lines file, or a text file with one
       sentence per line, from WhatsApp, Drive or the phone.
     - **Order:** **Random** gives a mix, with the questions this phone has recorded least
       first. **In order** continues where the last speaker stopped. "Only the hard ones"
       uses the 228 hard built-in questions.
     - **How they ask:** **Reads the sentence**, or **Says it in own words** for people who
       don't read: you explain the meaning shown, they ask their way, and you may type
       what they said.
     - **How many:** 25 to 50 is plenty (5–10 minutes).
3. **Consent.** Read the text on the screen to them in their language, then record their
   "yes". If they say no, tap "They said no": nothing is kept.
4. **Record each question.** Tap the big button, let them speak, tap again. The app says
   **Sounds good**, **Quiet**, **Too loud** or **Nothing was heard**: record again if it asks.
   Then **Save & next**.
   - Skip, Back and Finish work at any time. If a recording isn't saved yet, the app asks
     whether to keep it.
   - Change the place chip if the noise changes.
   - The screen stays on, and a phone call stops the microphone but keeps the recording.
   - Everything is saved as you go.
5. **Share zip** to the office computer (WhatsApp, Google Drive, email). The home screen shows
   what isn't shared yet. Delete a session only after the office has it.

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
data/<lang>/<session>/session.json     agent, speaker details, phone, app version, question list
data/<lang>/<session>/questions.jsonl  the questions this speaker was given
data/<lang>/<session>/consent.wav      the speaker's spoken yes
```

A session is named `<lang>-<date>-<time>-<speaker>`, for example `hi-20261119-1336-SH8RT`.

Upload every zip the agents sent, with one command. Put the zips in one folder; this needs
Python with `huggingface_hub`, which `hf` already installed, and `hf auth login` done once
with a write token:

```
python voices.py upload <folder with the zips>
```

It uploads them all in one go and moves them into `<folder>\done`. Without Python:

```
Get-ChildItem *.zip | ForEach-Object { Expand-Archive -Path $_.FullName -DestinationPath upload -Force }
hf upload AcxelinLabs/hisaabsaathi-voices upload\data data --repo-type dataset
```

## Columns (metadata.csv and recordings.xlsx)

| Column | Meaning |
|---|---|
| file_name | `audio/...wav`, relative to the session folder |
| prompt_id, question_set, lang | the question (e.g. `hi_017`; `free_003` when recording anything), its list (`hisaabsaathi`, a loaded list's name, or `free`), language |
| mode | `read` or `own_words` (for own words, prompt_text is what they were asked, not what they said) |
| prompt_text, said_text | the sentence shown, and what they said if the agent typed it |
| english, expected | its meaning, and what the app should answer (built-in list) |
| speaker, gender, age, state, district | the speaker: a code, no name |
| place | quiet_room, noisy_room, outdoors, street_market, worksite |
| agent, session, device, app_version | who recorded it, on which phone, with which app version |
| recorded_at, duration_s, quality | UTC time, length in seconds, and how it sounded: good, quiet, loud or silent |

## Checking and scoring (from `android/`)

```
hf download AcxelinLabs/hisaabsaathi-voices --repo-type dataset --local-dir voices
python tools/voices.py check voices          # counts per language, speaker, place; broken or missing files
python tools/voices.py table voices all.csv  # every recording in one table
python tools/voices.py transcribe voices     # the app's own speech models → voice_heard.jsonl
./gradlew :core:test --tests '*VoiceQuestionsTest*'   # what the app understood: core/build/voice-questions-report.txt
```
