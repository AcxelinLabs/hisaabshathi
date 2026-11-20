# The improvement loop: from what workers share to a better app

HisaabSaathi gets better by learning where it fails on real speech. Workers who agree share
what they say to the app. A pipeline turns that into failures and test cases. An agent proposes
fixes, automatic gates check them, and a person approves every release.

```
 app (opt-in)  ──▶  upload service  ──▶  S3 / Azure Blob  ──▶  pipeline (daily)
                    server/app.py                              ingest · report · export
                                                                     │
        ┌────────────────────────────────────────────────────────────┘
        ▼
  agent (scheduled, in the team's cloud)
    1. reads report.md, cases.jsonl, to_label.jsonl
    2. proposes: rule changes · labels · fine-tuning data
    3. runs the gates: all app tests + real cases (+ model evals)
    4. opens a pull request with the evidence
        │
        ▼
  person reviews ──▶ merge ──▶ release (APK, model on Hugging Face)
```

## What can be improved, in order of cost

1. **Understanding rules.** Cheapest, and each fix ships the same day. Sentences the app did not
   understand, or that workers fixed, show the missing words and phrasings per language
   (`core/.../QuestionWords.kt`, `NumberWords.kt`, `RuleBasedExtractor.kt`). The agent adds
   the words, adds **paraphrased** test sentences (never a worker's own words) to
   `QuestionRulesTest` or the corpus, and runs every test.
2. **The language model** (Qwen3-0.6B, fine-tuned). Saved and fixed entries give the right
   meaning of each number, which is fine-tuning data in the format of `tools/finetune_slm.py`.
   Training runs as a GPU job (SageMaker or Azure ML) on the private work folder. The result is
   converted to LiteRT-LM and evaluated with `tools/eval_slm.py` and the real cases.
3. **Speech models** (IndicConformer, Parakeet). These need checked transcripts. What the app
   heard is only a draft, so a larger speech model in the cloud transcribes the clips again,
   and a person checks a sample before fine-tuning. Fine-tuning is followed by export to ONNX
   int8 for sherpa-onnx and a word-error-rate test on speakers held out **by install**. A new
   voice pack version ships only if it beats the old one in every language.

## Gates (automatic; any failure stops the change)

- `./gradlew test` passes: the corpus, held-out and unseen question sets, the 1,000 voice
  questions per language, and the screen tests.
- `RealCasesTest` with the latest `cases.jsonl`: no confirmed case breaks, and targets
  improve. The test fails if a confirmed case breaks.
- For models: better than the shipped model on held-out real data, by language. No language may
  get worse. The model must still fit on a 3 GB phone.

## Rules for the agent

- **Never commit worker speech or text** to the repository, a pull request, an issue, or a log.
  Use paraphrases in tests. Real cases stay in the private work folder.
- **Never release.** The agent opens pull requests. Model uploads and app releases need a
  person.
- Labels the agent proposes for `to_label.jsonl` are drafts until a person accepts them.
- Run in the team's own cloud account, with read-only access to the work folder and network
  access only to the code repository and the model hub. Keep the data out of third-party
  runners (for example GitHub-hosted Actions).
- Honor deletions: always start from a fresh `pipeline ingest`, which removes deleted installs.

The task to give the agent each run is in [`improve-agent.md`](improve-agent.md). Any agent
runner that can read files, run commands and open pull requests can follow it, for example
Claude Code in headless mode in a scheduled container job.
