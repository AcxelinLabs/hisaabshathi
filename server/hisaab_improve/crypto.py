"""Batches are sealed on the phone to the team's public key (android/core/.../Seal.kt) and
opened only here, by the training pipeline. The upload service never holds the private key.

Format "HSE1": magic (4) · key id (8) · wrapped key length (2, big-endian) · wrapped key
(RSA-OAEP, SHA-256, MGF1-SHA-256) · nonce (12) · AES-256-GCM ciphertext and tag, with
everything before the ciphertext as associated data. Key id: first 8 bytes of SHA-256 of the
public key (SubjectPublicKeyInfo DER), so several keys can be held during a rotation.

    python -m hisaab_improve.crypto new-key <private_key_file>

makes a new team key: the private key goes to the file (put it in Key Vault or Secrets
Manager, then delete the file); the public key is printed for the app build
(-Phisaab.shareKey=...).
"""
from __future__ import annotations

import base64
import hashlib
import os
import struct
import sys

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

MAGIC = b"HSE1"
OAEP = padding.OAEP(mgf=padding.MGF1(algorithm=hashes.SHA256()), algorithm=hashes.SHA256(), label=None)


class SealError(ValueError):
    """Not a sealed batch, sealed to a key we do not hold, or changed after sealing."""


def public_der(public_key) -> bytes:
    return public_key.public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)


def key_id(public_key) -> bytes:
    return hashlib.sha256(public_der(public_key)).digest()[:8]


def seal(plain: bytes, public_key) -> bytes:
    """What the phone does (for tests and tools)."""
    aes, nonce = os.urandom(32), os.urandom(12)
    wrapped = public_key.encrypt(aes, OAEP)
    header = MAGIC + key_id(public_key) + struct.pack(">H", len(wrapped)) + wrapped + nonce
    return header + AESGCM(aes).encrypt(nonce, plain, header)


def open_sealed(blob: bytes, keys: dict[bytes, object]) -> bytes:
    if len(blob) < 4 + 8 + 2 + 12 + 16 or blob[:4] != MAGIC:
        raise SealError("not a sealed batch")
    kid = blob[4:12]
    key = keys.get(kid)
    if key is None:
        raise SealError(f"sealed to key {kid.hex()}, which this pipeline does not hold")
    (n,) = struct.unpack(">H", blob[12:14])
    end = 14 + n + 12
    if len(blob) < end + 16:
        raise SealError("truncated")
    header = blob[:end]
    try:
        aes = key.decrypt(blob[14:14 + n], OAEP)
        return AESGCM(aes).decrypt(blob[14 + n:end], blob[end:], header)
    except (InvalidTag, ValueError) as e:
        raise SealError("damaged or changed after sealing") from e


def load_private_keys(texts: list[str]) -> dict[bytes, object]:
    """Private keys as PEM text, base64 of the PEM (one line, for cloud secrets), or base64 of DER."""
    keys = {}
    for t in texts:
        t = t.strip()
        if not t:
            continue
        raw = t.encode() if t.startswith("-----BEGIN") else base64.b64decode(t)
        k = (serialization.load_pem_private_key(raw, password=None) if raw.startswith(b"-----BEGIN")
             else serialization.load_der_private_key(raw, password=None))
        if not isinstance(k, rsa.RSAPrivateKey) or k.key_size < 3072:
            raise ValueError("team keys must be RSA, 3072 bits or more")
        keys[key_id(k.public_key())] = k
    return keys


def keys_from_env() -> dict[bytes, object]:
    """HISAAB_PRIVATE_KEYS_B64 (comma-separated, from a secret store) and/or HISAAB_PRIVATE_KEY_FILES."""
    texts = [t for t in os.environ.get("HISAAB_PRIVATE_KEYS_B64", "").split(",") if t.strip()]
    for path in filter(None, os.environ.get("HISAAB_PRIVATE_KEY_FILES", "").split(",")):
        with open(path, encoding="ascii") as f:
            texts.append(f.read())
    return load_private_keys(texts)


def new_key(path: str) -> tuple[str, str]:
    """Writes a new private key (owner-only) and returns (public key base64, key id)."""
    k = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    pem = k.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption())
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "wb") as f:
        f.write(pem)
    return base64.b64encode(public_der(k.public_key())).decode(), key_id(k.public_key()).hex()


def main(argv=None):  # pragma: no cover
    argv = argv if argv is not None else sys.argv[1:]
    if len(argv) != 2 or argv[0] != "new-key":
        print(__doc__)
        return 2
    pub, kid = new_key(argv[1])
    with open(argv[1], "rb") as f:
        one_line = base64.b64encode(f.read()).decode()
    print(f"Key id: {kid}")
    print(f"Private key written to {argv[1]}. As one line for a cloud secret (HISAAB_PRIVATE_KEYS_B64):")
    print(one_line)
    print("\nPublic key, for the app build (-Phisaab.shareKey=...):")
    print(pub)
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
