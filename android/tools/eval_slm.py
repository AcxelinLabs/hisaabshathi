#!/usr/bin/env python3
"""Runs the app's prompts through a Qwen2.5-Instruct GGUF on this machine and records the
raw answer for every corpus prompt, so the Kotlin tests can score them with the app's own
validator (CorpusTest.scoreRecordedModelAnswers).

The prompts come from core/build/slm_prompts.jsonl, which the Kotlin test
CorpusTest.writePromptsForModelEvaluation writes, so they are exactly what the app sends.
This is a stand-in for the phone: same model weights, but llama.cpp instead of
MediaPipe/LiteRT, so on-device results can differ.

usage (from android/): eval_slm.py <model.gguf> <tag> [max_entry_prompts]
"""
import json, random, sys, time
from llama_cpp import Llama

model, tag = sys.argv[1], sys.argv[2]
limit = int(sys.argv[3]) if len(sys.argv) > 3 else None
rows = [json.loads(l) for l in open("core/build/slm_prompts.jsonl", encoding="utf-8") if l.strip()]
entries = [r for r in rows if r["kind"] == "entries" and r["prompt"]]
if limit:
    random.seed(3); entries = random.sample(entries, limit)
talk = [r for r in rows if r["kind"] in ("chat", "unknown")]

llm = Llama(model_path=model, n_ctx=2048, n_threads=4, verbose=False)
def ask(prompt, max_tokens):
    r = llm.create_chat_completion(messages=[{"role": "user", "content": prompt}], max_tokens=max_tokens, temperature=0)
    return r["choices"][0]["message"]["content"]

t0 = time.time()
with open(f"core/src/test/resources/slm_{tag}.jsonl", "w", encoding="utf-8") as f:
    for r in entries:
        f.write(json.dumps({"task": "extract", "lang": r["lang"], "text": r["text"], "output": ask(r["prompt"], 60)}, ensure_ascii=False) + "\n"); f.flush()
    t1 = time.time()
    for r in talk:
        f.write(json.dumps({"task": "chat", "lang": r["lang"], "kind": r["kind"], "text": r["text"], "output": ask(r["prompt"], 80)}, ensure_ascii=False) + "\n"); f.flush()
print(f"{tag}: {len(entries)} extract in {t1 - t0:.0f}s ({(t1 - t0) / max(1, len(entries)):.1f}s each), {len(talk)} chat in {time.time() - t1:.0f}s")
