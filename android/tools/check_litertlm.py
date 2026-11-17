#!/usr/bin/env python3
"""Runs the .litertlm model in the LiteRT-LM runtime (the same C++ engine as the Android
library) on held-out corpus prompts, exactly as the app will: a fresh conversation per
sentence, greedy decoding, the app's prompt text. Reports accuracy and speed.

usage (from android/): check_litertlm.py <model.litertlm> [n]
"""
import json, random, sys, time
sys.path.insert(0, "tools")
import litert_lm
import finetune_slm_gold as fg

model = sys.argv[1]
n = int(sys.argv[2]) if len(sys.argv) > 2 else 60
rows = [r for r in map(json.loads, open("core/build/slm_prompts.jsonl", encoding="utf-8")) if r["kind"] == "entries" and r["prompt"]]
corpus = {c["text"]: c for c in map(json.loads, open("core/src/test/resources/corpus.jsonl", encoding="utf-8"))}
random.seed(9); rows = random.sample(rows, n)

t0 = time.time()
engine = litert_lm.Engine(model, backend=litert_lm.Backend.CPU())
print(f"load {time.time() - t0:.1f}s", flush=True)
right = 0; times = []; by = {}
for r in rows:
    nums, labs = fg.gold(corpus[r["text"]])
    want = "\n".join(f"{x}={l}" for x, l in zip(nums, labs))
    # Raw text in exactly the training format (Qwen chat template with its default system turn).
    text = ("<|im_start|>system\nYou are Qwen, created by Alibaba Cloud. You are a helpful assistant.<|im_end|>\n"
            f"<|im_start|>user\n{r['prompt']}<|im_end|>\n<|im_start|>assistant\n")
    sess = engine.create_session(apply_prompt_template=False, sampler_config=litert_lm.SamplerConfig(top_k=1, temperature=0.0), max_output_tokens=48)
    t = time.time()
    sess.run_prefill([text])
    resp = sess.run_decode()
    times.append(time.time() - t)
    got = (resp.texts[0] if hasattr(resp, "texts") else str(resp)).split("<|im_end|>")[0].strip()
    del sess
    ok = got == want; right += ok
    a = by.setdefault(r["lang"], [0, 0]); a[0] += ok; a[1] += 1
    if not ok: print("MISS", r["lang"], r["text"], "|", got.replace("\n", " "), "| want", want.replace("\n", " "), flush=True)
times.sort()
print(f"litertlm: {right}/{n} exactly right {dict(sorted((k, f'{v[0]}/{v[1]}') for k, v in by.items()))}; "
      f"median {times[len(times)//2]:.2f}s, slowest {times[-1]:.2f}s per sentence (4 CPU threads, x86)")
