#!/usr/bin/env python3
"""
make_keystore.py — release signing keystore তৈরি করে CI-র জন্য base64 ও secret ভ্যালু দেখায়।

তিনটি ব্যাকএন্ড চেষ্টা করা হয় (যেটা পাওয়া যায় সেটাই ব্যবহার হবে):
  1. keytool   (JDK-এর সাথে আসে — সবচেয়ে নির্ভরযোগ্য, Android-এর জন্য আদর্শ JKS)
  2. openssl   (PKCS#12 তৈরি করে, AGP সেটাও পড়তে পারে)
  3. cryptography প্যাকেজ (Python fallback)

ব্যবহার:
    python3 tools/make_keystore.py --store-password "MyStrongPass" --alias upload
    python3 tools/make_keystore.py --ensure --out my-upload-key.jks   # প্রয়োজন হলে তৈরি করবে

আউটপুট: keystore ফাইল + KEYSTORE_BASE64 ভ্যালু (কপি করে GitHub secret-এ দিন)।
"""

from __future__ import annotations

import argparse
import base64
import os
import shutil
import secrets
import string
import subprocess
import sys


def random_password(length: int = 24) -> str:
    alphabet = string.ascii_letters + string.digits
    return "".join(secrets.choice(alphabet) for _ in range(length))


def run(cmd: list[str]) -> None:
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(
            f"{cmd[0]} ব্যর্থ (exit {result.returncode}):\n{result.stdout}\n{result.stderr}"
        )


def backend_keytool(out: str, store_password: str, alias: str, key_password: str,
                    common_name: str, years: int) -> bool:
    keytool = shutil.which("keytool")
    if not keytool:
        return False
    # একটি keystore-এ একটি key থাকলে store ও key password একই হওয়া উচিত,
    # নাহলে AGP-এর PKCS12 ডিকোডে সমস্যা হয়।
    run([
        keytool, "-genkeypair",
        "-keystore", out,
        "-storetype", "PKCS12",
        "-alias", alias,
        "-keyalg", "RSA",
        "-keysize", "2048",
        # Pin the certificate signature algorithm: JDK 21 defaults to SHA384withRSA, while
        # SHA256withRSA is what every Android version, apksigner and Play Console expect.
        "-sigalg", "SHA256withRSA",
        "-validity", str(years * 365),
        "-storepass", store_password,
        "-keypass", key_password,
        "-dname", f"CN={common_name},O=BA Screen Record,C=BD",
    ])
    return True


def backend_openssl(out: str, store_password: str, alias: str, key_password: str,
                    common_name: str, years: int) -> bool:
    openssl = shutil.which("openssl")
    if not openssl:
        return False
    key_file = f"{out}.key.pem"
    crt_file = f"{out}.crt.pem"
    run([openssl, "req", "-x509", "-newkey", "rsa:2048", "-nodes",
         "-keyout", key_file, "-out", crt_file,
         "-days", str(years * 365), "-subj", f"/CN={common_name}/O=BA Screen Record/C=BD"])
    run([openssl, "pkcs12", "-export", "-inkey", key_file, "-in", crt_file,
         "-out", out, "-name", alias, "-passout", f"pass:{key_password}"])
    for temp in (key_file, crt_file):
        try:
            os.remove(temp)
        except OSError:
            pass
    return True


def backend_cryptography(out: str, store_password: str, alias: str, key_password: str,
                         common_name: str, years: int) -> bool:
    try:
        import datetime

        from cryptography import x509
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import rsa
        from cryptography.hazmat.primitives.serialization import pkcs12
        from cryptography.x509.oid import NameOID
    except ImportError:
        return False

    key = rsa.generate_private_key(public_exponent=65537, key_size=4096)
    subject = issuer = x509.Name([
        x509.NameAttribute(NameOID.COMMON_NAME, common_name),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, "BA Screen Record"),
        x509.NameAttribute(NameOID.COUNTRY_NAME, "BD"),
    ])
    now = datetime.datetime.now(datetime.timezone.utc)
    certificate = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(issuer)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - datetime.timedelta(days=1))
        .not_valid_after(now + datetime.timedelta(days=365 * years))
        .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
        .sign(key, hashes.SHA256())
    )
    p12 = pkcs12.serialize_key_and_certificates(
        name=alias.encode("utf-8"), key=key, cert=certificate, cas=None,
        encryption_algorithm=serialization.BestAvailableEncryption(
            key_password.encode("utf-8")
        ),
    )
    with open(out, "wb") as handle:
        handle.write(p12)
    return True


def create_keystore(out: str, store_password: str, alias: str, key_password: str,
                    common_name: str, years: int) -> str:
    for name, backend in (
        ("keytool", backend_keytool),
        ("openssl", backend_openssl),
        ("cryptography", backend_cryptography),
    ):
        if backend(out, store_password, alias, key_password, common_name, years):
            return name
    raise RuntimeError(
        "কোনো keystore ব্যাকএন্ড পাওয়া যায়নি। Termux এ চালান: apt install -y openjdk-21"
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="CI-র জন্য release signing keystore তৈরি")
    parser.add_argument("--out", default="my-upload-key.jks")
    parser.add_argument("--store-password", default=None)
    parser.add_argument("--key-password", default=None)
    parser.add_argument("--alias", default="upload")
    parser.add_argument("--common-name", default="BA Screen Record")
    parser.add_argument("--years", type=int, default=30)
    parser.add_argument("--ensure", action="store_true",
                        help="ফাইল থাকলে কিছু করবে না (CI/স্ক্রিপ্টের জন্য নিরাপদ)")
    args = parser.parse_args()

    if os.path.exists(args.out):
        if args.ensure:
            print(f"• keystore আগে থেকেই আছে: {args.out} — নতুন করে তৈরি করা হলো না")
            return 0
        print(f"✗ ফাইল আগে থেকেই আছে: {args.out} — অন্য নাম দিন বা মুছে ফেলুন", file=sys.stderr)
        return 2

    store_password = args.store_password or random_password()
    key_password = args.key_password or store_password

    try:
        used = create_keystore(args.out, store_password, args.alias, key_password,
                               args.common_name, args.years)
    except RuntimeError as exc:
        print(f"✗ {exc}", file=sys.stderr)
        return 1

    with open(args.out, "rb") as handle:
        encoded = base64.b64encode(handle.read()).decode("ascii")

    print(f"✓ keystore তৈরি হয়েছে ({used} দিয়ে): {args.out} "
          f"({os.path.getsize(args.out)} bytes)")
    print()
    print("GitHub → Settings → Secrets and variables → Actions এ এই ৪টি secret যোগ করুন:")
    print(f"  KEY_ALIAS        = {args.alias}")
    print(f"  STORE_PASSWORD   = {store_password}")
    print(f"  KEY_PASSWORD     = {key_password}")
    print("  KEYSTORE_BASE64  = (নিচের পুরো লাইন কপি করুন)")
    print()
    print(encoded)
    print()
    print("⚠ keystore ও পাসওয়ার্ড কোথাও কমিট করবেন না — .gitignore সেগুলো বাদ দেয়।")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())