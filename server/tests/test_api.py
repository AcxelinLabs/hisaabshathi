import hashlib
import io
import json
import zipfile
from urllib.parse import urlsplit

import pytest
from fastapi.testclient import TestClient

from pathlib import Path

from hisaab_improve.app import Config, create_app
from hisaab_improve.crypto import load_private_keys, seal
from hisaab_improve.signing import Signer

SECRET = "x" * 40
TEAM_KEY = next(iter(load_private_keys([(Path(__file__).parent / "fixtures" / "TEST-ONLY-team-key.der.b64").read_text()]).values()))


def batch_zip(n=2, sealed=True):
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w") as z:
        z.writestr("batch.json", json.dumps({"format": 1, "count": n}))
        z.writestr("samples.jsonl", "".join(json.dumps({"id": f"{i:016x}", "lang": "hi", "heard": "x"}) + "\n" for i in range(n)))
    return seal(out.getvalue(), TEAM_KEY.public_key()) if sealed else out.getvalue()


@pytest.fixture
def client(tmp_path):
    cfg = Config(secret=SECRET, storage="local", local_dir=str(tmp_path), public_url="http://testserver", registrations_per_hour=5,
                 max_batches_per_day=3)
    return TestClient(create_app(cfg)), tmp_path


def register(c):
    r = c.post("/v1/installs", json={"app_version": "0.8.0"})
    assert r.status_code == 201, r.text
    j = r.json()
    return j["install_id"], {"Authorization": f"Bearer {j['token']}"}


def send(c, auth, data, sha=None):
    r = c.post("/v1/batches", headers=auth, json={"bytes": len(data), "sha256": sha or hashlib.sha256(data).hexdigest(), "count": 2})
    assert r.status_code == 200, r.text
    j = r.json()
    put = c.put(urlsplit(j["upload_url"]).path, content=data, headers=j["headers"])
    return j, put


def test_whole_flow(client):
    c, root = client
    install, auth = register(c)
    data = batch_zip()
    j, put = send(c, auth, data)
    assert put.status_code == 201
    r = c.post(f"/v1/batches/{j['batch_id']}/complete", headers=auth)
    assert r.status_code == 200, r.text
    zips = list((root / "incoming" / install).glob("*.zip"))
    metas = list((root / "incoming" / install).glob("*.json"))
    assert len(zips) == 1 and zips[0].read_bytes() == data
    meta = json.loads(metas[0].read_text())
    assert meta["sha256"] == hashlib.sha256(data).hexdigest() and meta["count"] == 2 and meta["install"] == install
    # The phone retrying "complete" is fine.
    assert c.post(f"/v1/batches/{j['batch_id']}/complete", headers=auth).status_code == 200


def test_damaged_upload_is_refused_and_removed(client):
    c, root = client
    install, auth = register(c)
    data = batch_zip()
    j, _ = send(c, auth, data, sha="0" * 64)
    r = c.post(f"/v1/batches/{j['batch_id']}/complete", headers=auth)
    assert r.status_code == 422
    assert not list((root / "incoming" / install).glob("*"))


def test_not_uploaded_yet(client):
    c, _ = client
    _, auth = register(c)
    r = c.post("/v1/batches", headers=auth, json={"bytes": 10, "sha256": "a" * 64, "count": 1})
    assert c.post(f"/v1/batches/{r.json()['batch_id']}/complete", headers=auth).status_code == 409


def test_tokens_and_tickets_cannot_be_forged_or_borrowed(client):
    c, _ = client
    install, auth = register(c)
    other, other_auth = register(c)
    assert c.post("/v1/batches", headers={"Authorization": f"Bearer {install}.forged"}, json={"bytes": 1, "sha256": "a" * 64, "count": 1}).status_code == 401
    forged = Signer("y" * 40).new_install()[1]
    assert c.post("/v1/batches", headers={"Authorization": f"Bearer {forged}"}, json={"bytes": 1, "sha256": "a" * 64, "count": 1}).status_code == 401
    j, _ = send(c, auth, batch_zip())
    assert c.post(f"/v1/batches/{j['batch_id']}/complete", headers=other_auth).status_code == 404
    assert c.delete(f"/v1/installs/{install}", headers=other_auth).status_code == 403
    path = urlsplit(j["upload_url"]).path
    assert c.put(path[:-3] + "AAA", content=b"x").status_code == 403


def test_more_than_promised_is_refused(client):
    c, _ = client
    _, auth = register(c)
    data = batch_zip()
    r = c.post("/v1/batches", headers=auth, json={"bytes": 10, "sha256": "a" * 64, "count": 1})
    assert c.put(urlsplit(r.json()["upload_url"]).path, content=data).status_code == 413


def test_limits(client):
    c, _ = client
    _, auth = register(c)
    assert c.post("/v1/batches", headers=auth, json={"bytes": 10**9, "sha256": "a" * 64, "count": 1}).status_code == 413
    assert c.post("/v1/batches", headers=auth, json={"bytes": 10, "sha256": "nothex", "count": 1}).status_code == 422
    for _ in range(4):
        c.post("/v1/installs", json={})
    assert c.post("/v1/installs", json={}).status_code == 429


def test_delete_everything_an_install_sent(client):
    c, root = client
    install, auth = register(c)
    for _ in range(2):
        j, _ = send(c, auth, batch_zip())
        c.post(f"/v1/batches/{j['batch_id']}/complete", headers=auth)
    r = c.delete(f"/v1/installs/{install}", headers=auth)
    assert r.status_code == 202 and r.json()["deleted"] == 4
    assert not list((root / "incoming" / install).glob("*"))
    assert (root / "deleted" / install).is_file()
    # The token is dead; a repeated delete is refused the same way (the phone treats it as done).
    assert c.post("/v1/batches", headers=auth, json={"bytes": 1, "sha256": "a" * 64, "count": 1}).status_code == 401
    assert c.delete(f"/v1/installs/{install}", headers=auth).status_code == 401


def test_short_secret_is_refused(tmp_path):
    with pytest.raises(ValueError):
        create_app(Config(secret="short", local_dir=str(tmp_path)))


def test_only_sealed_batches_are_kept(client):
    c, root = client
    install, auth = register(c)
    j, _ = send(c, auth, batch_zip(sealed=False))
    r = c.post(f"/v1/batches/{j['batch_id']}/complete", headers=auth)
    assert r.status_code == 422 and "sealed" in r.text
    assert not list((root / "incoming" / install).glob("*"))


def test_daily_limit_per_phone(client):
    c, _ = client
    _, auth = register(c)
    for _ in range(3):
        send(c, auth, batch_zip())
    r = c.post("/v1/batches", headers=auth, json={"bytes": 10, "sha256": "a" * 64, "count": 1})
    assert r.status_code == 429


def test_secret_can_change_without_signing_phones_out(tmp_path):
    old = TestClient(create_app(Config(secret="o" * 40, local_dir=str(tmp_path))))
    token = old.post("/v1/installs", json={}).json()["token"]
    new = TestClient(create_app(Config(secret="n" * 40, secret_previous=["o" * 40], local_dir=str(tmp_path))))
    r = new.post("/v1/batches", headers={"Authorization": f"Bearer {token}"}, json={"bytes": 10, "sha256": "a" * 64, "count": 1})
    assert r.status_code == 200
    gone = TestClient(create_app(Config(secret="n" * 40, local_dir=str(tmp_path))))
    assert gone.post("/v1/batches", headers={"Authorization": f"Bearer {token}"}, json={"bytes": 10, "sha256": "a" * 64, "count": 1}).status_code == 401


def test_rate_limit_cannot_be_dodged_with_forwarded_for(client):
    c, _ = client
    # Our proxy appends the real address last; a client may put anything before it.
    for i in range(5):
        assert c.post("/v1/installs", json={}, headers={"X-Forwarded-For": f"10.0.0.{i}, 203.0.113.7"}).status_code == 201
    assert c.post("/v1/installs", json={}, headers={"X-Forwarded-For": "10.9.9.9, 203.0.113.7"}).status_code == 429
    assert c.post("/v1/installs", json={}, headers={"X-Forwarded-For": "203.0.113.8"}).status_code == 201


def test_large_or_unsized_requests_are_refused(client):
    c, _ = client
    assert c.post("/v1/installs", content=b"{" + b" " * 9000 + b"}", headers={"Content-Type": "application/json"}).status_code == 413


def test_audit_log_has_no_addresses_or_tokens(client, caplog):
    import logging
    c, _ = client
    caplog.set_level(logging.INFO, logger="hisaab.audit")
    install, auth = register(c)
    j, _ = send(c, auth, batch_zip())
    c.post(f"/v1/batches/{j['batch_id']}/complete", headers=auth)
    text = caplog.text
    assert '"event":"registered"' in text and '"event":"batch_received"' in text
    assert "testclient" not in text and auth["Authorization"].split()[1] not in text
