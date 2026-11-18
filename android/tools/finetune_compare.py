#!/usr/bin/env python3
"""Fine-tunes one small language model for the app's two model jobs, the same way for every
candidate, and records its answers for the Kotlin comparison (core ModelCompareTest).

Jobs: (1) say what each number in a worker's sentence is (Slm.prompt); (2) when the rules do
not understand a sentence, say whether it is a ledger question, and which period and topic
(Slm.questionPrompt). Training data are identical for every model: the same generated
sentences (tools/make_corpus.py, make_questions.py TRAIN phrasings) in the same order.

Recorded for each model (tag):
- held-out corpus prompts: labelled exactly right, and time per answer;
- core/src/test/resources/slm_sp_<tag>.jsonl: answers for real speech-model output;
- core/src/test/resources/slm_q_<tag>.jsonl: answers for questions in held-out phrasings.
Run `./gradlew :core:test --tests '*ModelCompareTest*'` first (it writes the prompts) and
after (it scores).

LoRA on CPU. usage (from android/): finetune_compare.py <hf_model> <tag> <out_dir> [n_extract] [n_question]
"""
import json, random, re, sys, time
import torch
from transformers import AutoModelForCausalLM, AutoTokenizer
from peft import LoraConfig, get_peft_model
sys.path.insert(0, "tools")
import make_corpus as mc
import make_questions as mq
from finetune_slm_gold import LABELS, gold

name, tag, out_dir = sys.argv[1:4]
n_extract = int(sys.argv[4]) if len(sys.argv) > 4 else 1000
n_question = int(sys.argv[5]) if len(sys.argv) > 5 else 400
torch.manual_seed(0); torch.set_num_threads(4)


def prompt(text, numbers):
    """Must stay identical to Slm.prompt (checked below against the app's own prompts)."""
    nums = [str(n) for n in numbers]
    return ("A daily-wage worker in India said this (Hindi, Odia, Telugu, Kannada, Tamil or English):\n"
            f"\"{text}\"\n"
            f"The numbers in it, in order: {', '.join(nums)}\n"
            "Say what each number is. Use exactly one of these words for each:\n"
            "hours = hours worked\npieces = pieces or items made\nrate = pay for one piece or one hour\nwage = money earned for the work\n"
            "received = wage money the worker was actually paid\nadvance = money taken early from the employer or contractor\n"
            "expense = money the worker spent\nother = anything else\n"
            "Answer with one line per number, in the same order, like this:\n" + "\n".join(n + "=" for n in nums) + "\n")


def qprompt(text):
    """Must stay identical to Slm.questionPrompt."""
    return ("A daily-wage worker in India said this to their wage-ledger app (Hindi, Odia, Telugu, Kannada, Tamil or English):\n"
            f"\"{text.replace(chr(34), ' ')}\"\n"
            "Is it a question about their own work and money records? If yes, answer two lines:\n"
            "period=today, week, month or year (week if no time is said)\n"
            "topic=earned, advance, expense, due or all\n"
            "earned = money earned for work\nadvance = money taken early from the employer or contractor\n"
            "expense = money the worker spent\ndue = wages still owed to the worker\nall = everything\n"
            "If it is not such a question, answer: none\n")


# --- the app's prompts, and a check that ours are identical ------------------------------
corpus = [json.loads(l) for l in open("core/src/test/resources/corpus.jsonl", encoding="utf-8") if l.strip()]
corpus_texts = {c["text"] for c in corpus}
app_prompts = {r["text"]: r["prompt"] for r in map(json.loads, open("core/build/slm_prompts.jsonl", encoding="utf-8")) if r["kind"] == "entries"}
speech = [json.loads(l) for l in open("core/build/slm_speech_prompts.jsonl", encoding="utf-8") if l.strip()]
questions = [json.loads(l) for l in open("core/build/slm_question_prompts.jsonl", encoding="utf-8") if l.strip()]
test = [c for c in corpus if c["kind"] == "entries"]
for c in test[:60]:
    assert prompt(c["text"], gold(c)[0]) == app_prompts[c["text"]]
for q in questions:
    assert qprompt(q["text"]) == q["prompt"], (qprompt(q["text"]), q["prompt"])
held_out = random.Random(7).sample(test, 140)
import os
if os.environ.get("SMOKE"):  # a quick check that a model trains and answers at all
    held_out, speech, questions = held_out[:2], speech[:2], questions[:2]

# --- training data: identical for every model ---------------------------------------------
rng = random.Random(11)
def example(lang, kind, form):
    v = dict(H=rng.choice(mc.H), P=rng.choice(range(40, 400, 10)), R=rng.choice(range(2, 16)), A=rng.choice(range(100, 2100, 100)),
             W=rng.choice(range(250, 1600, 50)), E=rng.choice(range(10, 500, 10)), HR=rng.choice(range(40, 130, 10)))
    template = mc.T[lang][kind]
    order = re.findall(r"\{(\w+)\}", template)
    try:
        fill = {k: (str(n) if form == "digits" else mc.word(lang, n)) for k, n in v.items()}
    except (KeyError, IndexError):
        return None
    text = template.format(**fill)
    return text, [v[k] for k in order], [LABELS[kind][k] for k in order]

train = []
langs = list(mc.T); kinds = list(LABELS)
while len(train) < n_extract:
    lang = rng.choice(langs); kind = rng.choice(kinds)
    ex = example(lang, kind, "digits" if lang == "hi-Latn" or rng.random() < 0.4 else "words")
    if ex and ex[0] not in corpus_texts:
        train.append((prompt(ex[0], ex[1]), "\n".join(f"{n}={l}" for n, l in zip(ex[1], ex[2]))))
held_texts = {q["text"] for q in questions}
for lang, text, expected in mq.train_examples(n_question):
    assert text not in held_texts
    train.append((qprompt(text), mq.answer(expected)))
random.Random(3).shuffle(train)

# --- model --------------------------------------------------------------------------------
tok = AutoTokenizer.from_pretrained(name)
model = AutoModelForCausalLM.from_pretrained(name, torch_dtype=torch.float32)
template_kw = {"enable_thinking": False} if "qwen3" in name.lower() else {}
def head_of(p):
    return tok.apply_chat_template([{"role": "user", "content": p}], tokenize=False, add_generation_prompt=True, **template_kw)
END = "<|im_end|>" if "<|im_end|>" in head_of("x") else "<end_of_turn>"
print(name, "end token", END, "params", sum(p.numel() for p in model.parameters()) // 1_000_000, "M", flush=True)
model = get_peft_model(model, LoraConfig(r=16, lora_alpha=32, lora_dropout=0.05, task_type="CAUSAL_LM",
                                         target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "up_proj", "down_proj", "gate_proj"]))
softcap = getattr(model.config, "final_logit_softcapping", None)

def encode(p, answer):
    h = tok(head_of(p), add_special_tokens=False)["input_ids"]; a = tok(answer + END, add_special_tokens=False)["input_ids"]
    return h + a, [-100] * len(h) + a  # learn only the answer

model.gradient_checkpointing_enable(); model.enable_input_require_grads()
opt = torch.optim.AdamW([p for p in model.parameters() if p.requires_grad], lr=2e-4)
ACCUM = 4; steps = len(train) // ACCUM; t0 = time.time(); model.train()
base = model.get_base_model()
for step in range(steps):
    total = 0.0
    for p, answer in train[step * ACCUM:(step + 1) * ACCUM]:
        ids, lab = encode(p, answer)
        start = next(i for i, l in enumerate(lab) if l != -100)
        hidden = base.model(input_ids=torch.tensor([ids])).last_hidden_state[:, start - 1:-1]
        logits = base.lm_head(hidden).float()
        if softcap: logits = torch.tanh(logits / softcap) * softcap
        loss = torch.nn.functional.cross_entropy(logits.view(-1, logits.size(-1)), torch.tensor(lab[start:])) / ACCUM
        loss.backward(); total += loss.item()
    opt.step(); opt.zero_grad()
    if step % 20 == 0: print(f"step {step}/{steps} loss {total:.3f} {(time.time() - t0) / (step + 1):.1f}s/step", flush=True)
train_min = (time.time() - t0) / 60
model.save_pretrained(out_dir)

# --- answers --------------------------------------------------------------------------------
model = model.merge_and_unload().eval()
def ask(p, max_new=40):
    ids = tok(head_of(p), return_tensors="pt", add_special_tokens=False)
    with torch.no_grad():
        gen = model.generate(**ids, max_new_tokens=max_new, do_sample=False)
    return tok.decode(gen[0][ids["input_ids"].shape[1]:], skip_special_tokens=True).strip()

right = 0; times = []
for c in held_out:
    nums, labs = gold(c)
    t = time.time(); out = ask(app_prompts[c["text"]]); times.append(time.time() - t)
    right += out == "\n".join(f"{n}={l}" for n, l in zip(nums, labs))
with open(f"core/src/test/resources/slm_sp_{tag}.jsonl", "w", encoding="utf-8") as f:
    for r in speech:
        f.write(json.dumps({"id": r["id"], "output": ask(r["prompt"]) if r["prompt"] else None}, ensure_ascii=False) + "\n")
qright = 0
with open(f"core/src/test/resources/slm_q_{tag}.jsonl", "w", encoding="utf-8") as f:
    for q in questions:
        out = ask(q["prompt"], 16)
        qright += out.strip() == mq.answer(q["expect"])
        f.write(json.dumps({"text": q["text"], "output": out}, ensure_ascii=False) + "\n")
times.sort()
summary = {"tag": tag, "model": name, "train_min": round(train_min), "corpus_right": right, "corpus_n": len(held_out),
           "question_model_alone_right": qright, "question_n": len(questions), "median_s": round(times[len(times) // 2], 2)}
print("RESULT", json.dumps(summary), flush=True)
open("tools/model_compare_results.jsonl", "a").write(json.dumps(summary) + "\n")
