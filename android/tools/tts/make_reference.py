#!/usr/bin/env python3
"""Writes a reference sample for TinyTtsTest: the noise used and the audio produced by
tiny_tts_ref.py for one sentence, as raw little-endian float32 files."""
import sys, numpy as np
sys.path.insert(0, __file__.rsplit("/", 1)[0])
from tiny_tts_ref import TinyTTS
folder, lang, text, out = sys.argv[1:5]
tts = TinyTTS(folder, threads=2)
draws = []
class Rng:
    def standard_normal(self, shape, dtype):
        a = np.random.default_rng(len(draws)).standard_normal(shape, dtype=dtype); draws.append(a.ravel()); return a
spk = tts.cfg["voices"]["female"]; pieces = []
for c in tts.chunks(tts.normalize(text, lang)):
    pieces.append(tts.synth_chunk(c, spk, Rng()))
np.concatenate(draws).astype("<f4").tofile(out + ".noise"); np.concatenate(pieces).astype("<f4").tofile(out + ".wav.f32")
open(out + ".txt", "w", encoding="utf-8").write(text)
print(len(draws), "chunks", sum(len(p) for p in pieces), "samples", tts.normalize(text, lang))
