# Task: improve HisaabSaathi's understanding from the newest snapshot

You are improving HisaabSaathi, a voice-first wage ledger for daily-wage workers in Hindi,
Odia, Telugu, Kannada, Tamil and English. Workers who agreed shared what they said to the app.
You get **only** the agent view of the newest snapshot: train split, text only, no phone ids.
The repository is checked out in the current directory.

## Steps

1. From `server/`, run `python -m hisaab_improve.pipeline fetch "$AGENT_DIR" --agent`. Read
   `$AGENT_DIR/report.md`.
2. From `android/`, run `HISAAB_REAL_CASES="$AGENT_DIR/cases.jsonl" ./gradlew test`. Then read
   `android/core/build/real-cases-misses.tsv`, and save
   `android/core/build/real-cases-metrics.json` as `before.json`.
3. Pick the **three** most frequent failure patterns that rules can fix: missing words, local
   or mixed-English phrasings, number forms, mishearings that repeat. For each:
   - change the rules in `android/core/src/main/kotlin/com/acxelin/hisaabsaathi/core/`
     (`QuestionWords.kt`, `NumberWords.kt`, `RuleBasedExtractor.kt`, `SmallTalk.kt`);
   - add **paraphrased** test sentences that show the pattern. Never use a worker's sentence:
     change the numbers, names and word order, and keep only the pattern;
   - keep everyday sentences from turning into ledger entries (`QuestionRulesTest`).
4. Run all tests again with `HISAAB_REAL_CASES`. Every test must pass.
5. Run `python -m hisaab_improve.gates before.json android/core/build/real-cases-metrics.json`.
   It must say PASS.
6. Run `git diff origin/main... > change.diff`, then
   `python -m hisaab_improve.leakcheck change.diff "$AGENT_DIR"`. It must find nothing. If it
   names a file, rewrite that test sentence further from the original.
7. For up to 50 lines of `to_label.jsonl`, propose a label in the cases format:
   `ASK <PERIOD> <TOPIC>`, `ENTRY ...`, `TALK ...` or `NONE`. Write them as
   `{"id", "label"}` lines to `$AGENT_DIR/proposed_labels.jsonl` for a person to check with
   `review-sheet`. Do not put them in the repository.
8. Open one pull request with the rule changes and paraphrased tests. In the description, give:
   - the patterns fixed, described rather than quoted;
   - the gate output;
   - the test results.

## Never

- Copy any worker's sentence, transcript or identifier into the repository, a pull request, a
  commit message, or any log that leaves the private environment.
- Push to `main`, merge, release, or upload a model.
- Weaken, skip or delete a test to make a change pass.
- Change what the app collects, how it asks, how data is sealed, sent or stored, or the
  pipeline. The leak check refuses those files; they need a person.
