# The improvement loop: from what workers share to a better app

Workers' recordings and words stay in Azure, in India, from upload to training. Only public
things go elsewhere: the trained model and the APK (Hugging Face model repo, GitHub).

In one line: the app notes where it got things wrong (only for workers who agree), a nightly
job cleans and sorts those notes, an agent suggests fixes, automatic checks test them, and a
person approves before anything reaches phones.

```
 app (opt-in, sealed) ──▶ upload service ──▶ improve/ (ciphertext only)
                                                   │ 02:00 every night
                                                   ▼
                          nightly job: deletions → open → screen → snapshot → report
                                   │              │                    │
                                   │              ▼                    ▼
                                   │      people: labels,        Azure ML datasets
                                   │      held-back phones       hisaab-train / hisaab-eval, one version a night
                                   ▼
                     agent (weekly): reads snapshot/agent only (train split, text, no ids)
                       ├─ proposes labels ──▶ review sheet ──▶ 👤 accept
                       └─ proposes rule fixes ──▶ tests + gates + leak check ──▶ pull request ──▶ 👤 merge
                                   │
                                   ▼
                     training on Azure ML (when there's enough new data): fine-tune on a hisaab-train version
                       → evaluate on the matching hisaab-eval → gates ──▶ 👤 approve
                       ──▶ public model and APK released (Hugging Face model repo, GitHub)
```

## What runs on its own, and what needs a person

| Step | Who |
|---|---|
| Upload, check, delete on request | Automatic (upload service) |
| Deletions, opening, screening, snapshot, report, Azure ML dataset versions, tidying, team message | Automatic (nightly job) |
| Proposing labels and rule fixes | Agent |
| Tests, gates, leak check on every proposal | Automatic |
| Accepting labels, releasing held-back phones | **Person** (`review-apply`, `clear`) |
| Merging code, releasing a model or APK | **Person** |

## Snapshots

Each night makes a new snapshot (`processed/snapshots/<id>/`). Training and tests always name
the snapshot they used.
- **train / eval:** phones are split once and for all, about one in ten held out. A speaker is
  never in both, so evaluation measures new speakers.
- **gate: true** cases are ones the worker confirmed or a person labelled; they must stay
  right. **gate: false** cases are targets.
- **agent/** holds only what an agent may read: train split, text only, no phone ids.
- **Retired** the moment a phone in it asks for deletion. The next night makes a clean one.

## What can be improved, cheapest first

1. **Understanding rules.** These are words and phrasings in `QuestionWords.kt`,
   `NumberWords.kt` and `RuleBasedExtractor.kt`, and a fix can ship within days. An agent
   reads `agent/report.md` and `agent/to_label.jsonl`, changes the rules, adds **paraphrased**
   tests, and opens a pull request. The task is in [`improve-agent.md`](improve-agent.md).
2. **The language model** (Qwen3-0.6B, fine-tuned).
   - Saved and fixed entries, plus labels people accepted, give training examples.
   - Train on a `hisaab-train` version as an Azure ML GPU job. Azure ML records which dataset
     version each model came from.
   - Evaluate on the eval split, convert to LiteRT-LM, then run the gates.
3. **Speech models** (IndicConformer, Parakeet). What the app heard is only a draft
   transcript, so these need checked transcripts:
   - start from `trust: confirmed` clips;
   - have a larger cloud speech model transcribe again, and a person check a sample;
   - fine-tune and export to int8 ONNX;
   - ship only if no language's word error rate gets worse on the eval split.

## Gates (any failure stops the change)

- `./gradlew test` passes.
- `RealCasesTest` on the latest snapshot: no confirmed case breaks. It writes
  `build/real-cases-metrics.json`.
- `python -m hisaab_improve.gates <shipped.json> <candidate.json>`: no language worse, on the
  same snapshot. Model work adds its own metrics: names ending in `_wer` or `_cer` are better
  lower, the rest better higher.
- `python -m hisaab_improve.leakcheck <diff> <snapshot>`: no worker's words, and no change to
  collection, consent, sealing, storage or the pipeline.

## Where Azure AI Foundry fits

An agent in Foundry's Agent Service (or Claude Code running headless in a Container Apps Job)
can do the "propose" steps. Give it only these tools:
- a read-only view of `snapshots/<id>/agent/`;
- a way to start a job that runs the tests, gates and leak check on its change;
- opening a pull request with a token that can do nothing else.

The agent never sees voice clips, phone ids or the eval split. Where the model is hosted
decides where that text goes: prefer a deployment in an Indian region, or keep the agent's
input to the text-only view.
