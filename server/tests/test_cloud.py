"""The S3 backend against moto (a stand-in for S3), and the Azure upload link's form."""
import hashlib
import io
import json
import zipfile

import boto3
import pytest
import requests
from fastapi.testclient import TestClient
from moto import mock_aws

from hisaab_improve.app import Config, create_app
from hisaab_improve.storage import AzureStorage, S3Storage


@pytest.fixture
def s3():
    with mock_aws():
        client = boto3.client("s3", region_name="ap-south-1")
        client.create_bucket(Bucket="hisaab-improve", CreateBucketConfiguration={"LocationConstraint": "ap-south-1"})
        yield client


def test_s3_whole_flow(s3):
    storage = S3Storage("hisaab-improve", client=s3)
    c = TestClient(create_app(Config(secret="s" * 40, storage="s3"), storage=storage))
    j = c.post("/v1/installs", json={}).json()
    auth = {"Authorization": f"Bearer {j['token']}"}
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w") as z:
        z.writestr("samples.jsonl", "{}\n")
    data = b"HSE1" + out.getvalue()  # the service checks the seal's marker, not its contents
    b = c.post("/v1/batches", headers=auth, json={"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(), "count": 1}).json()
    assert "X-Amz-Signature=" in b["upload_url"] and b["upload_url"].startswith("https://")
    assert requests.put(b["upload_url"], data=data, headers=b["headers"]).status_code == 200
    assert c.post(f"/v1/batches/{b['batch_id']}/complete", headers=auth).status_code == 200
    keys = sorted(storage.keys("incoming/"))
    assert len(keys) == 2 and keys[0].endswith(".json") and keys[1].endswith(".zip")
    assert json.loads(b"".join(storage.chunks(keys[0])))["count"] == 1
    assert c.delete(f"/v1/installs/{j['install_id']}", headers=auth).json()["deleted"] == 2
    assert list(storage.keys("incoming/")) == [] and storage.exists(f"deleted/{j['install_id']}")


def test_azure_upload_link_is_short_lived_https_write_only():
    conn = "DefaultEndpointsProtocol=https;AccountName=hisaabimprove;AccountKey=" + "a" * 86 + "==;EndpointSuffix=core.windows.net"
    storage = AzureStorage("incoming", connection_string=conn)
    url, headers = storage.upload_link("incoming/i1/b1.zip", 100, 900)
    assert url.startswith("https://hisaabimprove.blob.core.windows.net/incoming/incoming/i1/b1.zip?")
    assert "sp=cw" in url and "spr=https" in url and "se=" in url and "sig=" in url
    assert headers["x-ms-blob-type"] == "BlockBlob"
