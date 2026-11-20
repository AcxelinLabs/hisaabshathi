"""Where batches are kept: Amazon S3, Azure Blob Storage, or a folder (for trying it out).

Layout, the same in every backend:

    incoming/<install>/<batch>.zip    a batch as the phone sent it
    incoming/<install>/<batch>.json   written once the zip is checked (size and SHA-256)
    deleted/<install>                 the worker asked for everything to be deleted

The training pipeline (training/ in the repository) reads only batches with their .json, and
removes everything it derived from an install listed under deleted/.
"""
from __future__ import annotations

import hashlib
import os
from pathlib import Path
from typing import Iterator, Protocol


class Storage(Protocol):
    def upload_link(self, key: str, size: int, ttl_s: int) -> tuple[str, dict[str, str]]:
        """A short-lived link the phone PUTs the zip to, and the headers it must send."""

    def size(self, key: str) -> int | None: ...
    def chunks(self, key: str) -> Iterator[bytes]: ...
    def put(self, key: str, data: bytes) -> None: ...
    def exists(self, key: str) -> bool: ...
    def delete(self, key: str) -> None: ...
    def keys(self, prefix: str) -> Iterator[str]: ...


def sha256_of(storage: Storage, key: str) -> str:
    h = hashlib.sha256()
    for c in storage.chunks(key):
        h.update(c)
    return h.hexdigest()


def delete_prefix(storage: Storage, prefix: str) -> int:
    n = 0
    for k in list(storage.keys(prefix)):
        storage.delete(k)
        n += 1
    return n


class LocalStorage:
    """A folder. Upload links point back at this service (PUT /v1/upload/<ticket>)."""

    def __init__(self, root: str, public_url: str, ticket):
        self.root = Path(root).resolve()
        self.root.mkdir(parents=True, exist_ok=True)
        self.public_url = public_url.rstrip("/")
        self._ticket = ticket  # (key, size, ttl) -> signed ticket

    def path(self, key: str) -> Path:
        p = (self.root / key).resolve()
        if p != self.root and self.root not in p.parents:
            raise ValueError("key outside storage")
        return p

    def upload_link(self, key, size, ttl_s):
        return f"{self.public_url}/v1/upload/{self._ticket(key, size, ttl_s)}", {"Content-Type": "application/zip"}

    def size(self, key):
        p = self.path(key)
        return p.stat().st_size if p.is_file() else None

    def chunks(self, key):
        with open(self.path(key), "rb") as f:
            while c := f.read(1 << 20):
                yield c

    def put(self, key, data):
        p = self.path(key)
        p.parent.mkdir(parents=True, exist_ok=True)
        tmp = p.with_suffix(p.suffix + ".part")
        tmp.write_bytes(data)
        os.replace(tmp, p)

    def exists(self, key):
        return self.path(key).is_file()

    def delete(self, key):
        self.path(key).unlink(missing_ok=True)

    def keys(self, prefix):
        # Walk only the prefix's folder, not the whole store.
        folder = prefix.rsplit("/", 1)[0] if "/" in prefix else ""
        start = self.path(folder) if folder else self.root
        if not start.is_dir():
            return
        for p in sorted(start.rglob("*")):
            if p.is_file() and not p.name.endswith(".part"):
                k = p.relative_to(self.root).as_posix()
                if k.startswith(prefix):
                    yield k


class S3Storage:
    """Amazon S3 (or any S3-compatible store). Credentials come from the usual AWS chain,
    best an IAM role on the service; the bucket should block public access and encrypt."""

    def __init__(self, bucket: str, region: str | None = None, client=None):
        import boto3
        from botocore.config import Config

        self.bucket = bucket
        self.s3 = client or boto3.client("s3", region_name=region, config=Config(signature_version="s3v4"))

    def upload_link(self, key, size, ttl_s):
        url = self.s3.generate_presigned_url(
            "put_object", Params={"Bucket": self.bucket, "Key": key, "ContentType": "application/zip"}, ExpiresIn=ttl_s,
        )
        return url, {"Content-Type": "application/zip"}

    def size(self, key):
        from botocore.exceptions import ClientError

        try:
            return self.s3.head_object(Bucket=self.bucket, Key=key)["ContentLength"]
        except ClientError as e:
            if e.response["Error"]["Code"] in ("404", "NoSuchKey", "NotFound"):
                return None
            raise

    def chunks(self, key):
        body = self.s3.get_object(Bucket=self.bucket, Key=key)["Body"]
        yield from body.iter_chunks(1 << 20)

    def put(self, key, data):
        self.s3.put_object(Bucket=self.bucket, Key=key, Body=data)

    def exists(self, key):
        return self.size(key) is not None

    def delete(self, key):
        self.s3.delete_object(Bucket=self.bucket, Key=key)

    def keys(self, prefix):
        for page in self.s3.get_paginator("list_objects_v2").paginate(Bucket=self.bucket, Prefix=prefix):
            for o in page.get("Contents", []):
                yield o["Key"]


class AzureStorage:
    """Azure Blob Storage. With a connection string, upload links are signed with the account
    key; without one, the service's managed identity signs them (user delegation SAS), which
    is better: no key to keep. The container must not allow public access."""

    def __init__(self, container: str, connection_string: str | None = None, account_url: str | None = None):
        from azure.storage.blob import BlobServiceClient

        if connection_string:
            self.service = BlobServiceClient.from_connection_string(connection_string)
            self._key = self.service.credential.account_key
        else:
            from azure.identity import DefaultAzureCredential

            self.service = BlobServiceClient(account_url, credential=DefaultAzureCredential())
            self._key = None
        self.container = self.service.get_container_client(container)
        self.container_name = container

    def upload_link(self, key, size, ttl_s):
        from datetime import datetime, timedelta, timezone

        from azure.storage.blob import BlobSasPermissions, generate_blob_sas

        now = datetime.now(timezone.utc)
        start, expiry = now - timedelta(minutes=5), now + timedelta(seconds=ttl_s)
        args = dict(
            account_name=self.service.account_name, container_name=self.container_name, blob_name=key,
            permission=BlobSasPermissions(create=True, write=True), start=start, expiry=expiry, protocol="https",
        )
        if self._key:
            sas = generate_blob_sas(account_key=self._key, **args)
        else:
            sas = generate_blob_sas(user_delegation_key=self.service.get_user_delegation_key(start, expiry), **args)
        url = f"{self.container.url}/{key}?{sas}"
        return url, {"Content-Type": "application/zip", "x-ms-blob-type": "BlockBlob"}

    def size(self, key):
        from azure.core.exceptions import ResourceNotFoundError

        try:
            return self.container.get_blob_client(key).get_blob_properties().size
        except ResourceNotFoundError:
            return None

    def chunks(self, key):
        yield from self.container.download_blob(key).chunks()

    def put(self, key, data):
        self.container.upload_blob(key, data, overwrite=True)

    def exists(self, key):
        return self.size(key) is not None

    def delete(self, key):
        from azure.core.exceptions import ResourceNotFoundError

        try:
            self.container.delete_blob(key)
        except ResourceNotFoundError:
            pass

    def keys(self, prefix):
        for b in self.container.list_blobs(name_starts_with=prefix):
            yield b.name
