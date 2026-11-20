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

import hashlib
import json
import logging
import os
import re
import tempfile
import threading
import time
from collections import defaultdict, deque
from datetime import datetime, timezone

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from . import __version__
from .signing import Signer
from .storage import AzureStorage, LocalStorage, S3Storage, Storage, delete_prefix

SHA256 = re.compile(r"^[0-9a-f]{64}$")


log = logging.getLogger("hisaab.audit")


def audit(event: str, **fields) -> None:
    """One JSON line per event. Never an IP address, token or worker data."""
    log.info(json.dumps({"event": event, **fields}, separators=(",", ":")))


class Config(BaseModel):
    secret: str
    secret_previous: list[str] = []
    storage: str = "local"  # local | s3 | azure
    local_dir: str = "./data"
    public_url: str = "http://127.0.0.1:8080"
    s3_bucket: str = ""
    s3_region: str | None = None
    azure_container: str = ""
    azure_connection_string: str | None = None
    azure_account_url: str | None = None
    # 25 exchanges with 20-second clips are under 17 MB.
    max_batch_bytes: int = 24 * 1024 * 1024
    # A phone sends at most 200 waiting exchanges, in batches of 25: 24 a day is generous.
    max_batches_per_day: int = 24
    upload_ttl_s: int = 15 * 60
    registrations_per_hour: int = 120
    # Batches must arrive sealed to the team key (crypto.py); anything else is refused.
    require_sealed: bool = True
    # Proxies in front of the service that add to X-Forwarded-For (Azure Container Apps,
    # a load balancer): 1. The client is the entry they added, not one it could have sent.
    trusted_proxy_hops: int = 1

    @classmethod
    def from_env(cls) -> "Config":
        e = os.environ.get
        return cls(
            secret=e("HISAAB_SECRET", ""),
            secret_previous=[x for x in e("HISAAB_SECRET_PREVIOUS", "").split(",") if x],
            storage=e("HISAAB_STORAGE", "local"),
            local_dir=e("HISAAB_LOCAL_DIR", "./data"),
            public_url=e("HISAAB_PUBLIC_URL", "http://127.0.0.1:8080"),
            s3_bucket=e("HISAAB_S3_BUCKET", ""),
            s3_region=e("AWS_REGION"),
            azure_container=e("HISAAB_AZURE_CONTAINER", ""),
            azure_connection_string=e("HISAAB_AZURE_CONNECTION_STRING"),
            azure_account_url=e("HISAAB_AZURE_ACCOUNT_URL"),
            max_batch_bytes=int(e("HISAAB_MAX_BATCH_BYTES", str(24 * 1024 * 1024))),
            max_batches_per_day=int(e("HISAAB_MAX_BATCHES_PER_DAY", "24")),
            upload_ttl_s=int(e("HISAAB_UPLOAD_TTL_S", "900")),
            registrations_per_hour=int(e("HISAAB_REGISTRATIONS_PER_HOUR", "120")),
            require_sealed=e("HISAAB_REQUIRE_SEALED", "1") != "0",
            trusted_proxy_hops=int(e("HISAAB_TRUSTED_PROXY_HOPS", "1")),
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
    signer = Signer(config.secret, config.secret_previous)
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
    app = FastAPI(title="HisaabSaathi improve", version=__version__, docs_url=None, redoc_url=None, openapi_url=None)

    @app.middleware("http")
    async def small_requests_only(request: Request, call_next):
        # Only the folder backend's upload route takes a large body; everything else is a few lines of JSON.
        if request.method != "PUT":
            n = request.headers.get("content-length")
            if n is None and request.method == "POST":
                return JSONResponse({"detail": "length required"}, status_code=411)
            if n is not None and (not n.isdigit() or int(n) > 8192):
                return JSONResponse({"detail": "request too large"}, status_code=413)
        return await call_next(request)

    def client_of(request: Request) -> str:
        """The caller's address as our own proxy saw it: a client can add entries to the
        left of X-Forwarded-For, but not to the right of what the proxy appended."""
        hops = [h.strip() for h in request.headers.get("x-forwarded-for", "").split(",") if h.strip()]
        if config.trusted_proxy_hops > 0 and len(hops) >= config.trusted_proxy_hops:
            return hops[-config.trusted_proxy_hops]
        return request.client.host if request.client else "?"

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
        if not limit.allow(client_of(request)):
            audit("register_refused", reason="rate")
            raise HTTPException(429, "too many new installs")
        install_id, token = signer.new_install()
        audit("registered", install=install_id, app_version=body.app_version)
        return {"install_id": install_id, "token": token}

    @app.post("/v1/batches")
    def new_batch(body: NewBatch, install: str = Depends(install_from)):
        if body.bytes > config.max_batch_bytes:
            raise HTTPException(413, "batch too big")
        if not SHA256.match(body.sha256):
            raise HTTPException(422, "sha256 must be 64 lower-case hex characters")
        now = datetime.now(timezone.utc)
        today = sum(1 for k in storage.keys(f"incoming/{install}/{now:%Y%m%d}") if k.endswith(".zip"))
        if today >= config.max_batches_per_day:
            audit("batch_refused", install=install, reason="daily limit")
            raise HTTPException(429, "daily limit reached; try tomorrow")
        batch = now.strftime("%Y%m%dT%H%M%S") + "-" + os.urandom(4).hex()
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
        target = storage.path(t["key"])
        target.parent.mkdir(parents=True, exist_ok=True)
        fd, tmp = tempfile.mkstemp(dir=target.parent, suffix=".part")
        n = 0
        try:
            with os.fdopen(fd, "wb") as f:
                async for chunk in request.stream():  # to disk as it arrives, never all in memory
                    n += len(chunk)
                    if n > t["bytes"]:
                        raise HTTPException(413, "more than promised")
                    f.write(chunk)
            os.replace(tmp, target)
        finally:
            if os.path.exists(tmp):
                os.unlink(tmp)
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
        h, head = hashlib.sha256(), b""
        if size == t["bytes"]:
            for c in storage.chunks(key + ".zip"):  # one read: fingerprint and the seal's magic
                head += c[:4 - len(head)]
                h.update(c)
        if size != t["bytes"] or h.hexdigest() != t["sha256"]:
            storage.delete(key + ".zip")
            audit("batch_rejected", install=install, reason="damaged")
            raise HTTPException(422, "upload damaged; send it again")
        if config.require_sealed and head != b"HSE1":
            storage.delete(key + ".zip")
            audit("batch_rejected", install=install, reason="not sealed")
            raise HTTPException(422, "batches must be sealed to the team key")
        meta = {"install": install, "batch": t["batch"], "bytes": size, "sha256": t["sha256"], "count": t["count"],
                "received_at": datetime.now(timezone.utc).isoformat(timespec="seconds")}
        storage.put(key + ".json", json.dumps(meta).encode())
        audit("batch_received", install=install, batch=t["batch"], bytes=size, count=t["count"])
        return {"ok": True}

    @app.delete("/v1/installs/{install_id}", status_code=202)
    def delete_install(install_id: str, install: str = Depends(install_from)):
        if install_id != install:
            raise HTTPException(403, "not your install")
        # The tombstone first: from here on the token is refused, and the pipeline purges
        # anything it derived from this install.
        storage.put(f"deleted/{install}", datetime.now(timezone.utc).isoformat(timespec="seconds").encode())
        n = delete_prefix(storage, f"incoming/{install}/")
        audit("install_deleted", install=install, files=n)
        return {"deleted": n}

    return app


def main():  # pragma: no cover
    import uvicorn

    logging.basicConfig(level=logging.INFO, format="%(message)s")
    # No access log (it would hold IP addresses); X-Forwarded-For is read by client_of() only.
    uvicorn.run(create_app(), host="0.0.0.0", port=int(os.environ.get("PORT", "8080")), access_log=False, proxy_headers=False)


if __name__ == "__main__":  # pragma: no cover
    main()
