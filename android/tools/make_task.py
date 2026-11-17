#!/usr/bin/env python3
"""Packs a converted .tflite LLM into a MediaPipe LLM Inference .task bundle.

A .task file is an uncompressed zip of TF_LITE_PREFILL_DECODE (the model),
TOKENIZER_MODEL (SentencePiece form of the tokenizer) and METADATA (stop tokens and chat
template). Fine-tuning does not change the tokenizer or template, so the last two are taken
unchanged from Google's published Qwen2.5-0.5B-Instruct bundle. Data is 64-byte aligned so
the runtime can memory-map the model.

usage: make_task.py <model.tflite> <TOKENIZER_MODEL> <METADATA> <out.task>
"""
import sys, zipfile

tflite, tokenizer, metadata, out = sys.argv[1:5]

def add_aligned(zf, name, path, align=64):
    info = zipfile.ZipInfo(name, date_time=(2026, 11, 17, 0, 0, 0))
    info.compress_type = zipfile.ZIP_STORED
    header_end = zf.fp.tell() + 30 + len(name.encode())
    pad = (-(header_end + 4)) % align  # 4 bytes of extra-field header
    info.extra = b"\xff\xff" + pad.to_bytes(2, "little") + b"\x00" * pad
    with open(path, "rb") as src:
        zf.writestr(info, src.read())  # files are well under 4 GB: no zip64 fields to shift the data

with zipfile.ZipFile(out, "w", allowZip64=True) as zf:
    add_aligned(zf, "TF_LITE_PREFILL_DECODE", tflite)
    add_aligned(zf, "TOKENIZER_MODEL", tokenizer)
    add_aligned(zf, "METADATA", metadata)

with zipfile.ZipFile(out) as zf:
    for i in zf.infolist():
        with open(out, "rb") as f:
            f.seek(i.header_offset + 26); n, e = int.from_bytes(f.read(2), "little"), int.from_bytes(f.read(2), "little")
        data = i.header_offset + 30 + n + e
        print(f"{i.filename}: {i.file_size} bytes at {data} (aligned: {data % 64 == 0})")
