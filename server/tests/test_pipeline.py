import hashlib
import io
import json
import struct
import zipfile
from pathlib import Path

import pytest

from hisaab_improve.crypto import load_private_keys, seal
from hisaab_improve.pipeline import review
from hisaab_improve.pipeline.common import read_bytes, read_json, read_jsonl, split_of
from hisaab_improve.pipeline.export import export
from hisaab_improve.pipeline.ingest import expire_raw, ingest
from hisaab_improve.pipeline.nightly import run, summary_text
from hisaab_improve.storage import LocalStorage

KEYS = load_private_keys([(Path(__file__).parent / "fixtures" / "TEST-ONLY-team-key.der.b64").read_text()])
PUBLIC = next(iter(KEYS.values())).public_key()


def install_in(split, start=0):
    n = start
    while True:
        i = "i" + hashlib.sha256(str(n).encode()).hexdigest()[:24]
        if split_of(i) == split:
            return i
        n += 1


A, B = install_in("train"), install_in("eval")


def wav(seed=0, n=16000):
    data = bytes([(seed + i) % 251 for i in range(n * 2)])
    return b"RIFF" + struct.pack("<I", 36 + len(data)) + b"WAVEfmt " + struct.pack("<IHHIIHH", 16, 1, 1, 16000, 32000, 2, 16) + b"data" + struct.pack("<I", len(data)) + data


def sample(i, outcome="answered", understood="ASK WEEK EARNED", heard=None, kept=None, audio=False, lang="hi", consent=1, quality="good"):
    return {"id": f"{i:016x}", "createdAtMs": i, "lang": lang, "source": "voice" if audio else "typed",
            "heard": heard or f"इस हफ्ते कितना कमाया {i}", "understood": understood, "reply": "", "outcome": outcome,
            "kept": kept, "hasAudio": audio, "quality": quality if audio else "", "audioSeconds": 1.0 if audio else 0.0, "consent": consent}


def put_batch(raw, install, name, samples, clips=None, sealed=True, damage=False, extra=None, key=PUBLIC):
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w") as z:
        z.writestr("samples.jsonl", "".join(json.dumps(s, ensure_ascii=False) + "\n" for s in samples))
        for k, v in (clips or {}).items():
            z.writestr(f"audio/{k}.wav", v)
        for k, v in (extra or {}).items():
            z.writestr(k, v)
    data = seal(out.getvalue(), key) if sealed else out.getvalue()
    raw.put(f"incoming/{install}/{name}.zip", data)
    sha = "0" * 64 if damage else hashlib.sha256(data).hexdigest()
    raw.put(f"incoming/{install}/{name}.json", json.dumps({"batch": name, "sha256": sha, "received_at": "2026-11-20T01:00:00+00:00"}).encode())


@pytest.fixture
def stores(tmp_path):
    return LocalStorage(tmp_path / "raw", "", None), LocalStorage(tmp_path / "work", "", None)


def basic(raw):
    put_batch(raw, A, "20261101T000000-aaaa", [
        sample(1, "saved", "ENTRY ADVANCE 300", "तीन सौ एडवांस लिया", kept="ADVANCE 300", audio=True),
        sample(2, "corrected", "ENTRY EXPENSE 80 FOOD", "सब्जी पर अस्सी", kept="EXPENSE 90 FOOD"),
        sample(3, "not_understood", "NONE", "पगार कब मिलेगा"),
        sample(4, "answered"),
    ], {f"{1:016x}": wav(1)})
    put_batch(raw, B, "20261101T000000-bbbb", [sample(5, "saved", "ENTRY WORK 600", "छह सौ मजदूरी")])


def test_nightly_end_to_end(stores):
    raw, work = stores
    basic(raw)
    r = run(raw, work, KEYS)
    assert r["ingest"]["batches"] == 2 and r["ingest"]["exchanges"] == 5 and r["ingest"]["clips"] == 1
    sid = read_bytes(work, "snapshots/latest").decode()
    train = read_jsonl(work, f"snapshots/{sid}/train/cases.jsonl")
    evals = read_jsonl(work, f"snapshots/{sid}/eval/cases.jsonl")
    assert {c["id"] for c in evals} == {f"{5:016x}"}  # phone B is held out
    assert [c["gate"] for c in train if c["id"] == f"{1:016x}"] == [True]
    assert read_jsonl(work, f"snapshots/{sid}/train/asr_manifest.jsonl")[0]["audio_filepath"] == f"audio/{A}/{1:016x}.wav"
    agent = read_jsonl(work, f"snapshots/{sid}/agent/cases.jsonl")
    assert agent and all("install" not in c and "why" not in c for c in agent) and f"{5:016x}" not in {c["id"] for c in agent}
    assert not any(k for k in work.keys(f"snapshots/{sid}/agent/") if k.endswith(".wav"))
    assert read_json(work, f"snapshots/{sid}/manifest.json", {})["installs"] == sorted([A, B])
    assert list(work.keys("audit/"))
    text = summary_text(r)
    assert "पगार" not in text and "तीन सौ" not in text and A not in text  # counts only, no words or phone ids
    assert run(raw, work, KEYS)["ingest"].get("batches", 0) == 0  # each batch once


def test_deletion_reaches_everything(stores):
    raw, work = stores
    basic(raw)
    run(raw, work, KEYS)
    old = read_bytes(work, "snapshots/latest").decode()
    raw.put(f"deleted/{A}", b"now")
    r = run(raw, work, KEYS)
    assert r["ingest"]["purged_phones"] == 1 and r["ingest"]["purged_snapshots_retired"] == 1
    assert not work.exists(f"index/{A}.jsonl") and not list(work.keys(f"audio/{A}/"))
    assert not list(work.keys(f"snapshots/{old}/"))
    new = read_bytes(work, "snapshots/latest").decode()
    assert read_json(work, f"snapshots/{new}/manifest.json", {})["installs"] == [B]
    assert "तीन सौ" not in "".join(read_bytes(work, k).decode() for k in work.keys("snapshots/") if k.endswith(".jsonl"))


def test_bad_batches_and_bad_exchanges(stores, tmp_path):
    raw, work = stores
    from cryptography.hazmat.primitives.asymmetric import rsa
    other = rsa.generate_private_key(public_exponent=65537, key_size=3072).public_key()
    put_batch(raw, A, "20261101T000000-0001", [sample(1)], damage=True)
    put_batch(raw, A, "20261101T000000-0002", [sample(2)], sealed=False)
    put_batch(raw, A, "20261101T000000-0003", [sample(3)], key=other)
    put_batch(raw, A, "20261101T000000-0004", [
        sample(4),                                            # fine
        sample(5, heard="मेरा नंबर 98765 43210 है"),          # phone number
        sample(6, heard="UPI ramesh@okicici पर भेजो"),         # UPI id
        sample(7, consent=0),                                  # no consent version
        {**sample(8), "id": "../../etc/passwd"},               # bad id
    ], extra={"../../outside.txt": "x"})
    s = ingest(raw, work, KEYS)
    assert s["batches_damaged"] == 1 and s["batches_unreadable"] == 1 and s["batches_waiting_for_key"] == 1
    assert s["exchanges"] == 1 and s["exchanges_dropped"] == 4
    assert not (tmp_path / "outside.txt").exists()
    # The batch sealed to a key we do not hold is tried again later, the others are not.
    assert ingest(raw, work, KEYS)["batches_waiting_for_key"] == 1


def test_replayed_recordings_are_held_back_until_cleared(stores):
    raw, work = stores
    clip = wav(7)
    put_batch(raw, A, "20261101T000000-0001", [sample(i, audio=True) for i in range(1, 13)], {f"{i:016x}": clip for i in range(1, 13)})
    put_batch(raw, B, "20261101T000000-0002", [sample(50)])
    m = export_after(raw, work)
    assert A not in m["installs"] and m["counts"]["phones_held_back"] == 1
    assert "the same recording sent again and again" in read_json(work, "state/quarantine.json", {})[A]["reasons"]
    with pytest.raises(ValueError):
        review.clear(work, A, " ")
    review.clear(work, A, "Lakshmi")
    assert A in export(work)["installs"]


def export_after(raw, work):
    ingest(raw, work, KEYS)
    return export(work)


def test_one_phone_cannot_dominate(stores, monkeypatch):
    raw, work = stores
    import hisaab_improve.pipeline.export as ex
    monkeypatch.setattr(ex, "PER_PHONE_CAP", 10)
    put_batch(raw, A, "20261101T000000-0001", [sample(i) for i in range(1, 41)])
    m = export_after(raw, work)
    assert m["counts"]["train_exchanges"] == 10


def test_people_review_labels(stores):
    raw, work = stores
    basic(raw)
    export_after(raw, work)
    sheet = review.sheet(work, proposals={f"{3:016x}": "ASK DAY DUE"})
    assert "पगार कब मिलेगा" in sheet and "ASK DAY DUE" in sheet
    filled = sheet.replace("ASK DAY DUE,,", "ASK DAY DUE,yes,")
    with pytest.raises(ValueError):
        review.apply(work, filled, "")
    bad = "id,lang,text,outcome,app_understood,proposed_label,accept,correct_label\n" + f"{3:016x},hi,x,y,z,,,DELETE EVERYTHING\n"
    assert review.apply(work, bad, "Lakshmi") == {"accepted": 0, "skipped": 1}
    assert review.apply(work, filled, "Lakshmi") == {"accepted": 1, "skipped": 0}
    sid = export(work)["snapshot"]
    case = [c for c in read_jsonl(work, f"snapshots/{sid}/train/cases.jsonl") if c["id"] == f"{3:016x}"][0]
    assert case == {**case, "expect": "ASK DAY DUE", "gate": True}


class FakeAzureML:
    def __init__(self):
        self.calls = []

    def __call__(self, method, url, body):
        self.calls.append((method, url.split("/data/")[1].split("?")[0], body["properties"]["isArchived"], body["properties"]["dataUri"]))
        return 200


def test_snapshots_become_azure_ml_versions_and_are_archived_when_retired(stores):
    from hisaab_improve.pipeline.azureml import AzureML
    raw, work = stores
    basic(raw)
    fake = FakeAzureML()
    aml = AzureML("sub1", "rg1", "ws1", "hisaab_processed", request=fake)
    r = run(raw, work, KEYS, aml=aml)
    sid = r["export"]["snapshot"]
    assert r["aml"]["registered"] == [f"hisaab-train:{sid}", f"hisaab-eval:{sid}"]
    assert ("PUT", f"hisaab-train/versions/{sid}", False,
            f"azureml://subscriptions/sub1/resourcegroups/rg1/workspaces/ws1/datastores/hisaab_processed/paths/snapshots/{sid}/train/") in fake.calls
    raw.put(f"deleted/{A}", b"now")
    r2 = run(raw, work, KEYS, aml=aml)
    assert r2["aml"]["archived"] == [sid]
    assert ("PUT", f"hisaab-eval/versions/{sid}", True,
            f"azureml://subscriptions/sub1/resourcegroups/rg1/workspaces/ws1/datastores/hisaab_processed/paths/snapshots/{sid}/eval/") in fake.calls
    assert "Azure ML: registered" in summary_text(r2)


def test_azure_ml_trouble_does_not_stop_the_night(stores):
    from hisaab_improve.pipeline.azureml import AzureML
    raw, work = stores
    basic(raw)

    def broken(*a):
        raise OSError("workspace unreachable")
    r = run(raw, work, KEYS, aml=AzureML("s", "r", "w", "d", request=broken))
    assert "aml_error" in r and r["export"]["snapshot"]
    assert "registration failed" in summary_text(r)


def test_old_raw_batches_are_tidied(stores):
    raw, work = stores
    put_batch(raw, A, "20260901T000000-old0", [sample(1)])
    put_batch(raw, A, "20261119T000000-new0", [sample(2)])
    ingest(raw, work, KEYS)
    put_batch(raw, B, "20260901T000000-unread", [sample(3)])  # arrived after the run: not read yet, kept
    raw.put(f"incoming/{B}/20260801T000000-later.json", b"{}")
    assert expire_raw(raw, work, 30, "20261120") == 2
    assert sorted(k.split("/")[2] for k in raw.keys("incoming/")) == [
        "20260801T000000-later.json", "20260901T000000-unread.json", "20260901T000000-unread.zip",
        "20261119T000000-new0.json", "20261119T000000-new0.zip"]


def counts_line(week, consent=2, **counts):
    return json.dumps({"week": week, "counts": counts, "consent": consent, "appVersion": "0.9.6"}) + "\n"


def test_weekly_counts_add_up_without_phone_ids_and_go_on_deletion(stores):
    raw, work = stores
    phones = [install_in("train", 100 + 50 * n) for n in range(3)] + [install_in("eval", 400)]
    for n, p in enumerate(phones[:3]):
        put_batch(raw, p, f"20261101T000000-c{n}", [], extra={"counts.jsonl": counts_line(
            "2026-W46", entries_saved=10, entries_fixed_before_saving=2, days_used=4, made_up=99)})
    # Only one phone sent this week: not shown (a single phone must not show).
    put_batch(raw, phones[3], "20261101T000000-c3", [], extra={"counts.jsonl":
        counts_line("2026-W45", entries_saved=5) + counts_line("2026-W46", consent=1, entries_saved=500) + "not json\n"})
    r = run(raw, work, KEYS)
    assert r["ingest"]["weeks_of_counts"] == 4  # the consent-1 week and the junk line are dropped
    csv = read_bytes(work, "reports/usage.csv").decode()
    md = read_bytes(work, "reports/usage.md").decode()
    assert "2026-W46,3," in csv and "2026-W45" not in csv and "made_up" not in csv
    assert "| 2026-W46 | 3 | 30 | 6 (20%) |" in md
    assert not any(p in csv + md for p in phones)
    raw.put(f"deleted/{phones[0]}", b"now")
    run(raw, work, KEYS)
    assert not work.exists(f"usage/{phones[0]}.json")
    assert "2026-W46" not in read_bytes(work, "reports/usage.csv").decode()  # now only 2 phones that week
