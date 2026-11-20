import hashlib
import io
import json
import struct
import zipfile

import pytest

from hisaab_improve.pipeline import export, ingest, publish_hf, report, read_batch
from hisaab_improve.storage import LocalStorage

A = "i" + "a" * 24
B = "i" + "b" * 24


def wav(n=16000):
    data = b"\x10\x00" * n
    return b"RIFF" + struct.pack("<I", 36 + len(data)) + b"WAVEfmt " + struct.pack("<IHHIIHH", 16, 1, 1, 16000, 32000, 2, 16) + b"data" + struct.pack("<I", len(data)) + data


def sample(i, outcome, understood, heard, kept=None, audio=False, lang="hi"):
    return {"id": f"{i:016x}", "createdAtMs": i, "lang": lang, "source": "voice" if audio else "typed", "heard": heard,
            "understood": understood, "reply": "", "outcome": outcome, "kept": kept, "hasAudio": audio,
            "quality": "good" if audio else "", "audioSeconds": 1.0 if audio else 0.0, "engine": "pack" if audio else ""}


def put_batch(storage, install, name, samples, clips, sha=None, extra=None):
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w") as z:
        z.writestr("samples.jsonl", "".join(json.dumps(s, ensure_ascii=False) + "\n" for s in samples))
        for k, v in clips.items():
            z.writestr(f"audio/{k}.wav", v)
        for k, v in (extra or {}).items():
            z.writestr(k, v)
    data = out.getvalue()
    storage.put(f"incoming/{install}/{name}.zip", data)
    storage.put(f"incoming/{install}/{name}.json", json.dumps({"batch": name, "sha256": sha or hashlib.sha256(data).hexdigest()}).encode())


def setup(tmp_path):
    storage = LocalStorage(tmp_path / "store", "http://x", None)
    s1 = [
        sample(1, "saved", "ENTRY ADVANCE 300", "तीन सौ एडवांस लिया", kept="ADVANCE 300", audio=True),
        sample(2, "corrected", "ENTRY EXPENSE 80 FOOD", "सब्जी पर अस्सी", kept="EXPENSE 90 FOOD"),
        sample(3, "not_understood", "NONE", "पगार कब मिलेगा"),
        sample(4, "not_understood", "NONE", "पगार कब मिलेगा?"),
        sample(5, "answered", "ASK WEEK EARNED", "इस हफ्ते कितना कमाया"),
    ]
    put_batch(storage, A, "b1", s1, {f"{1:016x}": wav()})
    return storage


def test_ingest_report_export(tmp_path):
    storage = setup(tmp_path)
    work = tmp_path / "work"
    assert ingest(storage, work) == {"purged": 0, "samples": 5, "clips": 1, "batches": 1}
    assert ingest(storage, work)["samples"] == 0  # each batch once
    text = report(work)
    assert "2× “पगार कब मिलेगा”" in text and "kept `EXPENSE 90 FOOD`" in text
    assert export(work) == {"cases": 3, "to_label": 2, "clips": 1}
    cases = [json.loads(l) for l in open(work / "cases.jsonl", encoding="utf-8")]
    assert [c["gate"] for c in cases] == [True, False, False]
    assert cases[1]["expect"] == "ENTRY EXPENSE 90 FOOD"
    asr = [json.loads(l) for l in open(work / "asr_manifest.jsonl", encoding="utf-8")]
    assert asr[0]["trust"] == "confirmed" and asr[0]["audio_filepath"] == f"samples/hi/{A}/{1:016x}.wav"


def test_deleted_installs_are_purged_everywhere(tmp_path):
    storage = setup(tmp_path)
    put_batch(storage, B, "b2", [sample(9, "saved", "ENTRY WORK 600", "छह सौ मजदूरी")], {})
    work = tmp_path / "work"
    ingest(storage, work)
    assert len(list(work.glob("samples/*/*/*.json"))) == 6
    storage.put(f"deleted/{A}", b"now")
    stats = ingest(storage, work)
    assert stats["purged"] == 5
    assert [p.parent.name for p in work.glob("samples/*/*/*.json")] == [B]
    export(work)
    assert "तीन सौ" not in (work / "cases.jsonl").read_text(encoding="utf-8")


def test_damaged_and_hostile_batches(tmp_path):
    storage = LocalStorage(tmp_path / "store", "http://x", None)
    put_batch(storage, A, "bad", [sample(1, "saved", "ENTRY WORK 6", "x")], {}, sha="0" * 64)
    evil = sample(2, "saved", "ENTRY WORK 6", "y")
    evil_bad_id = {**sample(3, "saved", "ENTRY WORK 6", "z"), "id": "../../etc/passwd"}
    put_batch(storage, A, "evil", [evil, evil_bad_id], {}, extra={"../../outside.txt": "x", "audio/../../x.wav": "y"})
    work = tmp_path / "work"
    stats = ingest(storage, work)
    assert stats["damaged"] == 1 and stats["samples"] == 1
    assert not (tmp_path / "outside.txt").exists()
    assert [p.name for p in work.rglob("*.json")] == [f"{2:016x}.json"]


def test_clip_must_be_a_wav():
    out = io.BytesIO()
    s = sample(1, "saved", "ENTRY WORK 6", "x", audio=True)
    with zipfile.ZipFile(out, "w") as z:
        z.writestr("samples.jsonl", json.dumps(s) + "\n")
        z.writestr(f"audio/{s['id']}.wav", b"not a wav at all, honest" * 4)
    samples, clips = read_batch(out.getvalue())
    assert len(samples) == 1 and clips == {}


class FakeHf:
    def __init__(self, private):
        self.private, self.calls = private, []

    def dataset_info(self, repo):
        return type("Info", (), {"private": self.private})()

    def upload_folder(self, **kw):
        self.calls.append(("upload", kw))

    def super_squash_history(self, **kw):
        self.calls.append(("squash", kw))


def test_publish_only_to_a_private_dataset_and_leave_no_history(tmp_path):
    with pytest.raises(SystemExit):
        publish_hf(tmp_path, "org/public", FakeHf(private=False))
    hf = FakeHf(private=True)
    publish_hf(tmp_path, "org/private", hf)
    (kind, up), (kind2, _) = hf.calls
    assert kind == "upload" and kind2 == "squash"
    assert up["delete_patterns"] == up["allow_patterns"] and "prompts/*" not in up["delete_patterns"]
