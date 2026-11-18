"""Fine-tunes the Hindi IndicConformer CTC branch on CPU, fast.

The subsampling block and the lower `freeze` conformer layers stay frozen, so their output
for each training clip is computed once and cached (fp16). Each training step then runs only
the upper layers and the CTC head. Augmentation: random time masks on the cached features
(SpecAugment-style) and the layers' own dropout. Model selection by WER on dev speakers
who are not in the training set.

usage: train2.py <ckpt_dir> <train.jsonl> <dev.jsonl> <out_dir> [epochs] [freeze] [lr]
"""
import json, math, os, random, sys, time
import numpy as np, soundfile as sf, torch
sys.path.insert(0, os.path.dirname(__file__))
from icmodel import ICModel
from evalset import edits, norm

ckpt, train_p, dev_p, out = sys.argv[1:5]
epochs = int(sys.argv[5]) if len(sys.argv) > 5 else 4
freeze = int(sys.argv[6]) if len(sys.argv) > 6 else 8
peak_lr = float(sys.argv[7]) if len(sys.argv) > 7 else 3e-5
BATCH_FRAMES = 2500            # ~100 s of audio after 4x subsampling (40 ms frames); 6000 ran out of memory
os.makedirs(out, exist_ok=True)
torch.set_num_threads(4); torch.manual_seed(0); random.seed(0)

m = ICModel(ckpt, "hi").eval()
enc = m.encoder

def bottom(feats, flens):
    """Subsampling + positional scaling + frozen lower layers (eval mode, no grad)."""
    x, L = enc.pre_encode(x=feats.transpose(1, 2), lengths=flens); L = L.to(torch.int64)
    x, pos = enc.pos_enc(x=x)
    pad, att = enc._create_masks(att_context_size=enc.att_context_size, padding_length=L,
                                 max_audio_length=x.size(1), offset=None, device=x.device)
    for layer in enc.layers[:freeze]:
        x = layer(x=x, att_mask=att, pos_emb=pos, pad_mask=pad)
    return x, L

def top(x, L):
    """Upper layers + CTC head on cached lower-layer output."""
    _, pos = enc.pos_enc(x=torch.zeros_like(x))
    pad, att = enc._create_masks(att_context_size=enc.att_context_size, padding_length=L,
                                 max_audio_length=x.size(1), offset=None, device=x.device)
    for layer in enc.layers[freeze:]:
        x = layer(x=x, att_mask=att, pos_emb=pos, pad_mask=pad)
    logits = m.ctc.decoder_layers(x.transpose(1, 2)).transpose(1, 2)
    sub = torch.cat([logits[..., m.lo:m.hi], logits[..., m.blank:m.blank + 1]], -1)
    return sub.log_softmax(-1)

def load_audio(rows):
    wavs = [sf.read(r["audio"], dtype="float32")[0] for r in rows]
    x = torch.zeros(len(rows), max(len(w) for w in wavs))
    for i, w in enumerate(wavs): x[i, :len(w)] = torch.from_numpy(w)
    return x, torch.tensor([len(w) for w in wavs])

# --- check the split path equals the full model --------------------------------------
with torch.no_grad():
    r0 = [json.loads(open(dev_p, encoding="utf-8").readline())]
    w, l = load_audio(r0); m.pre.featurizer.dither = 0.0
    f, fl = m.features(w, l); full, _ = m(f, fl)
    x, L = bottom(f, fl); split = top(x, L)
    print("split-vs-full max diff", float((full - split).abs().max()), flush=True)
    assert float((full - split).abs().max()) < 1e-3

# --- cache the frozen part -----------------------------------------------------------
def load_rows(p):
    rows = []
    for line in open(p, encoding="utf-8"):
        r = json.loads(line); ids = m.encode_text(r["text"])
        if ids and 0 not in ids: r["ids"] = ids; rows.append(r)
    return rows
train, dev = load_rows(train_p), load_rows(dev_p)
cache_p = f"{out}/cache_f{freeze}.pt"
if os.path.exists(cache_p):
    cache = torch.load(cache_p, weights_only=True)
else:
    cache = []; t0 = time.time()
    with torch.no_grad():
        for i in range(0, len(train), 8):
            b = train[i:i + 8]; w, l = load_audio(b)
            f, fl = m.features(w, l); x, L = bottom(f, fl)
            for j in range(len(b)): cache.append(x[j, :L[j]].to(torch.float16).clone())
            if i % 400 == 0: print(f"cached {i}/{len(train)} {(time.time() - t0) / 60:.1f} min", flush=True)
    torch.save(cache, cache_p)
print("cache ready:", len(cache), "clips,", sum(c.shape[0] for c in cache) * 0.04 / 3600, "h", flush=True)

# --- train ---------------------------------------------------------------------------
for p in m.parameters(): p.requires_grad = False
for layer in enc.layers[freeze:]:
    for p in layer.parameters(): p.requires_grad = True
for p in m.ctc.parameters(): p.requires_grad = True
trainable = [p for p in m.parameters() if p.requires_grad]
print("trainable", sum(p.numel() for p in trainable) // 1_000_000, "M params", flush=True)

def time_mask(x, L):
    for i in range(x.size(0)):
        n = int(L[i])
        for _ in range(5):
            w = random.randint(0, max(1, int(0.05 * n))); s = random.randint(0, max(0, n - w))
            x[i, s:s + w] = 0
    return x

def batches():
    idx = sorted(range(len(train)), key=lambda i: cache[i].shape[0] + random.random() * 20)
    out_, cur, mx = [], [], 0
    for i in idx:
        T = cache[i].shape[0]
        if cur and (len(cur) + 1) * max(mx, T) > BATCH_FRAMES:
            out_.append(cur); cur, mx = [], 0
        cur.append(i); mx = max(mx, T)
    if cur: out_.append(cur)
    random.shuffle(out_); return out_

def evaluate(rows):
    m.eval(); we = wn = 0
    with torch.no_grad():
        for i in range(0, len(rows), 8):
            b = rows[i:i + 8]; w, l = load_audio(b)
            f, fl = m.features(w, l); x, L = bottom(f, fl); lp = top(x, L)
            for j, r in enumerate(b):
                hyp = norm(m.decode_ids(lp[j, :L[j]].argmax(-1).tolist())); ref = norm(r["text"])
                we += edits(hyp.split(), ref.split()); wn += len(ref.split())
    return we / wn

spe = len(batches()); total = spe * epochs; warm = max(20, total // 20)
opt = torch.optim.AdamW(trainable, lr=peak_lr, weight_decay=1e-3)
sched = torch.optim.lr_scheduler.LambdaLR(opt, lambda s: min(1.0, (s + 1) / warm) * max(0.05, 0.5 * (1 + math.cos(math.pi * min(1.0, s / total)))))
ctc = torch.nn.CTCLoss(blank=256, zero_infinity=True)
best = evaluate(dev); print(f"steps/epoch {spe}; dev WER before training {best:.4f}", flush=True)
torch.save({k: v for k, v in m.state_dict().items() if k.startswith(("encoder.", "ctc."))}, f"{out}/best.pt")
log = open(f"{out}/log.jsonl", "a"); step = 0; t0 = time.time()
for ep in range(epochs):
    for b in batches():
        m.train()
        T = max(cache[i].shape[0] for i in b)
        x = torch.zeros(len(b), T, cache[0].shape[1])
        for j, i in enumerate(b): x[j, :cache[i].shape[0]] = cache[i].float()
        L = torch.tensor([cache[i].shape[0] for i in b])
        x = time_mask(x, L)
        lp = top(x, L)
        tg = torch.cat([torch.tensor(train[i]["ids"]) for i in b]); tl = torch.tensor([len(train[i]["ids"]) for i in b])
        loss = ctc(lp.transpose(0, 1), tg, L, tl)
        opt.zero_grad(); loss.backward()
        torch.nn.utils.clip_grad_norm_(trainable, 1.0); opt.step(); sched.step(); step += 1
        if step % 10 == 0:
            print(f"ep {ep} step {step}/{total} loss {loss.item():.3f} lr {sched.get_last_lr()[0]:.2e} {(time.time() - t0) / 60:.1f} min", flush=True)
        if step % max(1, spe // 2) == 0 or step == total:
            wer = evaluate(dev)
            rec = {"step": step, "epoch": ep, "dev_wer": round(wer, 4), "loss": round(loss.item(), 3), "min": round((time.time() - t0) / 60, 1)}
            print("DEV", rec, flush=True); log.write(json.dumps(rec) + "\n"); log.flush()
            if wer < best:
                best = wer
                torch.save({k: v for k, v in m.state_dict().items() if k.startswith(("encoder.", "ctc."))}, f"{out}/best.pt")
                print(f"  saved best ({best:.4f})", flush=True)
print("done, best dev WER", round(best, 4), flush=True)
