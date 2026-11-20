# Task: improve HisaabSaathi's understanding from this week's shared data

You are improving HisaabSaathi, a voice-first wage ledger for daily-wage workers in Hindi,
Odia, Telugu, Kannada, Tamil and English. Workers who agreed shared what they said to the app.
The private work folder is in `$HISAAB_WORK`. The repository is checked out in the current
directory.

## Steps

1. Run `python -m hisaab_improve.pipeline all "$HISAAB_WORK"` from `server/`. Then read
   `$HISAAB_WORK/report.md`.
2. Run the app's tests with the real cases:
   `HISAAB_REAL_CASES="$HISAAB_WORK/cases.jsonl" ./gradlew test` from `android/`. Then read
   `android/core/build/real-cases-misses.tsv`.
3. Pick the **three** most frequent failure patterns that rules can fix: missing words,
   local or mixed-English phrasings, number forms, mishearings that repeat. For each:
   - change the rules in `android/core/src/main/kotlin/com/acxelin/hisaabsaathi/core/`;
   - add **paraphrased** test sentences that show the pattern. Never use a worker's sentence:
     change the numbers, names and word order, and keep only the pattern;
   - make sure the change does not turn everyday sentences into ledger entries
     (`QuestionRulesTest` keeps such sentences).
4. Run all tests again with `HISAAB_REAL_CASES`. Every test must pass, and no confirmed real
   case may break. Count how many targets now pass compared with step 2.
5. For up to 50 lines of `to_label.jsonl`, propose a label in the cases format
   (`ASK <PERIOD> <TOPIC>`, `ENTRY ...`, `TALK ...`, `NONE`). Write them to
   `$HISAAB_WORK/proposed_labels.jsonl` for a person to check. Do not put them in the
   repository.
6. Open one pull request with the rule changes and the paraphrased tests. In the description,
   give the patterns fixed (described, not quoted), the before and after counts per language,
   and the test results.

## Never

- Copy any worker's sentence, transcript, audio or identifier into the repository, the pull
  request, a commit message, or any log that leaves the work folder.
- Push to the main branch, merge, publish a release, or upload a model.
- Weaken or skip a test to make a change pass.
- Change the consent text, what the app collects, or where it sends it. Those need a person.
