#!/usr/bin/env python3
"""LoRA fine-tune of Qwen2.5-0.5B-Instruct for the app's one model task: given a worker's
sentence and the numbers the app found in it, say what each number is.

Training sentences are made the same way as the test corpus (tools/make_corpus.py) but with
fresh random values; any sentence that is also in the corpus is left out of training, and the
model is then scored on corpus prompts exactly as the app would send them
(core/build/slm_prompts.jsonl). Because train and test share sentence patterns, the score
shows the model can learn the task, not how it does on real speech.

CPU only. usage (from android/): finetune_slm.py <out_dir> [train_examples] [eval_examples]
"""
import json, random, re, sys, time
import torch
from transformers import AutoModelForCausalLM, AutoTokenizer
from peft import LoraConfig, get_peft_model
import make_corpus as mc

out_dir = sys.argv[1]
n_train = int(sys.argv[2]) if len(sys.argv) > 2 else 1200
n_eval = int(sys.argv[3]) if len(sys.argv) > 3 else 140
torch.manual_seed(0); random.seed(11); torch.set_num_threads(4)

LABELS = {"piece": dict(H="hours", P="pieces", R="rate", A="advance"), "wage_recv": dict(W="received"), "wage_unpaid": dict(W="wage"),
          "exp_food": dict(E="expense"), "exp_travel": dict(E="expense"), "exp_health": dict(E="expense"), "adv": dict(A="advance"),
          "yday": dict(H="hours", W="wage"), "hourly": dict(H="hours", HR="rate")}

def prompt(text, numbers):
    """Must stay identical to Slm.prompt in core/Slm.kt (checked below against the app's own prompts)."""
    nums = [str(n) for n in numbers]
    return ("A daily-wage worker in India said this (Hindi, Odia, Telugu, Kannada, Tamil or English):\n"
            f"\"{text}\"\n"
            f"The numbers in it, in order: {', '.join(nums)}\n"
            "Say what each number is. Use exactly one of these words for each:\n"
            "hours = hours worked\npieces = pieces or items made\nrate = pay for one piece or one hour\nwage = money earned for the work\n"
            "received = wage money the worker was actually paid\nadvance = money taken early from the employer or contractor\n"
            "expense = money the worker spent\nother = anything else\n"
            "Answer with one line per number, in the same order, like this:\n" + "\n".join(n + "=" for n in nums) + "\n")

def example(lang, kind, form):
    v = dict(H=random.choice(mc.H), P=random.choice(range(40, 400, 10)), R=random.choice(range(2, 16)), A=random.choice(range(100, 2100, 100)),
             W=random.choice(range(250, 1600, 50)), E=random.choice(range(10, 500, 10)), HR=random.choice(range(40, 130, 10)))
    template = mc.T[lang][kind]
    order = re.findall(r"\{(\w+)\}", template)
    try:
        fill = {k: (str(n) if form == "digits" else mc.word(lang, n)) for k, n in v.items()}
    except (KeyError, IndexError):
        return None  # a value this language's table cannot spell
    text = template.format(**fill)
    return text, [v[k] for k in order], [LABELS[kind][k] for k in order]

corpus = [json.loads(l) for l in open("core/src/test/resources/corpus.jsonl", encoding="utf-8") if l.strip()]
corpus_texts = {c["text"] for c in corpus}
app_prompts = {r["text"]: r["prompt"] for r in map(json.loads, open("core/build/slm_prompts.jsonl", encoding="utf-8")) if r["kind"] == "entries"}

def gold(c):
    e = c["entries"]; k = c["template"]
    vals = {"piece": lambda: [e[0]["hours"], e[0]["pieces"], e[0]["rate"], e[1]["amount"]], "wage_recv": lambda: [e[0]["amount"]], "wage_unpaid": lambda: [e[0]["amount"]],
            "adv": lambda: [e[0]["amount"]], "yday": lambda: [e[0]["hours"], e[0]["amount"]], "hourly": lambda: [e[0]["hours"], e[0]["rate"]]}.get(k, lambda: [e[0]["amount"]])()
    order = re.findall(r"\{(\w+)\}", mc.T[c["lang"]][k])
    return vals, [LABELS[k][x] for x in order]

test = [c for c in corpus if c["kind"] == "entries"]
for c in test[:60]:  # the Python prompt must be the app's prompt, character for character
    assert prompt(c["text"], gold(c)[0]) == app_prompts[c["text"]], (prompt(c["text"], gold(c)[0]), app_prompts[c["text"]])
random.shuffle(test); test = test[:n_eval]

train = []
langs = list(mc.T); kinds = list(LABELS)
while len(train) < n_train:
    lang = random.choice(langs); kind = random.choice(kinds)
    ex = example(lang, kind, "digits" if lang == "hi-Latn" or random.random() < 0.4 else "words")
    if ex and ex[0] not in corpus_texts: train.append(ex)

name = "Qwen/Qwen2.5-0.5B-Instruct"
tok = AutoTokenizer.from_pretrained(name)
model = AutoModelForCausalLM.from_pretrained(name, torch_dtype=torch.float32)
model = get_peft_model(model, LoraConfig(r=16, lora_alpha=32, lora_dropout=0.05, task_type="CAUSAL_LM",
                                         target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "up_proj", "down_proj", "gate_proj"]))

def encode(text, numbers, labels):
    head = tok.apply_chat_template([{"role": "user", "content": prompt(text, numbers)}], tokenize=False, add_generation_prompt=True)
    answer = "\n".join(f"{n}={l}" for n, l in zip(numbers, labels)) + "<|im_end|>"
    h = tok(head, add_special_tokens=False)["input_ids"]; a = tok(answer, add_special_tokens=False)["input_ids"]
    return h + a, [-100] * len(h) + a  # learn only the answer

# Memory: one example at a time (gradients accumulated over ACCUM), activation
# checkpointing, and the 151k-word output layer evaluated only on the answer tokens.
model.gradient_checkpointing_enable(); model.enable_input_require_grads()
opt = torch.optim.AdamW([p for p in model.parameters() if p.requires_grad], lr=2e-4)
ACCUM = 4; steps = len(train) // ACCUM; t0 = time.time(); model.train()
base = model.get_base_model()
for step in range(steps):
    total = 0.0
    for ex in train[step * ACCUM:(step + 1) * ACCUM]:
        ids, lab = encode(*ex)
        start = next(i for i, l in enumerate(lab) if l != -100)
        hidden = base.model(input_ids=torch.tensor([ids])).last_hidden_state[:, start - 1:-1]
        logits = base.lm_head(hidden).float()
        loss = torch.nn.functional.cross_entropy(logits.view(-1, logits.size(-1)), torch.tensor(lab[start:])) / ACCUM
        loss.backward(); total += loss.item()
    opt.step(); opt.zero_grad()
    if step % 20 == 0: print(f"step {step}/{steps} loss {total:.3f} {(time.time() - t0) / (step + 1):.1f}s/step", flush=True)
model.save_pretrained(out_dir)

model.eval(); right = 0; by = {}
with open("core/src/test/resources/slm_qwen05ft.jsonl", "w", encoding="utf-8") as f, torch.no_grad():
    for c in test:
        nums, labs = gold(c)
        head = tok.apply_chat_template([{"role": "user", "content": app_prompts[c["text"]]}], tokenize=False, add_generation_prompt=True)
        ids = tok(head, return_tensors="pt", add_special_tokens=False)
        gen = model.generate(**ids, max_new_tokens=40, do_sample=False)
        out = tok.decode(gen[0][ids["input_ids"].shape[1]:], skip_special_tokens=True).strip()
        ok = out == "\n".join(f"{n}={l}" for n, l in zip(nums, labs))
        right += ok; a = by.setdefault(c["lang"], [0, 0]); a[0] += ok; a[1] += 1
        f.write(json.dumps({"task": "extract", "lang": c["lang"], "text": c["text"], "output": out}, ensure_ascii=False) + "\n")
print(f"fine-tuned: {right}/{len(test)} labelled exactly right", {k: f"{v[0]}/{v[1]}" for k, v in sorted(by.items())})
