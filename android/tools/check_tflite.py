#!/usr/bin/env python3
"""Runs a converted .tflite LLM (litert-torch prefill/decode signatures) with greedy
decoding on corpus prompts and reports how many are labelled exactly right. Checks that
conversion and 8-bit quantisation kept the fine-tuned behaviour. Run from android/:
  check_tflite.py <model.tflite> <hf_tokenizer_dir> [n]
"""
import json, random, re, sys
import numpy as np
from ai_edge_litert.interpreter import Interpreter
from transformers import AutoTokenizer
sys.path.insert(0, "tools")
import finetune_slm_gold as fg

model, tokdir = sys.argv[1], sys.argv[2]
n = int(sys.argv[3]) if len(sys.argv) > 3 else 40
tok = AutoTokenizer.from_pretrained(tokdir)
it = Interpreter(model_path=model, num_threads=4)
prefill = it.get_signature_runner("prefill_512"); decode = it.get_signature_runner("decode")
kv_names = [k for k in prefill.get_input_details() if k.startswith("kv_cache")]
shape = prefill.get_input_details()[kv_names[0]]["shape"]

def generate(text, max_new=40):
    ids = tok(tok.apply_chat_template([{"role": "user", "content": text}], tokenize=False, add_generation_prompt=True), add_special_tokens=False)["input_ids"]
    kv = {k: np.zeros(shape, np.float32) for k in kv_names}
    toks = np.zeros((1, 512), np.int32); toks[0, :len(ids)] = ids
    out = prefill(tokens=toks, input_pos=np.arange(512, dtype=np.int32), **kv)
    kv = {k: out[k] for k in kv_names}
    pos = len(ids); last = ids[-1]; result = []
    for _ in range(max_new):
        o = decode(tokens=np.array([[last]], np.int32), input_pos=np.array([pos - 1], np.int32), **kv)
        kv = {k: o[k] for k in kv_names}
        nxt = int(np.argmax(o["logits"][0, -1]))
        if nxt in (tok.convert_tokens_to_ids("<|im_end|>"), tok.eos_token_id): break
        result.append(nxt); last = nxt; pos += 1
    return tok.decode(result).strip()

rows = [r for r in map(json.loads, open("core/build/slm_prompts.jsonl", encoding="utf-8")) if r["kind"] == "entries" and r["prompt"]]
corpus = {c["text"]: c for c in map(json.loads, open("core/src/test/resources/corpus.jsonl", encoding="utf-8"))}
random.seed(5); rows = random.sample(rows, n)
right = 0
for r in rows:
    nums, labs = fg.gold(corpus[r["text"]])
    want = "\n".join(f"{x}={l}" for x, l in zip(nums, labs))
    got = generate(r["prompt"])
    right += got == want
    if got != want: print("MISS", r["lang"], r["text"], "|", got.replace("\n", " "), "| want", want.replace("\n", " "))
print(f"tflite: {right}/{n} labelled exactly right")
