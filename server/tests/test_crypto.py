from pathlib import Path

import pytest
from cryptography.hazmat.primitives.asymmetric import rsa

from hisaab_improve.crypto import SealError, key_id, load_private_keys, new_key, open_sealed, seal

FIX = Path(__file__).parent / "fixtures"
KEYS = load_private_keys([(FIX / "TEST-ONLY-team-key.der.b64").read_text()])


def test_opens_what_the_app_sealed():
    """Sealed by android/core/.../Seal.kt (SealTest) with the shared test key."""
    assert open_sealed((FIX / "sealed_by_kotlin.bin").read_bytes(), KEYS) == b"HisaabSaathi sealed by Kotlin"


def test_round_trip_and_tampering():
    key = next(iter(KEYS.values()))
    blob = seal(b"x" * 1000, key.public_key())
    assert open_sealed(blob, KEYS) == b"x" * 1000
    for i in (5, 20, len(blob) - 1):
        bad = bytearray(blob); bad[i] ^= 1
        with pytest.raises(SealError):
            open_sealed(bytes(bad), KEYS)
    with pytest.raises(SealError):
        open_sealed(b"PK\x03\x04 an unsealed zip", KEYS)


def test_unknown_key_and_weak_keys():
    other = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    with pytest.raises(SealError, match="does not hold"):
        open_sealed(seal(b"x", other.public_key()), KEYS)
    weak = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    from cryptography.hazmat.primitives import serialization
    pem = weak.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()).decode()
    with pytest.raises(ValueError):
        load_private_keys([pem])


def test_new_key_is_owner_only_and_usable(tmp_path):
    path = tmp_path / "team.pem"
    pub, kid = new_key(str(path))
    assert oct(path.stat().st_mode & 0o777) == "0o600"
    keys = load_private_keys([path.read_text()])
    assert next(iter(keys)).hex() == kid
    import base64
    from cryptography.hazmat.primitives import serialization
    public = serialization.load_der_public_key(base64.b64decode(pub))
    assert key_id(public).hex() == kid
    assert open_sealed(seal(b"ok", public), keys) == b"ok"
