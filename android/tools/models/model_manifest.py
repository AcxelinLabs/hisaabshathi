#!/usr/bin/env python3
"""Signs the model manifest that tells phones which smart model is current.

    pip install cryptography

1) Once: make the team's model key (keep the private key OFF this repository, like the upload key).
     python tools/models/model_manifest.py new-key --out C:\\keys\\hisaab-model-key.pem
   It prints the public key. Build the app with it:
     ./gradlew assembleRelease -Phisaab.modelKey=<public key>
   (or put hisaab.modelKey=<public key> in android/gradle.properties: it is public, safe to commit).

2) For each new model, after uploading its file to Hugging Face under a NEW name:
     python tools/models/model_manifest.py sign --key C:\\keys\\hisaab-model-key.pem \\
         --file hisaabsaathi-qwen3-ft-v2-q8.litertlm --version 2 --model-file path\\to\\the\\file
   It reads the file's size and SHA-256, finds the Hugging Face commit that holds it, and writes
   models.json and models.json.sig in the current folder. Upload both to the model repo:
     hf upload AcxelinLabs/hisaabsaathi-model models.json models.json --commit-message "Model v2"
     hf upload AcxelinLabs/hisaabsaathi-model models.json.sig models.json.sig --commit-message "Model v2 signature"
   Phones check about once a week on Wi-Fi, download it, test it, switch, and delete the old one.

Rules: never overwrite a published model file; version numbers only go up. To roll back, sign the
old file again with a higher version number. --min-app N stops app versions older than versionCode N
from taking a model whose answers they cannot read.
"""
import argparse, base64, hashlib, json, os, sys, urllib.request

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

REPO = "AcxelinLabs/hisaabsaathi-model"
MODEL_ID = "slm-hisaab"
HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))


def new_key(out):
    out = os.path.abspath(out)
    if out.startswith(REPO_ROOT + os.sep):
        sys.exit("Refusing to write the private key inside the repository. Choose a folder outside it.")
    if os.path.exists(out):
        sys.exit(f"{out} exists; not overwriting a key.")
    key = ec.generate_private_key(ec.SECP256R1())
    with open(out, "wb") as f:
        f.write(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
    print("Private key written to", out, "(keep it safe; anyone with it can push models to phones)")
    print("Public key (hisaab.modelKey):")
    print(public_b64(key))


def public_b64(key):
    der = key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    return base64.b64encode(der).decode()


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def commit_holding(name, sha):
    """The latest commit of the model repo whose copy of `name` has this SHA-256 (so the URL is pinned)."""
    with urllib.request.urlopen(f"https://huggingface.co/api/models/{REPO}/revision/main", timeout=60) as r:
        commit = json.load(r)["sha"]
    with urllib.request.urlopen(f"https://huggingface.co/api/models/{REPO}/tree/{commit}", timeout=60) as r:
        files = {f["path"]: f for f in json.load(r)}
    f = files.get(name)
    if not f or (f.get("lfs") or {}).get("oid") != sha:
        sys.exit(f"{name} with SHA-256 {sha} is not on Hugging Face main yet. Upload it first (under a new name).")
    return commit


def sign(args):
    with open(args.key, "rb") as f:
        key = serialization.load_pem_private_key(f.read(), password=None)
    size = os.path.getsize(args.model_file)
    sha = sha256_of(args.model_file)
    commit = args.revision or commit_holding(args.file, sha)
    release = {
        "id": MODEL_ID, "version": args.version, "file": args.file,
        "url": f"https://huggingface.co/{REPO}/resolve/{commit}/{args.file}",
        "bytes": size, "sha256": sha, "minAppVersion": args.min_app,
    }
    manifest = json.dumps({"format": 1, "models": [release]}, indent=2).encode() + b"\n"
    sig = key.sign(manifest, ec.ECDSA(hashes.SHA256()))
    with open("models.json", "wb") as f:
        f.write(manifest)
    with open("models.json.sig", "w") as f:
        f.write(base64.b64encode(sig).decode() + "\n")
    print(manifest.decode())
    print("Signed with public key:", public_b64(key))
    print("Wrote models.json and models.json.sig here. Upload both to", REPO)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    k = sub.add_parser("new-key"); k.add_argument("--out", required=True)
    s = sub.add_parser("sign")
    s.add_argument("--key", required=True)
    s.add_argument("--file", required=True, help="file name as uploaded to Hugging Face")
    s.add_argument("--version", type=int, required=True)
    s.add_argument("--model-file", required=True, help="the same file on this computer (size and SHA-256 are read from it)")
    s.add_argument("--min-app", type=int, default=0)
    s.add_argument("--revision", help="Hugging Face commit (default: found automatically)")
    a = ap.parse_args()
    new_key(a.out) if a.cmd == "new-key" else sign(a)


if __name__ == "__main__":
    main()
