"""Signed, self-checking strings, so the service keeps no database of installs or uploads.

An install token is ``<install_id>.<signature>``; an upload ticket carries what was promised
(where, how big, its SHA-256, until when) and a signature. Changing the secret signs everyone
out: phones then register again on their own.
"""
from __future__ import annotations

import base64
import hashlib
import hmac
import json
import secrets
import time


def _b64(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()


def _unb64(s: str) -> bytes:
    return base64.urlsafe_b64decode(s + "=" * (-len(s) % 4))


class Signer:
    """Signs with [secret]. [previous] secrets still verify, so the secret can be changed
    without signing every phone out: put the old one in HISAAB_SECRET_PREVIOUS for a while."""

    def __init__(self, secret: str, previous: list[str] | None = None):
        for s in [secret, *(previous or [])]:
            if len(s) < 32:
                raise ValueError("HISAAB_SECRET (and any previous one) must be at least 32 characters")
        self._keys = [secret.encode()] + [p.encode() for p in previous or []]

    def _sig(self, purpose: str, data: str, key: bytes | None = None) -> str:
        return _b64(hmac.new(key or self._keys[0], f"{purpose}:{data}".encode(), hashlib.sha256).digest()[:24])

    def _ok(self, purpose: str, data: str, sig: str) -> bool:
        return any(hmac.compare_digest(sig, self._sig(purpose, data, k)) for k in self._keys)

    # ---- installs

    def new_install(self) -> tuple[str, str]:
        install_id = "i" + secrets.token_hex(12)
        return install_id, f"{install_id}.{self._sig('install', install_id)}"

    def install_of(self, token: str) -> str | None:
        """The install a token belongs to, or None if it is not one of ours."""
        install_id, _, sig = token.partition(".")
        if not install_id.startswith("i") or not sig:
            return None
        return install_id if self._ok("install", install_id, sig) else None

    # ---- tickets (upload links and batch ids)

    def ticket(self, purpose: str, fields: dict, ttl_s: int) -> str:
        body = _b64(json.dumps({**fields, "exp": int(time.time()) + ttl_s}, separators=(",", ":")).encode())
        return f"{body}.{self._sig(purpose, body)}"

    def open_ticket(self, purpose: str, ticket: str) -> dict | None:
        """The fields of a ticket, or None if forged or expired."""
        body, _, sig = ticket.partition(".")
        if not sig or not self._ok(purpose, body, sig):
            return None
        try:
            fields = json.loads(_unb64(body))
        except ValueError:
            return None
        return fields if fields.get("exp", 0) >= time.time() else None
