#!/usr/bin/env python3
"""
release_setup.py — এক কমান্ডে সম্পূর্ণ signed release APK সেটআপ।

কী করে:
    ১) ছবির জন্য একটি fresh upload keystore তৈরি করে (tools/make_keystore.py ব্যবহার করে)
    ২) keystore এর base64 + পাসওয়ার্ড দিয়ে secrets.json বানায়
    ৩) GitHub-এ সব ফাইল পুশ করে (tools/ayan_push.py)
    ৪) Actions secrets আপলোড করে (এনক্রিপ্টেড)
    ৫) ট্যাগ তৈরি করে Release workflow ট্রিগার করে → signed, minified APK

ব্যবহার:
    python3 tools/release_setup.py --token ghp_xxx --repo ba-screen-record --version 1.0.0

সতর্কতা:
    * keystore ফাইলটি (my-upload-key.jks) ও secrets.json কখনো কমিট/শেয়ার করবেন না।
      এগুলো .gitignore-এ আগে থেকেই বাদ দেওয়া আছে।
    * keystore হারালে Play Store-এ অ্যাপ আপডেট করা যাবে না — নিরাপদে ব্যাকআপ রাখুন।
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import subprocess
import sys

TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS_DIR)


def sh(cmd: list[str]) -> None:
    print("  $", " ".join(cmd))
    subprocess.run(cmd, cwd=ROOT, check=True)


def main() -> int:
    parser = argparse.ArgumentParser(description="এক কমান্ডে signed release APK সেটআপ")
    parser.add_argument("--token", default=os.environ.get("GITHUB_TOKEN", ""), help="GitHub token")
    parser.add_argument("--repo", default="ba-screen-record", help="রিপো নাম")
    parser.add_argument("--version", default="1.0.0", help="রিলিজ ভার্সন (যেমন 1.0.0)")
    parser.add_argument("--keystore", default="my-upload-key.jks", help="keystore ফাইল পাথ")
    parser.add_argument("--alias", default="upload", help="key alias")
    parser.add_argument(
        "--password",
        default=os.environ.get("KEYSTORE_PASSWORD", ""),
        help="keystore + key পাসওয়ার্ড (না দিলে র‍্যান্ডম তৈরি হবে)",
    )
    parser.add_argument("--public", action="store_true", help="পাবলিক রিপো")
    parser.add_argument("--skip-push", action="store_true", help="শুধু keystore/secrets তৈরি করুন")
    parser.add_argument("--dry-run", action="store_true", help="কিছু পরিবর্তন না করে দেখান")
    args = parser.parse_args()

    ks_path = os.path.join(ROOT, args.keystore)
    password = args.password or base64.urlsafe_b64encode(os.urandom(18)).decode().rstrip("=")

    print("=== ১) keystore তৈরি ===")
    if os.path.exists(ks_path):
        print(f"• আগে থেকেই আছে: {args.keystore} (নতুন করে তৈরি হচ্ছে না)")
    else:
        cmd = [
            sys.executable,
            os.path.join(TOOLS_DIR, "make_keystore.py"),
            "--out",
            ks_path,
            "--alias",
            args.alias,
            "--store-password",
            password,
            "--key-password",
            password,
        ]
        if args.dry_run:
            print("  (dry-run) $", " ".join(cmd))
        else:
            sh(cmd)

    print("=== ২) secrets ফাইল তৈরি ===")
    if args.dry_run:
        print("  (dry-run) secrets.json তৈরি হবে (keystore base64 সহ)")
    elif os.path.exists(ks_path):
        with open(ks_path, "rb") as handle:
            ks_b64 = base64.b64encode(handle.read()).decode("ascii")
        secrets_path = os.path.join(ROOT, "secrets.json")
        payload = {
            "KEYSTORE_BASE64": ks_b64,
            "STORE_PASSWORD": password,
            "KEY_ALIAS": args.alias,
            "KEY_PASSWORD": password,
        }
        with open(secrets_path, "w", encoding="utf-8") as handle:
            json.dump(payload, handle, indent=2)
        os.chmod(secrets_path, 0o600)
        print(f"• তৈরি হয়েছে: secrets.json (chmod 600)")
        print(f"• keystore পাসওয়ার্ড (সংরক্ষণ করুন!): {password}")

    if args.skip_push:
        print("\n✓ keystore ও secrets প্রস্তুত। এখন পুশ করতে --token দিন।")
        return 0

    if not args.token:
        print("\n✗ পুশ ও secrets আপলোডের জন্য --token দরকার।", file=sys.stderr)
        return 2

    print("=== ৩–৫) পুশ + secrets + ট্যাগ ===")
    push_cmd = [
        sys.executable,
        os.path.join(TOOLS_DIR, "ayan_push.py"),
        "--token",
        args.token,
        "--repo",
        args.repo,
        "--tag",
        f"v{args.version}",
        "--message",
        f"release: v{args.version} — mic capture fix + signed release pipeline",
    ]
    if os.path.exists(os.path.join(ROOT, "secrets.json")):
        push_cmd += ["--secrets-file", os.path.join(ROOT, "secrets.json")]
    if args.public:
        push_cmd.append("--public")
    if args.dry_run:
        push_cmd.append("--dry-run")

    if args.dry_run:
        print("  (dry-run) $", " ".join(push_cmd))
        return 0
    sh(push_cmd)

    print()
    print("✓ সম্পূর্ণ! কিছুক্ষণ পর signed APK এখানে পাবেন:")
    print(f"  https://github.com/<owner>/{args.repo}/releases")
    print()
    print("⚠️ নিশ্চিত করুন secrets.json ও my-upload-key.jks কখনো পাবলিক হয়নি।")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())