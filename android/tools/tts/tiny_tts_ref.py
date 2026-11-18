#!/usr/bin/env python3
"""Reference player for Indic Tiny TTS voices, written from scratch for HisaabSaathi.

Uses only a voice's data files (config.json and the three ONNX graphs), never the code
shipped with the model. Mirrors the published pipeline: characters -> text encoder ->
durations -> length regulation -> 4-step Euler flow -> mel -> Vocos vocoder -> ISTFT.
This is the spec the Kotlin version in the app follows.

usage: tiny_tts_ref.py <voice_dir> <lang> <female|male> "<text>" <out.wav>"""
import json, re, sys, unicodedata, wave
import numpy as np, onnxruntime as ort
sys.path.insert(0, __file__.rsplit("/", 1)[0])
from numspell import spell_numbers

class TinyTTS:
    def __init__(self, folder, threads=4):
        self.cfg = json.load(open(f"{folder}/config.json", encoding="utf-8"))
        v = self.cfg["vocab"]; syms = v["symbols"]
        self.id = {s[2:]: i for i, s in enumerate(syms) if s.startswith("c:")}
        self.sr = int(self.cfg["sample_rate"])
        syn = self.cfg["synthesis"]; self.steps = int(syn["n_timesteps"]); self.temp = float(syn["temperature"])
        self.pause = float(syn.get("sentence_pause_s", 0.15)); self.ls = syn["length_scale"]
        self.mean, self.std = (np.float32(x) for x in self.cfg["mel_stats"])
        h = self.cfg["vocoder"]["head"]; self.n_fft, self.hop = int(h["n_fft"]), int(h["hop_length"])
        self.ids = self.cfg["ids"]
        so = ort.SessionOptions(); so.intra_op_num_threads = threads; so.inter_op_num_threads = 1
        self.s = {g: ort.InferenceSession(f"{folder}/onnx/{g}.onnx", so, providers=["CPUExecutionProvider"])
                  for g in ("text_encoder", "flow_decoder", "vocoder")}

    def normalize(self, text, lang):
        t = unicodedata.normalize("NFC", spell_numbers(text, lang))
        out = []
        for ch in t:
            if ch in self.id: out.append(ch)
            elif ch in "\n\t": out.append(" ")
            elif unicodedata.category(ch)[0] == "P": out.append("," if "," in self.id else " ")
            else: out.append(" ")
        return re.sub(r" +", " ", "".join(out)).strip()

    def chunks(self, norm):
        parts = re.split(r"(?<=[.?!।|])\s+", norm)
        return [p.strip() for p in parts if any(c.isalpha() for c in p)]

    def synth_chunk(self, chunk, spk, rng):
        ids = [self.id[c] for c in chunk]
        x = [0] * (2 * len(ids) + 1); x[1::2] = ids
        i64 = np.int64
        mu_x, logw, cond = self.s["text_encoder"].run(None, {
            "tokens": np.asarray([x], i64), "token_lengths": np.asarray([len(x)], i64),
            "language": np.asarray([self.ids["language"]], i64), "speaker": np.asarray([self.ids["speakers"][spk]], i64),
            "style": np.asarray([self.ids["styles"]["read"]], i64)})
        w = np.ceil(np.exp(logw[0, 0].astype(np.float32))).astype(np.float32) * np.float32(self.ls.get(spk, 1.0))
        y_len = int(max(np.float32(np.sum(w, dtype=np.float64)), 1.0)); y_pad = -(-y_len // 2) * 2
        cum = np.cumsum(w.astype(np.float64)).astype(np.float32); j = np.arange(y_pad, dtype=np.float32)
        tok = np.searchsorted(cum, j, side="right"); valid = (tok < len(cum)) & (j < y_len)
        mu = mu_x[0]; mu_y = np.where(valid[None, :], mu[:, np.minimum(tok, len(cum) - 1)], 0).astype(np.float32)
        mask = valid.astype(np.float32).reshape(1, 1, -1)
        z = rng.standard_normal((1, mu_y.shape[0], y_pad), dtype=np.float32) * np.float32(self.temp)
        dt = np.float32(1.0 / self.steps); t = np.float32(0.0); xx = z
        for _ in range(self.steps):
            v = self.s["flow_decoder"].run(None, {"x": xx, "mask": mask, "mu": mu_y[None], "t": np.asarray([t], np.float32), "cond": cond})[0]
            xx = xx + dt * v; t = t + dt
        mel = (xx[:, :, :y_len] * self.std + self.mean).astype(np.float32)
        re_, im = self.s["vocoder"].run(None, {"mel": mel})
        return np.clip(istft(re_[0], im[0], self.n_fft, self.hop), -1, 1).astype(np.float32)

    def synthesize(self, text, lang, voice="female", seed=0):
        spk = self.cfg["voices"][voice]; rng = np.random.default_rng(seed)
        pieces = []
        for c in self.chunks(self.normalize(text, lang)):
            if pieces: pieces.append(np.zeros(int(self.pause * self.sr), np.float32))
            pieces.append(self.synth_chunk(c, spk, rng))
        return np.concatenate(pieces) if pieces else np.zeros(0, np.float32)

def istft(real, imag, n_fft, hop):
    spec = real.astype(np.float64) + 1j * imag.astype(np.float64); T = spec.shape[1]
    win = 0.5 - 0.5 * np.cos(2 * np.pi * np.arange(n_fft) / n_fft)
    frames = np.fft.irfft(spec, n=n_fft, axis=0).T * win
    total = n_fft + hop * (T - 1); y = np.zeros(total); env = np.zeros(total)
    for i in range(T):
        y[i * hop:i * hop + n_fft] += frames[i]; env[i * hop:i * hop + n_fft] += win * win
    pad = n_fft // 2; y, env = y[pad:total - pad], env[pad:total - pad]
    return (y / np.maximum(env, 1e-11)).astype(np.float32)

def save_wav(wav, sr, path):
    with wave.open(path, "wb") as f:
        f.setnchannels(1); f.setsampwidth(2); f.setframerate(sr); f.writeframes((np.clip(wav, -1, 1) * 32767).astype("<i2").tobytes())

if __name__ == "__main__":
    folder, lang, voice, text, out = sys.argv[1:6]
    tts = TinyTTS(folder); save_wav(tts.synthesize(text, lang, voice), tts.sr, out)
