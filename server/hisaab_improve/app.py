"""The upload service the phone talks to (android/core/.../ShareClient.kt, protocol version 1).

    POST   /v1/installs                     -> {"install_id", "token"}
    POST   /v1/batches                      {"bytes", "sha256", "count"} -> {"batch_id", "upload_url", "headers"}
    PUT    <upload_url>                     the zip, straight to S3 / Azure Blob (or /v1/upload/... for a folder)
    POST   /v1/batches/{batch_id}/complete  checks size and SHA-256, then records the batch
    DELETE /v1/installs/{install_id}        deletes everything the install sent
    GET    /healthz

No accounts and no database: tokens and batch ids are signed (signing.py), and everything is
kept in the storage. The service never stores IP addresses; run it without access logs.
"""
from __future__ import annotations

import json
import os
import re
import threading
import time
from collections import defaultdict, deque
from datetime import datetime, timezone

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from . import __version__
from .signing import Signer
from .storage import AzureStorage, LocalStorage, S3Storage, Storage, delete_prefix, sha256_of

SHA256 = re.compile(r"^[0-9a-f]{64}$")


class Config(BaseModel):
    secret: str
    storage: str = "local"  # local | s3 | azure
    local_dir: str = "./data"
    public_url: str = "http://127.0.0.1:8080"
    s3_bucket: str = ""
    s3_region: str | None = None
    azure_container: str = ""
    azure_connection_string: str | None = None
    azure_account_url: str | None = None
    max_batch_bytes: int = 50 * 1024 * 1024
    upload_ttl_s: int = 15 * 60
    registrations_per_hour: int = 60

    @classmethod
    def from_env(cls) -> "Config":
        e = os.environ.get
        return cls(
            secret=e("HISAAB_SECRET", ""),
            storage=e("HISAAB_STORAGE", "local"),
            local_dir=e("HISAAB_LOCAL_DIR", "./data"),
            public_url=e("HISAAB_PUBLIC_URL", "http://127.0.0.1:8080"),
            s3_bucket=e("HISAAB_S3_BUCKET", ""),
            s3_region=e("AWS_REGION"),
            azure_container=e("HISAAB_AZURE_CONTAINER", ""),
            azure_connection_string=e("HISAAB_AZURE_CONNECTION_STRING"),
            azure_account_url=e("HISAAB_AZURE_ACCOUNT_URL"),
            max_batch_bytes=int(e("HISAAB_MAX_BATCH_BYTES", str(50 * 1024 * 1024))),
            upload_ttl_s=int(e("HISAAB_UPLOAD_TTL_S", "900")),
            registrations_per_hour=int(e("HISAAB_REGISTRATIONS_PER_HOUR", "60")),
        )


class NewInstall(BaseModel):
    app_version: str = Field("", max_length=40)


class NewBatch(BaseModel):
    bytes: int = Field(gt=0)
    sha256: str
    count: int = Field(gt=0, le=200)


class RateLimit:
    """At most [n] registrations per hour from one address, kept in memory only."""

    def __init__(self, n: int):
        self.n, self.seen, self.lock = n, defaultdict(deque), threading.Lock()

    def allow(self, who: str) -> bool:
        now = time.time()
        with self.lock:
            q = self.seen[who]
            while q and q[0] < now - 3600:
                q.popleft()
            if len(q) >= self.n:
                return False
            q.append(now)
            if len(self.seen) > 100_000:  # never grow without limit
                self.seen.clear()
            return True


def create_app(config: Config | None = None, storage: Storage | None = None) -> FastAPI:
    config = config or Config.from_env()
    signer = Signer(config.secret)
    if storage is None:
        if config.storage == "s3":
            storage = S3Storage(config.s3_bucket, config.s3_region)
        elif config.storage == "azure":
            storage = AzureStorage(config.azure_container, config.azure_connection_string, config.azure_account_url)
        else:
            storage = LocalStorage(
                config.local_dir, config.public_url,
                lambda key, size, ttl: signer.ticket("put", {"key": key, "bytes": size}, ttl),
            )
    limit = RateLimit(config.registrations_per_hour)
    app = FastAPI(title="HisaabSaathi improve", version=__version__, docs_url=None, redoc_url=None)

    def install_from(authorization: str = Header("")) -> str:
        token = authorization.removeprefix("Bearer ").strip()
        install = signer.install_of(token)
        if install is None or storage.exists(f"deleted/{install}"):
            raise HTTPException(401, "unknown token")
        return install

    @app.get("/healthz")
    def healthz():
        return {"ok": True, "version": __version__}

    @app.post("/v1/installs", status_code=201)
    def register(body: NewInstall, request: Request):
        who = request.client.host if request.client else "?"
        if not limit.allow(who):
            raise HTTPException(429, "too many new installs")
        install_id, token = signer.new_install()
        return {"install_id": install_id, "token": token}

    @app.post("/v1/batches")
    def new_batch(body: NewBatch, install: str = Depends(install_from)):
        if body.bytes > config.max_batch_bytes:
            raise HTTPException(413, "batch too big")
        if not SHA256.match(body.sha256):
            raise HTTPException(422, "sha256 must be 64 lower-case hex characters")
        batch = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S") + "-" + os.urandom(4).hex()
        key = f"incoming/{install}/{batch}.zip"
        url, headers = storage.upload_link(key, body.bytes, config.upload_ttl_s)
        ticket = signer.ticket("batch", {"install": install, "batch": batch, "bytes": body.bytes, "sha256": body.sha256, "count": body.count},
                               config.upload_ttl_s + 3600)
        return {"batch_id": ticket, "upload_url": url, "headers": headers}

    @app.put("/v1/upload/{ticket}", status_code=201)
    async def upload_to_folder(ticket: str, request: Request):
        """Only for the folder backend: S3 and Azure take the upload themselves."""
        if not isinstance(storage, LocalStorage):
            raise HTTPException(404)
        t = signer.open_ticket("put", ticket)
        if t is None:
            raise HTTPException(403, "link expired or not valid")
        data = bytearray()
        async for chunk in request.stream():
            data += chunk
            if len(data) > t["bytes"]:
                raise HTTPException(413, "more than promised")
        storage.put(t["key"], bytes(data))
        return JSONResponse({}, status_code=201)

    @app.post("/v1/batches/{ticket}/complete")
    def complete(ticket: str, install: str = Depends(install_from)):
        t = signer.open_ticket("batch", ticket)
        if t is None or t["install"] != install:
            raise HTTPException(404, "unknown batch")
        key = f"incoming/{install}/{t['batch']}"
        if storage.exists(key + ".json"):
            return {"ok": True}  # already recorded: the phone retried
        size = storage.size(key + ".zip")
        if size is None:
            raise HTTPException(409, "not uploaded")
        if size != t["bytes"] or sha256_of(storage, key + ".zip") != t["sha256"]:
            storage.delete(key + ".zip")
            raise HTTPException(422, "upload damaged; send it again")
        meta = {"install": install, "batch": t["batch"], "bytes": size, "sha256": t["sha256"], "count": t["count"],
                "received_at": datetime.now(timezone.utc).isoformat(timespec="seconds")}
        storage.put(key + ".json", json.dumps(meta).encode())
        return {"ok": True}

    @app.delete("/v1/installs/{install_id}", status_code=202)
    def delete_install(install_id: str, install: str = Depends(install_from)):
        if install_id != install:
            raise HTTPException(403, "not your install")
        # The tombstone first: from here on the token is refused, and the pipeline purges
        # anything it derived from this install.
        storage.put(f"deleted/{install}", datetime.now(timezone.utc).isoformat(timespec="seconds").encode())
        return {"deleted": delete_prefix(storage, f"incoming/{install}/")}

    return app


def main():  # pragma: no cover
    import uvicorn

    uvicorn.run(create_app(), host="0.0.0.0", port=int(os.environ.get("PORT", "8080")), access_log=False, proxy_headers=True)


if __name__ == "__main__":  # pragma: no cover
    main()
