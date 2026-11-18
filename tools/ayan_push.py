#!/usr/bin/env python3
"""
ayan_push.py — GitHub এ প্রজেক্ট পুশ করার টুল (git ছাড়াই, শুধু Python standard library).

কেন এই টুল:
    Termux / Ayan Agent runtime এ `git` বাইনারি ইনস্টল থাকে না, তাই আমরা সরাসরি
    GitHub REST API (Git Data API) ব্যবহার করে পুরো প্রজেক্টের ফাইলগুলো একটি
    কমিট হিসেবে পুশ করি। Gradle wrapper jar সহ binary ফাইলও base64 হিসেবে ঠিকভাবে যায়।

ব্যবহার:
    python3 tools/ayan_push.py --token ghp_xxx
    python3 tools/ayan_push.py --token ghp_xxx --repo my-recorder --private
    python3 tools/ayan_push.py --token ghp_xxx --branch main --message "fix: mic capture"
    python3 tools/ayan_push.py --dry-run          # শুধু কী কী ফাইল যাবে দেখাবে

টোকেন স্কোপ: classic token হলে `repo` স্কোপ, fine-grained হলে
`Administration: Read and write` + `Contents: Read and write` দরকার।
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import sys
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

API = "https://api.github.com"
USER_AGENT = "ayan-agent-push/1.0"

# এই ডিরেক্টরি/ফাইলগুলো কখনো পুশ করা যাবে না (সিকিউরিটি + সাইজ)
SKIP_DIRS = {
    ".git",
    ".gradle",
    ".kotlin",
    ".idea",
    "build",
    "out",
    "output",
    "ci-artifacts",
    "__pycache__",
    ".DS_Store",
}
SKIP_EXT = {
    ".apk", ".aab", ".ap_", ".dex", ".jks", ".keystore", ".p12", ".pepk", ".log",
}
SKIP_NAMES = {
    ".env", "local.properties", "keystore.properties", "my-upload-key.jks",
    "debug.keystore", ".DS_Store", "Thumbs.db", "secrets.json",
}
# `.env.example` / `.env.sample` হলো টেমপ্লেট — commit করা উচিত (আসল `.env` কখনো নয়)
ENV_TEMPLATE_NAMES = {".env.example", ".env.sample", ".env.template", ".env.dist"}
MAX_FILE_BYTES = 40 * 1024 * 1024  # GitHub blob limit এর নিরাপদ সীমা

# এই ফাইলগুলো GitHub-এ executable (100755) হিসেবে যেতে হবে, নাহলে Actions-এ ./gradlew fail করবে।
EXECUTABLE_NAMES = {"gradlew", "gradlew.bat"}


class GitHubError(RuntimeError):
    pass


class GitHub:
    def __init__(self, token: str) -> None:
        self.token = token.strip()

    def request(self, method: str, path: str, payload: dict | None = None) -> dict:
        url = path if path.startswith("http") else f"{API}{path}"
        data = None
        if payload is not None:
            data = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("Authorization", f"Bearer {self.token}")
        req.add_header("Accept", "application/vnd.github+json")
        req.add_header("X-GitHub-Api-Version", "2022-11-28")
        req.add_header("User-Agent", USER_AGENT)
        if data is not None:
            req.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                body = resp.read().decode("utf-8")
                return json.loads(body) if body else {}
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", "replace")
            raise GitHubError(f"{method} {path} -> HTTP {exc.code}\n{detail}") from exc
        except urllib.error.URLError as exc:
            raise GitHubError(f"{method} {path} -> network error: {exc.reason}") from exc

    def get(self, path: str) -> dict:
        return self.request("GET", path)

    def get_or_none(self, path: str) -> dict | None:
        try:
            return self.request("GET", path)
        except GitHubError as exc:
            if "HTTP 404" in str(exc):
                return None
            raise


def collect_files(root: str) -> list[tuple[str, str]]:
    """(relative_posix_path, absolute_path) — ignored ফাইল বাদ দিয়ে।"""
    collected: list[tuple[str, str]] = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = sorted(
            d for d in dirnames if d not in SKIP_DIRS and d != ".git"
        )
        for name in sorted(filenames):
            if name in SKIP_NAMES:
                continue
            # `.env` ও `.env.local` ইত্যাদি বাদ, কিন্তু `.env.example` টেমপ্লেট রাখা হবে
            if name.startswith(".env") and name not in ENV_TEMPLATE_NAMES:
                continue
            ext = os.path.splitext(name)[1].lower()
            if ext in SKIP_EXT:
                continue
            abs_path = os.path.join(dirpath, name)
            try:
                if os.path.getsize(abs_path) > MAX_FILE_BYTES:
                    print(f"  ! skip (too large): {abs_path}")
                    continue
            except OSError:
                continue
            rel = os.path.relpath(abs_path, root).replace(os.sep, "/")
            collected.append((rel, abs_path))
    return collected


def ensure_repo(gh: GitHub, owner: str, repo: str, private: bool) -> str:
    existing = gh.get_or_none(f"/repos/{owner}/{repo}")
    if existing:
        print(f"• রিপো আগে থেকেই আছে: {existing['full_name']}")
        return existing["full_name"]
    print(f"• নতুন রিপো তৈরি হচ্ছে: {owner}/{repo} (private={private})")
    created = gh.request(
        "POST",
        "/user/repos",
        {
            "name": repo,
            "private": private,
            "description": "BA Screen Record — 2K screen recorder with live draw overlay",
            "has_issues": True,
            "has_wiki": False,
            "auto_init": False,
        },
    )
    return created["full_name"]


def repo_is_empty(gh: GitHub, full_name: str) -> bool:
    """GitHub empty রিপোতে `/git/refs` ডাকলে 409 দেয় — সেটাই খালি রিপোর নির্ভরযোগ্য সংকেত।"""
    try:
        gh.get(f"/repos/{full_name}/git/refs")
        return False
    except GitHubError as exc:
        if "HTTP 409" in str(exc):
            return True
        raise


def bootstrap_empty_repo(
    gh: GitHub, full_name: str, branch: str, seed_path: str, seed_bytes: bytes
) -> None:
    """
    খালি রিপোতে Git Data API কাজ করে না (409 Git Repository is empty)।
    তাই Contents API দিয়ে প্রথম ফাইলটি লিখে রিপো bootstrap করা হয় —
    এতে default branch + প্রথম কমিট তৈরি হয়, তারপর বাকি সব ফাইল
    Git Data API দিয়ে পুশ করা যায় (executable mode সহ)।
    """
    print(f"• খালি রিপো — bootstrap হচ্ছে ({seed_path} দিয়ে)")
    gh.request(
        "PUT",
        f"/repos/{full_name}/contents/{seed_path}",
        {
            "message": "chore: bootstrap repository",
            "content": base64.b64encode(seed_bytes).decode("ascii"),
            "branch": branch,
        },
    )


def set_actions_secret(gh: GitHub, full_name: str, name: str, value: str) -> None:
    """রিপো Actions secret এনক্রিপ্ট করে সেট করে (libsodium sealed box)।"""
    import sealed_box  # লোকাল মডিউল (tools/sealed_box.py)

    key_info = gh.get(f"/repos/{full_name}/actions/secrets/public-key")
    encrypted = sealed_box.seal_for_github(value, key_info["key"])
    gh.request(
        "PUT",
        f"/repos/{full_name}/actions/secrets/{name}",
        {"encrypted_value": encrypted, "key_id": key_info["key_id"]},
    )
    print(f"   secret সেট হয়েছে: {name}")


def main() -> int:
    parser = argparse.ArgumentParser(description="GitHub এ প্রজেক্ট পুশ করুন (git ছাড়াই)")
    parser.add_argument("--token", default=os.environ.get("GITHUB_TOKEN", ""),
                        help="GitHub personal access token (অথবা GITHUB_TOKEN env)")
    parser.add_argument("--repo", default="ba-screen-record", help="রিপো নাম")
    parser.add_argument("--branch", default="main", help="ব্রাঞ্চ নাম (default: main)")
    parser.add_argument("--message", default="chore: sync from Ayan Agent",
                        help="কমিট মেসেজ")
    parser.add_argument("--public", action="store_true", help="পাবলিক রিপো (default: private)")
    parser.add_argument("--root", default=".", help="প্রজেক্ট রুট")
    parser.add_argument("--dry-run", action="store_true", help="শুধু ফাইল লিস্ট দেখান")
    parser.add_argument(
        "--tag",
        default="",
        help="পুশ শেষে এই ট্যাগ তৈরি করে Release workflow ট্রিগার করুন (যেমন v1.0.0)",
    )
    parser.add_argument(
        "--secrets-file",
        default="",
        help="GitHub Secrets সেট করার JSON ফাইল (রিপো Actions secrets এনক্রিপ্ট করে আপলোড হবে)",
    )
    args = parser.parse_args()

    root = os.path.abspath(args.root)
    files = collect_files(root)
    print(f"• {len(files)} টি ফাইল পুশ হবে ({root})")

    if args.dry_run:
        for rel, _ in files:
            print(f"   - {rel}")
        return 0

    token = args.token.strip()
    if not token:
        print("✗ টোকেন দরকার। ব্যবহার: --token ghp_xxx অথবা GITHUB_TOKEN env", file=sys.stderr)
        return 2

    gh = GitHub(token)
    try:
        user = gh.get("/user")
        owner = user["login"]
        print(f"• লগইন সফল: {owner}")

        full_name = ensure_repo(gh, owner, args.repo, private=not args.public)

        # ০) রিপো একদম খালি হলে Git Data API 409 দেয় — Contents API দিয়ে bootstrap
        if repo_is_empty(gh, full_name):
            seed_rel, seed_abs = next(
                ((r, a) for r, a in files if r == "gradlew"),
                files[0],
            )
            with open(seed_abs, "rb") as handle:
                seed_bytes = handle.read()
            bootstrap_empty_repo(gh, full_name, args.branch, seed_rel, seed_bytes)

        # ১) প্রতিটি ফাইলের blob তৈরি
        tree_entries: list[dict] = []
        for index, (rel, abs_path) in enumerate(files, start=1):
            with open(abs_path, "rb") as handle:
                raw = handle.read()
            blob = gh.request(
                "POST",
                f"/repos/{full_name}/git/blobs",
                {"content": base64.b64encode(raw).decode("ascii"), "encoding": "base64"},
            )
            tree_entries.append(
                {
                    "path": rel,
                    "mode": "100755" if os.path.basename(rel) in EXECUTABLE_NAMES else "100644",
                    "type": "blob",
                    "sha": blob["sha"],
                }
            )
            if index % 25 == 0 or index == len(files):
                print(f"   blob {index}/{len(files)} আপলোড হয়েছে")

        # ২) আগের কমিট থাকলে তার উপর ভিত্তি করে tree, নাহলে নতুন tree
        #    নোট: GitHub API-তে GET এর জন্য `git/ref/<ref>`, কিন্তু
        #    PATCH এর জন্য `git/refs/<ref>` (বহুবচন) — নাহলে 404 আসে।
        ref_get_path = f"/repos/{full_name}/git/ref/heads/{args.branch}"
        ref_patch_path = f"/repos/{full_name}/git/refs/heads/{args.branch}"
        existing_ref = gh.get_or_none(ref_get_path)
        parent_sha = existing_ref["object"]["sha"] if existing_ref else None

        tree_payload: dict = {"tree": tree_entries}
        if parent_sha:
            parent_commit = gh.get(f"/repos/{full_name}/git/commits/{parent_sha}")
            tree_payload["base_tree"] = parent_commit["tree"]["sha"]

        tree = gh.request("POST", f"/repos/{full_name}/git/trees", tree_payload)

        # ৩) কমিট তৈরি
        commit_payload: dict = {"message": args.message, "tree": tree["sha"]}
        if parent_sha:
            commit_payload["parents"] = [parent_sha]
        commit = gh.request("POST", f"/repos/{full_name}/git/commits", commit_payload)

        # ৪) রেফারেন্স তৈরি / আপডেট
        if existing_ref:
            gh.request("PATCH", ref_patch_path, {"sha": commit["sha"], "force": False})
            print(f"• ব্রাঞ্চ আপডেট হয়েছে: {args.branch}")
        else:
            gh.request(
                "POST",
                f"/repos/{full_name}/git/refs",
                {"ref": f"refs/heads/{args.branch}", "sha": commit["sha"]},
            )
            print(f"• নতুন ব্রাঞ্চ তৈরি হয়েছে: {args.branch}")

        print()
        print("✓ পুশ সম্পন্ন")
        print(f"  রিপো     : https://github.com/{full_name}")
        print(f"  কমিট     : {commit['sha'][:12]}")
        print(f"  Actions  : https://github.com/{full_name}/actions")

        # ঐচ্ছিক: GitHub Secrets সেট করা (এক কমান্ডেই সম্পূর্ণ রিলিজ সেটআপ)
        if args.secrets_file:
            import json as _json

            with open(args.secrets_file, encoding="utf-8") as handle:
                secrets_map = _json.load(handle)
            if not isinstance(secrets_map, dict) or not secrets_map:
                print("✗ secrets ফাইল খালি বা ভুল ফরম্যাট ({\"KEY\": \"value\"} দরকার)", file=sys.stderr)
                return 2
            print(f"• {len(secrets_map)} টি secret সেট হচ্ছে…")
            for secret_name, secret_value in secrets_map.items():
                set_actions_secret(gh, full_name, str(secret_name), str(secret_value))

        # ৫) ঐচ্ছিক: ট্যাগ তৈরি করে signed release APK বিল্ড ট্রিগার করা
        if args.tag:
            tag_name = args.tag.strip()
            tag_ref = f"refs/tags/{tag_name}"
            existing_tag = gh.get_or_none(f"/repos/{full_name}/git/ref/tags/{tag_name}")
            if existing_tag:
                print(f"• ট্যাগ আগে থেকেই আছে: {tag_name} (ছোঁয়া হলো না)")
            else:
                gh.request(
                    "POST",
                    f"/repos/{full_name}/git/refs",
                    {"ref": tag_ref, "sha": commit["sha"]},
                )
                print(f"• ট্যাগ তৈরি হয়েছে: {tag_name} → Release workflow শুরু হবে")

        print()
        print("পরবর্তী ধাপ (রিলিজ APK বিল্ডের জন্য) — Settings → Secrets and variables → Actions:")
        print("  KEYSTORE_BASE64  (keystore ফাইলের base64)")
        print("  STORE_PASSWORD")
        print("  KEY_ALIAS")
        print("  KEY_PASSWORD")
        print("তারপর ট্যাগ পুশ করলেই signed release APK তৈরি হবে: v1.0.0")
        return 0
    except GitHubError as exc:
        print(f"✗ GitHub API ত্রুটি:\n{exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())