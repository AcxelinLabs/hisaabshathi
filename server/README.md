# Help improve HisaabSaathi: upload service and training pipeline

Workers who say **yes** to "Help improve HisaabSaathi?" (asked once in the app, read aloud,
and changeable in Settings) send what they say to the app: the voice clip, what the app heard,
what it understood and answered, and how it ended. Nothing carries their name or phone number.
This folder holds everything after the phone:

- **`hisaab_improve/app.py`**: the upload service the phone talks to. It is small and
  stateless and runs as one container on AWS, Azure or a laptop.
- **`hisaab_improve/storage.py`**: where batches are kept: Amazon S3, Azure Blob Storage, or a
  folder.
- **`hisaab_improve/pipeline.py`**: turns the batches into a failure report, test cases for
  the app, and clips for speech-model work. It also removes everything from phones that asked
  for deletion.

How these feed an agent that improves the app is in [`../docs/improve-loop.md`](../docs/improve-loop.md).

## Why not FTP

FTP sends files, and the password, unencrypted, and it needs one login shared by every phone.
Instead, the phone asks the service for a link that works once, for 15 minutes, and only for
writing one file. It then sends the zip over HTTPS straight to S3 or Azure Blob. The service
checks the file's size and SHA-256 before it counts as received. Neither the service nor the
phone holds a cloud password.

## How it works

```
phone                                   service                         storage (S3 / Azure Blob / folder)
  POST /v1/installs           ──────▶   new anonymous install + token
  POST /v1/batches  (token)   ──────▶   upload link (15 min, write-only)
  PUT  <link>  zip            ────────────────────────────────────────▶ incoming/<install>/<batch>.zip
  POST /v1/batches/<id>/complete ───▶   checks size + SHA-256 ─────────▶ incoming/<install>/<batch>.json
  DELETE /v1/installs/<install> ────▶   "delete what I shared" ────────▶ deleted/<install>; incoming/<install>/ removed
```

- **No database.** Tokens and batch ids are signed with `HISAAB_SECRET`. Changing the secret
  signs every phone out, and phones then register again on their own.
- **No IP addresses** are stored or logged. New installs are rate-limited per address in
  memory only.
- A batch is a zip of up to 25 exchanges: `batch.json`, `samples.jsonl` (fields: see
  `android/core/.../Improve.kt`), and `audio/<id>.wav` (16 kHz mono).
- The phone sends every 12 hours on Wi-Fi with the battery not low, or at once from
  "Send now". Batches are at most 50 MB.

## Run it on your computer

```bash
cd server
python -m venv .venv
.venv/bin/pip install -r requirements-dev.txt
.venv/bin/pytest                                   # 15 tests: service, S3 (moto), Azure links, pipeline
HISAAB_SECRET=$(python -c "import secrets; print(secrets.token_urlsafe(48))") \
HISAAB_LOCAL_DIR=./data .venv/bin/python -m hisaab_improve.app   # http://127.0.0.1:8080
```

Check that the phone's code and this service agree (from `android/`, with the service running):

```bash
HISAAB_SHARE_URL=http://127.0.0.1:8080 ./gradlew :core:test --tests '*ShareClientLiveTest*'
```

Phones only talk HTTPS. A plain-HTTP address works only for `localhost`, and for `10.0.2.2`
from the Android emulator.

## Settings

| Variable | Meaning |
|---|---|
| `HISAAB_SECRET` | **Required**, at least 32 characters. Keep it in the cloud's secret store. |
| `HISAAB_STORAGE` | `s3`, `azure` or `local` (default). |
| `HISAAB_S3_BUCKET`, `AWS_REGION` | For S3. Credentials come from the service's IAM role. |
| `HISAAB_AZURE_ACCOUNT_URL`, `HISAAB_AZURE_CONTAINER` | For Azure. The service's managed identity signs the links. |
| `HISAAB_AZURE_CONNECTION_STRING` | Instead of a managed identity (account key). Less safe; for testing. |
| `HISAAB_LOCAL_DIR`, `HISAAB_PUBLIC_URL` | For a folder: where files go, and the service's own address. |
| `HISAAB_MAX_BATCH_BYTES`, `HISAAB_UPLOAD_TTL_S`, `HISAAB_REGISTRATIONS_PER_HOUR` | Limits (50 MB, 900 s, 60). |

## Deploy on AWS (Mumbai, `ap-south-1`)

1. **Bucket:** create a private S3 bucket. Keep "Block all public access" on and default
   encryption on. Leave **versioning off**: with versioning, "delete what I shared" would only
   hide files. Add a lifecycle rule that expires `incoming/` after your retention period
   (suggested: 180 days).
2. **Image:** `docker build -t hisaab-improve server/`, then push it to Amazon ECR.
3. **Service:** run the image on any container service with HTTPS in front (ECS on Fargate
   behind a load balancer, or App Runner where offered). Use port 8080 and a task/instance
   role allowed `s3:PutObject`, `s3:GetObject` and `s3:DeleteObject` on `arn:aws:s3:::<bucket>/*`,
   and `s3:ListBucket` on the bucket.
4. **Settings:** `HISAAB_STORAGE=s3`, `HISAAB_S3_BUCKET=<bucket>`, `AWS_REGION=ap-south-1`, and
   `HISAAB_SECRET` from Secrets Manager.

## Deploy on Azure (Central India)

1. **Storage account:** disable "Allow Blob anonymous access" and "Allow storage account key
   access", and set minimum TLS 1.2. Leave blob versioning off and soft delete off (or very
   short), for the same reason as on AWS. Create a container, for example `improve`, and add a
   lifecycle rule for `incoming/`.
2. **Image:** push `server/` to Azure Container Registry.
3. **Service:** create an Azure Container App with external HTTPS ingress on port 8080. Turn on
   its system-assigned managed identity, and give that identity **Storage Blob Data
   Contributor** on the storage account.
4. **Settings:** `HISAAB_STORAGE=azure`,
   `HISAAB_AZURE_ACCOUNT_URL=https://<account>.blob.core.windows.net`,
   `HISAAB_AZURE_CONTAINER=improve`, and `HISAAB_SECRET` as a Container Apps secret (from Key
   Vault).

**Both clouds:** the phone sends to one address. Choose one cloud to receive uploads. To train
in the other, copy the pipeline's output there (AzCopy and the AWS CLI can both copy between S3
and Blob).

## Point the app at it

```bash
cd android
./gradlew :app:assembleRelease -Phisaab.shareUrl=https://<your service>
```

A build without `hisaab.shareUrl` asks the same question but sends nothing. What workers agree
to share then waits on the phone, at most 200 exchanges.

## The pipeline

Run it on a schedule, for example daily, as a job in the same cloud (an ECS scheduled task or an
Azure Container Apps job), with the same storage settings. Its work folder must be private: an
encrypted disk or a private bucket mount, never the code repository.

```bash
python -m hisaab_improve.pipeline all /secure/work
```

- **ingest** reads each checked batch once and verifies its SHA-256. It never uses names from
  inside a zip as paths, and checks that each clip is a WAV. Before anything else, it removes
  everything from installs under `deleted/`.
- **report** writes `report.md`: per language, how exchanges ended, recording quality, and the
  most common sentences the app did not understand, got wrong, or the worker fixed.
- **export** writes:
  - `cases.jsonl` for the app's `RealCasesTest`. With `gate: true`, the worker confirmed the
    case and it must stay right. With `gate: false`, the case is a target.
  - `to_label.jsonl`: sentences with no known answer yet.
  - `asr_manifest.jsonl`: clips with the app's own transcript, marked `confirmed` or
    `unverified`.

```bash
HISAAB_REAL_CASES=/secure/work/cases.jsonl ./gradlew :core:test --tests '*RealCasesTest*'
```

**publish-hf** (optional) copies the samples and exports to the team's **private** Hugging Face
dataset, `AcxelinLabs/hisaabsaathi-voices`. It refuses a public dataset, and it squashes the
dataset's history after each copy, so files from deleted installs do not survive in old
commits. The dataset's own README and `prompts/` are left alone.

```bash
python -m hisaab_improve.pipeline publish-hf /secure/work --repo AcxelinLabs/hisaabsaathi-voices
```

## Rules for this data

- Only from workers who said yes. They can stop at any time; turning sharing off deletes what
  is still waiting on the phone.
- "Delete what I shared" removes the install's batches at once, and the next pipeline run
  removes everything derived from them. Models are trained afresh from the current data, so
  deleted data drops out of the next model.
- Worker speech and text never go into the code repository, any public dataset, or a public
  model card. Tests read them from a private path.
- Access only for the HisaabSaathi team. That is what workers were told. Using another
  company's service to process the data (for example a labelling tool or a hosted model) needs
  a data-processing agreement, and a check that it fits what workers agreed to.
- Before scaling up, have the consent text and this setup reviewed against India's Digital
  Personal Data Protection Act, 2023 and its rules.
