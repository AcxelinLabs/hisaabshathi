# Help improve HisaabSaathi: upload service and training pipeline

Workers who say **yes** to "Help improve HisaabSaathi?" send what they say to the app. The app
asks once, reads the question aloud, and lets them change it in Settings. This folder holds
everything after the phone:

| Part | File | Runs as |
|---|---|---|
| Upload service | `hisaab_improve/app.py` (+ `signing.py`, `storage.py`) | Always on (Azure Container App) |
| Team key and sealing | `hisaab_improve/crypto.py` | Library and `new-key` command |
| Nightly pipeline | `hisaab_improve/pipeline/` (+ `azureml.py` for dataset versions) | Scheduled job, once a night |
| Training gates | `hisaab_improve/gates.py` | Before any change to understanding or models ships |
| Leak check | `hisaab_improve/leakcheck.py` | Before any agent change leaves the private environment |

- **Setting it up on Azure, and every Azure service it uses:** [`deploy/azure.md`](deploy/azure.md).
- **How it feeds an agent and training:** [`../docs/improve-loop.md`](../docs/improve-loop.md).

## The path of one exchange

```
phone ── sealed zip ──▶ upload service ──▶ improve/  (only ciphertext)
                                                 │  every night
                                                 ▼
                         nightly job (holds the private key)
                         ingest → screen → snapshot → report ──▶ processed/
                                                 │
                       ┌─────────────────────────┼──────────────────────────┐
                       ▼                         ▼                          ▼
              Azure ML datasets         person: labels, held-back   agent: text-only view,
              (versioned, by night)     phones, approvals           gates + leak check → PR
```

1. **On the phone,** only with consent. An exchange is never kept if it contains a phone,
   Aadhaar, bank or card number, a UPI id or an email. Each sample records the version of the
   question the worker said yes to.
2. **Sealing.** Each batch is sealed to the team's public key with RSA-OAEP-3072 and
   AES-256-GCM before it leaves the phone. Without the team key in the build, the phone sends
   nothing.
3. **The upload service** checks the phone's signed token and its daily limit. It hands out a
   write-only link to one file, valid 15 minutes. When the upload arrives, it checks size,
   fingerprint and that the file is sealed. It cannot open what it stores.
4. **The nightly job**, which alone holds the private key:
   - carries out deletions first, and retires any snapshot that held a deleted phone;
   - opens each new batch and drops anything malformed, without known consent, or holding
     identifying numbers;
   - screens phones, and holds back any that look like replays, floods or junk;
   - freezes a **snapshot**. About one phone in ten is held out for evaluation. No phone may
     supply more than 300 exchanges, and the agent gets its own text-only folder;
   - registers the snapshot as versioned Azure ML datasets, writes the failure report, tidies
     old raw files, keeps an audit record, and messages the team.
5. **People decide.** They accept labels, release held-back phones, merge code, and approve
   releases. An agent can only propose.

## What protects the data

| Risk | Protection |
|---|---|
| Collecting without consent | Asked aloud, no preselected answer; nothing kept before a yes; the consent version is on every sample; a new question asks again |
| Workers' identity | No names or phone numbers; identifying numbers never kept (phone, then pipeline); random install ids; no IP addresses logged |
| Someone reading uploads in transit or in storage | HTTPS; sealed on the phone to a key only the nightly job holds; private storage with no public access and no account keys |
| A leaked upload link | Write-only, one file, 15 minutes |
| Fake or forged phones | Signed tokens; registrations rate-limited per address (cannot be dodged with X-Forwarded-For) |
| Flooding or poisoning training | 24 batches a day per phone; screening for replays, recordings shared across phones, silent clips and wrong-script text; held-back phones need a person; at most 300 exchanges per phone per snapshot; gates per language |
| Damaged or tampered uploads | Size and SHA-256 on arrival; the seal's authentication tag; unsafe zip contents ignored |
| "Delete what I shared" | Tombstone first, files at once; the next run removes everything derived, retires snapshots and archives their Azure ML dataset versions |
| Data leaving India or the team | Everything stays in one Azure subscription in Central India; keys in Key Vault; nothing copied to other services |
| An agent leaking workers' words or changing collection | Text-only view, train split only; the leak check fails on any copied sentence or 5-word run, or on any change to collection code |
| A bad model or rule change shipping | Gates: no language may get worse, confirmed cases must all stay right; release needs a person |
| Old data piling up | Raw batches deleted after `HISAAB_RAW_DAYS`; the newest 7 snapshots kept |
| Losing access | Service secret rotation without signing phones out; several team keys during a rotation |

## Run and test on your computer

```bash
cd server
python -m venv .venv && .venv/bin/pip install -r requirements-dev.txt
.venv/bin/pytest                      # 33 tests: service, S3 (moto), Azure links, sealing, pipeline, gates, leak check
HISAAB_SECRET=$(python -c "import secrets; print(secrets.token_urlsafe(48))") \
HISAAB_LOCAL_DIR=./data HISAAB_PUBLIC_URL=http://127.0.0.1:8080 HISAAB_TRUSTED_PROXY_HOPS=0 \
  .venv/bin/python -m hisaab_improve.app
```

To check the phone's real client against it, run this from `android/`. It seals with the
TEST-ONLY key:

```bash
HISAAB_SHARE_URL=http://127.0.0.1:8080 ./gradlew :core:test --tests '*ShareClientLiveTest*'
```

Then run the pipeline on what arrived:

```bash
HISAAB_LOCAL_DIR=./data HISAAB_WORK_DIR=./work HISAAB_PRIVATE_KEY_FILES=<key> \
  .venv/bin/python -m hisaab_improve.pipeline nightly
```

## The pipeline's commands

| Command | Does |
|---|---|
| `nightly` | The whole unattended run |
| `ingest`, `export` | One step at a time |
| `fetch <dir> [--agent]` | The newest snapshot to a folder; `--agent` gives only the agent's view |
| `review-sheet <out.csv>` | Sentences to label, for Excel |
| `review-apply <in.csv> --reviewer NAME` | Accepted labels become confirmed test cases |
| `clear <install> --reviewer NAME` | Release a held-back phone after checking it |

- **Stores:**
  - `HISAAB_STORAGE` = `local`, `s3` or `azure`, for what phones sent;
  - `HISAAB_WORK_DIR`, `HISAAB_WORK_BUCKET` or `HISAAB_WORK_CONTAINER`, for the pipeline's
    own private store.
- **Keys:** `HISAAB_PRIVATE_KEYS_B64` (comma-separated) or `HISAAB_PRIVATE_KEY_FILES`.
- **Optional:**
  - `HISAAB_AML_SUBSCRIPTION`, `HISAAB_AML_RESOURCE_GROUP`, `HISAAB_AML_WORKSPACE`,
    `HISAAB_AML_DATASTORE`: register each snapshot as Azure ML datasets;
  - `HISAAB_NOTIFY_WEBHOOK`;
  - `HISAAB_RAW_DAYS`.

## Service settings

| Variable | Meaning |
|---|---|
| `HISAAB_SECRET` | **Required**, 32+ characters, from the cloud's secret store. `HISAAB_SECRET_PREVIOUS`: old ones that still verify, during a change. |
| `HISAAB_STORAGE` + `HISAAB_AZURE_ACCOUNT_URL`, `HISAAB_AZURE_CONTAINER` / `HISAAB_S3_BUCKET`, `AWS_REGION` / `HISAAB_LOCAL_DIR`, `HISAAB_PUBLIC_URL` | Where uploads go |
| `HISAAB_MAX_BATCH_BYTES` (24 MB), `HISAAB_MAX_BATCHES_PER_DAY` (24), `HISAAB_UPLOAD_TTL_S` (900), `HISAAB_REGISTRATIONS_PER_HOUR` (120) | Limits |
| `HISAAB_TRUSTED_PROXY_HOPS` | Proxies in front that add to X-Forwarded-For: 1 on Azure Container Apps, 0 with none |
| `HISAAB_REQUIRE_SEALED` | 1 (default): refuse anything not sealed to the team key |

## Protocol (version 1)

| Request | Answer |
|---|---|
| `POST /v1/installs` | `{"install_id", "token"}` |
| `POST /v1/batches` (token) with `{"bytes", "sha256", "count"}` | `{"batch_id", "upload_url", "headers"}` (429 over the daily limit) |
| `PUT <upload_url>` | The sealed zip, straight to storage |
| `POST /v1/batches/{batch_id}/complete` | Checks size, SHA-256 and the seal marker, then writes the receipt |
| `DELETE /v1/installs/{install_id}` | Deletes everything the install sent; its token stops working |

Storage layout:
- **Uploads:** `incoming/<install>/<batch>.zip` (sealed), with `.json` receipts, and
  `deleted/<install>`.
- **The pipeline's own store:** see `hisaab_improve/pipeline/common.py`.

## AWS instead of Azure

The same image and commands work. The S3 backend is tested with moto. You'd set up:
- two private S3 buckets in `ap-south-1` (uploads and work), with versioning off;
- the service on ECS (App Runner closed to new customers in April 2026), with a role allowed
  `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject` and `s3:ListBucket` on the uploads bucket
  only;
- the nightly job as an ECS task started by EventBridge Scheduler, with a role on both buckets
  and the private key from Secrets Manager.

## Rules for this data

- Only from workers who said yes, under a consent version the pipeline knows.
- Worker speech and text never go into the code repository, a pull request, a public dataset
  or a model card.
- Access only for the HisaabSaathi team, which is what workers were told. Any other company's
  service that processes it (labelling, hosted models) needs a data-processing agreement and a
  check that it fits the consent.
- Before scaling up, have the consent text and this setup reviewed against India's Digital
  Personal Data Protection Act, 2023 and its rules.
