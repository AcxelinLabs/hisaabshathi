# Updating the smart model on workers' phones

Workers keep the app they installed, often for months. A retrained model reaches them without a
new app: the phone checks a small signed list on Hugging Face about once a week, and switches
over by itself.

## What the phone does

About once a week, on Wi-Fi, with the battery and storage not low (`work/ModelUpdates.kt`):

1. **It fetches `models.json` and `models.json.sig`** from
   `AcxelinLabs/hisaabsaathi-model`. It uses them only if the signature matches the team's model
   key built into the app. An altered or unsigned list is ignored.
2. **It picks the newest version that is newer than the one in use** and that this app version can
   read (`minAppVersion`). The file must be pinned to a commit of the team's repo. An old list
   can never move a phone backwards.
3. **If the phone never downloaded the smart model,** it just remembers the new version: that is
   what it will download if the worker ever turns the model on.
4. **Otherwise it downloads the new model next to the old one.** This happens only if there is
   room for it plus 300 MB to spare, and it resumes if interrupted. Mobile data is never used.
5. **It checks the file's size and SHA-256 against the list.** If they don't match, the file is
   deleted.
6. **It loads the new model and asks it a known sentence.** If the phone is too busy, it tries
   again later. If the model fails three times on that phone, that version is skipped there.
7. **It switches to the new model,** then **deletes the old model** and anything else left in the
   model folder: older versions and half-finished downloads.

If anything goes wrong, the phone keeps the model it had. The worker is never asked anything.

## Publishing a new model (team)

Do this once: make the team's model key and keep the private half safe, outside this repository.

```bash
python tools/models/model_manifest.py new-key --out C:\keys\hisaab-model-key.pem
```

Put the public key it prints into `android/gradle.properties` as `hisaab.modelKey=…` (it's public;
committing it is fine). Only builds with this key check for new models.

For each new model:

1. **Test it** against the corpus (`tools/finetune_compare.py`, `tools/litertlm_answers.py`).
2. **Upload it to Hugging Face under a new name.** Never overwrite a published model file.
   ```bash
   hf upload AcxelinLabs/hisaabsaathi-model hisaabsaathi-qwen3-ft-v2-q8.litertlm hisaabsaathi-qwen3-ft-v2-q8.litertlm --commit-message "Model v2"
   ```
3. **Sign the list.** The tool reads the size and SHA-256 from your copy, and finds the commit that
   holds the upload:
   ```bash
   python tools/models/model_manifest.py sign --key C:\keys\hisaab-model-key.pem --file hisaabsaathi-qwen3-ft-v2-q8.litertlm --version 2 --model-file hisaabsaathi-qwen3-ft-v2-q8.litertlm
   ```
4. **Upload `models.json` and `models.json.sig`** to the same repo. Phones pick it up within about
   a week.

**Rules:**
- Version numbers only go up.
- **To roll back,** sign the previous file again under a *higher* number. Phones download it
  again: the old file was deleted when they switched.
- **If a new model answers in a different format,** give it `--min-app <versionCode>` of the
  first app that can read it.

## The model built into the app

Each app build names one model as its starting point (`SlmModel.builtIn` in
`models/Packs.kt`). Today that's v1, `hisaabsaathi-qwen3-ft-q8.litertlm`, pinned to commit
`4eea332d…`. A phone uses whichever is newer: the built-in model, or one it switched to.
