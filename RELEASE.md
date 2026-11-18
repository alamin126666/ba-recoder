# BA Screen Record — Release, CI/CD ও Signing গাইড

এই ডকুমেন্টে আছে: signed release APK বিল্ড, GitHub Actions সেটআপ, secret কনফিগারেশন,
এবং অ্যাপ পুশ করার ধাপগুলো।

---

## ১. ভয়েস রেকর্ডিং ফিক্স (v1.0 এর পরের পরিবর্তন)

### মূল সমস্যা (root cause)

Android 14 (API 34) থেকে ফোরগ্রাউন্ড সার্ভিসে মাইক্রোফোন ব্যবহার করতে **দুটি** জিনিস লাগে:

1. `FOREGROUND_SERVICE_MICROPHONE` পারমিশন
2. সার্ভিসে `android:foregroundServiceType="microphone"` এবং `startForeground()`-এ
   `FOREGROUND_SERVICE_TYPE_MICROPHONE` ফ্ল্যাগ

আগের কোডে শুধু `mediaProjection` টাইপ ছিল। ফলে ইউজার হোম স্ক্রিনে গেলে (যা স্ক্রিন
রেকর্ডিংয়ের স্বাভাবিক অংশ) সিস্টেম চুপচাপ মাইক কেটে দিত — কখনো ভয়েস থাকত, কখনো থাকত না।

### যা ঠিক করা হয়েছে

| ফাইল | পরিবর্তন |
|---|---|
| `AndroidManifest.xml` | `FOREGROUND_SERVICE_MICROPHONE` পারমিশন + `foregroundServiceType="mediaProjection\|microphone"` |
| `ScreenRecordService.kt` | `startForeground()`-এ মাইক টাইপ যোগ (API 30+), `EXTRA_ENABLE_MIC` ইনটেন্ট ফ্ল্যাগ |
| `ScreenRecordManager.kt` | অডিও সোর্স ফলব্যাক চেইন: `CAMCORDER` → `MIC` → `MIC (mono)` → `DEFAULT` → ভিডিও-only, প্রতিটি ধাপে রিসোর্স রিলিজ |
| `HomeViewModel.kt` / `MainActivity.kt` | মাইক পারমিশন যাচাই করে ফ্ল্যাগ সার্ভিসে পাঠানো |

এখন ভয়েস ক্যাপচার কোনো অবস্থাতেই চুপচাপ বন্ধ হবে না — শুধু মাইক পারমিশন না থাকলে
ইউজার স্পষ্ট মেসেজ পাবেন।

---

## ২. এক কমান্ডে সম্পূর্ণ রিলিজ সেটআপ (সবচেয়ে সহজ)

শুধু GitHub টোকেন দিন — বাকি সব (keystore, secrets, পুশ, ট্যাগ) স্বয়ংক্রিয়:

```bash
python3 tools/release_setup.py --token ghp_xxxxxxxxxxxx --repo ba-screen-record --version 1.0.0
```

এটি ধারাবাহিকভাবে করে:

1. `my-upload-key.jks` (RSA 2048, 30 বছর) তৈরি করে `keytool` দিয়ে
2. `secrets.json` বানায় (keystore base64 + পাসওয়ার্ড, `chmod 600`)
3. সব ফাইল GitHub-এ পুশ করে (private রিপো)
4. ৪টি Actions secret **এনক্রিপ্ট করে** আপলোড করে (libsodium sealed box)
5. `v1.0.0` ট্যাগ তৈরি করে → Release workflow চালু → signed + minified APK

শুধু keystore/secrets তৈরি করতে চাইলে:

```bash
python3 tools/release_setup.py --skip-push
```

> ⚠️ `my-upload-key.jks` ও `secrets.json` অবশ্যই গোপন রাখুন এবং ব্যাকআপ রাখুন।
> keystore হারালে একই অ্যাপের আপডেট আর প্রকাশ করা যাবে না।

---

## ৩. আলাদা আলাদা ধাপে পুশ করা (git ছাড়াই)

এই রানটাইমে `git` নেই, তাই GitHub REST API ব্যবহার করা হয়।

```bash
# ১) টোকেন সহ পুশ (private রিপো ডিফল্ট)
python3 tools/ayan_push.py --token ghp_xxxxxxxxxxxx --repo ba-screen-record

# ২) কী কী ফাইল যাবে শুধু দেখতে
python3 tools/ayan_push.py --dry-run

# ৩) পাবলিক রিপো চাইলে
python3 tools/ayan_push.py --token ghp_xxx --repo ba-screen-record --public

# ৪) পুশ + secrets আপলোড + ট্যাগ (সম্পূর্ণ রিলিজ)
python3 tools/ayan_push.py --token ghp_xxx --secrets-file secrets.json --tag v1.0.0
```

`ayan_push.py` এর বৈশিষ্ট্য:

- `gradlew` স্বয়ংক্রিয়ভাবে executable (100755) মোডে পুশ হয় — Actions-এ `Permission denied` হবে না
- `.env`, `*.jks`, `secrets.json`, `*.apk`, `build/` ইত্যাদি স্বয়ংক্রিয়ভাবে বাদ যায়
- বড় ফাইল (>40 MB) এড়িয়ে যায়

**টোকেন স্কোপ:**

- Classic token → `repo` স্কোপ
- Fine-grained token → `Administration: Read and write` + `Contents: Read and write`

---

## ৪. Signing keystore তৈরি

```bash
# keytool (JDK), openssl বা cryptography — যেটা পাওয়া যায় সেটাই ব্যবহার হবে
python3 tools/make_keystore.py --out my-upload-key.jks --alias upload
```

আউটপুটে ৪টি secret ভ্যালু ও `KEYSTORE_BASE64` প্রিন্ট হবে। **keystore ফাইল বা
পাসওয়ার্ড কখনো কমিট করবেন না** — `.gitignore` এগুলো বাদ দেয়।

> ⚠ এই keystore হারালে আর একই অ্যাপ আপডেট করা যাবে না। নিরাপদ জায়গায় ব্যাকআপ রাখুন।

---

## ৫. GitHub Secrets যোগ করা

রিপো → **Settings → Secrets and variables → Actions → New repository secret**

| Secret | মান |
|---|---|
| `KEYSTORE_BASE64` | keystore ফাইলের base64 (এক লাইনে) |
| `STORE_PASSWORD` | keystore পাসওয়ার্ড |
| `KEY_ALIAS` | `upload` |
| `KEY_PASSWORD` | key পাসওয়ার্ড (সাধারণত store পাসওয়ার্ডের সমান) |

`KEYSTORE_BASE64` তৈরি করার কমান্ড:

```bash
base64 -w0 my-upload-key.jks > keystore.b64   # Linux
# Termux এ:
python3 -c "import base64;print(base64.b64encode(open('my-upload-key.jks','rb').read()).decode())"
```

---

## ৬. Release APK বিল্ড

দুইভাবে চালানো যায়:

**ক) ট্যাগ পুশ করে (স্বয়ংক্রিয়)**

```bash
# ট্যাগ তৈরি করতে (git থাকলে)
git tag v1.0.0 && git push origin v1.0.0
```

ট্যাগ না থাকলে Actions ট্যাব → **Release APK** → **Run workflow** → version name লিখে চালান।

**খ) Workflow থেকে ম্যানুয়ালি**

`version_name = 1.0.1`, `version_code = 2` দিয়ে Run workflow।

বিল্ড শেষে:

- signed APK → GitHub **Releases** এ আপলোড হবে
- একই APK → Actions artifacts (`release-apk`) এ ৯০ দিন থাকবে
- বিল্ড লগে `apksigner verify` এর ফল ও SHA-256 দেখাবে

---

## ৭. CI (প্রতিটি পুশে)

`.github/workflows/ci.yml` প্রতিটি পুশ/PR-এ চালায়:

1. `assembleDebug` — কম্পাইল যাচাই
2. `testDebugUnitTest` — ইউনিট টেস্ট (JUnit + Robolectric)
3. `lintDebug` — Android lint

টেস্ট রিপোর্ট artifact হিসেবে সংরক্ষিত হয়।

---

## ৮. লোকাল বিল্ড (ঐচ্ছিক)

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk
export SIGNING_REQUIRED=true
export KEYSTORE_PATH=/path/to/my-upload-key.jks
export STORE_PASSWORD=... KEY_ALIAS=upload KEY_PASSWORD=...
export VERSION_NAME=1.0.1 VERSION_CODE=2
./gradlew :app:assembleRelease
```

আউটপুট: `app/build/outputs/apk/release/app-release.apk`

> **নোট:** এই ডিভাইসে (ARM) সরাসরি Android SDK ইন্সটল করা যায় না (Google-এর
> `sdkmanager` বাইনারি এখন x86_64-only), তাই অপ্টিমাইজড বিল্ড GitHub Actions-এই হবে।

---

## ৯. অপ্টিমাইজেশন যা ইতিমধ্যে চালু

- R8 minify + resource shrink (`isMinifyEnabled`, `isShrinkResources`)
- `-repackageclasses`, `-allowaccessmodification`, ৫টি optimization pass
- `assumenosideeffects` দিয়ে release বিল্ডে `Log` কল সম্পূর্ণ বাদ
- `dependenciesInfo.includeInApk = false` (Play metadata APK-তে যায় না)
- Gradle configuration cache + build cache + parallel execution

---

## ১০. সমস্যা হলে

| সমস্যা | সমাধান |
|---|---|
| `Missing repository secret KEYSTORE_BASE64` | Secret নাম ঠিক আছে কিনা দেখুন, base64 এক লাইনে হতে হবে |
| `Decoded keystore is empty` | base64 আবার তৈরি করুন (newline ছাড়া) |
| `Release signing keystore not found` | `KEYSTORE_BASE64` সঠিকভাবে decode হচ্ছে না |
| `apksigner verify` ফেল | `STORE_PASSWORD` / `KEY_PASSWORD` মিলছে না |
| বিল্ডে SDK পাওয়া যাচ্ছে না | workflow-এর sdkmanager ধাপের লগ দেখুন |

---

## ১১. ব্যানার ও README আর্টওয়ার্ক

README-এর তিনটি ব্যানার **হাতে আঁকা নয়** — কোড থেকে তৈরি। ব্র্যান্ড রং সরাসরি অ্যাপের `app/src/main/res/values/colors.xml` থেকে পড়া হয়, তাই আর্টওয়ার্ক আর চলমান UI কখনো আলাদা হয়ে যেতে পারে না।

```bash
pip install pillow
python3 tools/make_banners.py
```

| ফাইল | মাপ | কোথায় ব্যবহৃত |
|---|---|---|
| `docs/banners/hero.png` | ১৬০০ × ৬৪০ | README হেডার |
| `docs/banners/features.png` | ১৬০০ × ৪৬০ | ফিচার স্ট্রিপ |
| `docs/banners/social-preview.png` | ১২৮০ × ৬৪০ | GitHub social preview |

### রং পরিবর্তন করতে চাইলে

`tools/make_banners.py`-এর উপরের দিকে `INK`, `EMERALD`, `MINT` — এই টোকেনগুলো বদলান, তারপর আবার চালান। অ্যাপের রংও একসাথে বদলালে দুই জায়গায় মিল থাকবে।

### Social preview সেট করা

GitHub API দিয়ে এই সেটিং বদলানো যায় না। একবার হাতে করতে হবে:

```text
Settings → General → Social preview → Upload
 docs/banners/social-preview.png
```