#!/usr/bin/env python3
"""Answers the comparison prompts with a converted .litertlm model in the LiteRT-LM runtime
(the same C++ engine as the Android library), set up as the app does: a fresh session per
sentence, the chat template stored in the file, greedy decoding.

Writes core/src/test/resources/slm_sp_<tag>.jsonl and slm_q_<tag>.jsonl, so
`./gradlew :core:test --tests '*ModelCompareTest*'` scores the converted model next to the
PyTorch one (tools/finetune_compare.py), and prints held-out corpus accuracy and speed.

usage (from android/, after ModelCompareTest.writePrompts and the corpus test):
  litertlm_answers.py <model.litertlm> <tag> [n_corpus]
"""
import json, random, sys, time
sys.path.insert(0, "tools")
import litert_lm
import finetune_slm_gold as fg
import make_questions as mq

model, tag = sys.argv[1:3]
n = int(sys.argv[3]) if len(sys.argv) > 3 else 140
engine = litert_lm.Engine(model, backend=litert_lm.Backend.CPU())
times = []


def ask(prompt, max_new=48):
    s = engine.create_session(apply_prompt_template=True, sampler_config=litert_lm.SamplerConfig(top_k=1, temperature=0.0),
                              max_output_tokens=max_new)
    t = time.time(); s.run_prefill([prompt]); resp = s.run_decode(); times.append(time.time() - t)
    del s
    return resp.texts[0].split("<|im_end|>")[0].strip()


rows = [r for r in map(json.loads, open("core/build/slm_prompts.jsonl", encoding="utf-8")) if r["kind"] == "entries" and r["prompt"]]
corpus = {c["text"]: c for c in map(json.loads, open("core/src/test/resources/corpus.jsonl", encoding="utf-8"))}
right = 0
for r in random.Random(9).sample(rows, n):
    nums, labs = fg.gold(corpus[r["text"]])
    right += ask(r["prompt"]) == "\n".join(f"{x}={l}" for x, l in zip(nums, labs))
corpus_times = sorted(times)

with open(f"core/src/test/resources/slm_sp_{tag}.jsonl", "w", encoding="utf-8") as f:
    for r in map(json.loads, open("core/build/slm_speech_prompts.jsonl", encoding="utf-8")):
        f.write(json.dumps({"id": r["id"], "output": ask(r["prompt"]) if r["prompt"] else None}, ensure_ascii=False) + "\n")
qright = 0; qn = 0
with open(f"core/src/test/resources/slm_q_{tag}.jsonl", "w", encoding="utf-8") as f:
    for q in map(json.loads, open("core/build/slm_question_prompts.jsonl", encoding="utf-8")):
        out = ask(q["prompt"], 16); qn += 1
        qright += out == mq.answer(q["expect"])
        f.write(json.dumps({"text": q["text"], "output": out}, ensure_ascii=False) + "\n")
print(f"{tag}: corpus {right}/{n} exactly right, median {corpus_times[len(corpus_times) // 2]:.2f}s; questions (model alone) {qright}/{qn}")
