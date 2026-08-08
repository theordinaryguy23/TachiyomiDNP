# TachiyomiDNP

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://github.com/theordinaryguy23/TachiyomiDNP)
[![Based on](https://img.shields.io/badge/based%20on-TachiyomiJ2K-green.svg)](https://github.com/Jays2Kings/tachiyomiJ2K)
[![Min SDK](https://img.shields.io/badge/minSdk-26%20(Android%208.0)-orange.svg)](https://developer.android.com/about/versions/oreo)
[![Target SDK](https://img.shields.io/badge/targetSdk-36%20(Android%2016)-orange.svg)](https://developer.android.com/about/versions)
[![Extensions](https://img.shields.io/badge/extensions-Keiyoushi%20%7C%20Mihon%20compatible-blue.svg)](https://github.com/keiyoushi/extensions-source)

A free and open source manga reader for **Android 8.0 (Oreo) and above**.

> **TachiyomiDNP** is based on the [TachiyomiJ2K](https://github.com/Jays2Kings/tachiyomiJ2K) project — a feature-rich fork of the original [Tachiyomi](https://github.com/tachiyomiorg/tachiyomi) manga reader. This build continues the TachiyomiJ2K legacy with additional optimizations and custom branding.

---

## ⚠️ Upgrading to 1.9.0 — minSdk raised to 26 (Android 8.0)

**Android 6.0 and 7.x are no longer supported.**

This was required to fix extension loading. Modern Keiyoushi/Mihon extensions
declare shared libraries (kotlinx.serialization, OkHttp, Jsoup, …) as `compileOnly`
and resolve them from the host app at runtime. Below API 26, the D8 compiler
desugars Java 8 interface default methods into synthetic `$-CC` classes and strips
the default body from the interface. `kotlinx.serialization`'s
`GeneratedSerializer.typeParametersSerializers()` is one such method — so every
extension using `@Serializable` crashed with:

```text
java.lang.AbstractMethodError: abstract method
"kotlinx.serialization.KSerializer[]
 kotlinx.serialization.internal.GeneratedSerializer.typeParametersSerializers()"
on receiver java.lang.Class<g1>
```

Raising `minSdk` to 26 — matching [Mihon](https://github.com/mihonapp/mihon) and
[Keiyoushi](https://github.com/keiyoushi/extensions-source) — restores the default
method and fixes MangaDex and every other serialization-using source. There is no
host-side workaround; the method body is deleted at compile time.

Full analysis: **[docs/EXTENSION_RUNTIME.md](docs/EXTENSION_RUNTIME.md)**.

If you are on Android 6.0/7.x, stay on **v1.8.8** — but be aware most current
extensions will not work there.

---

## Minimum Requirements

| Requirement | Minimum | Recommended |
|-------------|---------|-------------|
| **Android** | 8.0 (API 26) Oreo | 10.0+ (API 29) |
| **RAM** | 2 GB | 4 GB+ |
| **Storage** | 100 MB (app) + space for downloads | 500 MB+ |
| **CPU** | ARMv7 / ARM64 / x86_64 | ARM64 (arm64-v8a) |
| **Screen** | Any | 5.5"+ for best reading |

### Supported Architectures
- `arm64-v8a` (recommended — most modern devices)
- `armeabi-v7a` (older 32-bit ARM devices)
- `x86_64` (emulators, some tablets)
- `x86` (legacy emulators)

### APK Variants
| Variant | Architecture | Size | Notes |
|---------|-------------|------|-------|
| `TachiyomiDNP-1.9.0-release-arm64-v8a.apk` | arm64-v8a | ~35 MB | **Recommended** for most devices |
| `TachiyomiDNP-1.9.0-release-armeabi-v7a.apk` | armeabi-v7a | ~30 MB | Older 32-bit devices |
| `TachiyomiDNP-1.9.0-release-x86_64.apk` | x86_64 | ~38 MB | Emulators |
| `TachiyomiDNP-1.9.0-release-x86.apk` | x86 | ~32 MB | Legacy emulators |
| `TachiyomiDNP-1.9.0-release-universal.apk` | All | ~90 MB | Universal (all ABIs) |

---

## Features

### Core
- [x] No ads, ever
- [x] Online reading from a variety of sources via extensions
- [x] Local reading of downloaded content
- [x] Configurable reader with multiple viewers, reading directions, and display settings
- [x] Automatic light and dark themes
- [x] Schedule library updates for new chapters
- [x] Create backups locally or to cloud storage

### Library Management
- [x] Categories to organize your library
- [x] Library redesigned as a single list view with collapsible categories
- [x] Staggered library grid
- [x] Drag & drop sorting in library
- [x] Dynamic categories — group by tags, tracking status, source, and more
- [x] New Recents page for quick access to newly added manga and chapters

### Tracking
- [x] [MyAnimeList](https://myanimelist.net/)
- [x] [AniList](https://anilist.co/)
- [x] [Kitsu](https://kitsu.io/)
- [x] [Shikimori](https://shikimori.one)
- [x] [Manga Updates](https://www.mangaupdates.com/)

### TachiyomiJ2K Enhancements
- [x] New manga details screens, themed by manga covers
- [x] Combine 2 pages while reading for better tablet experience
- [x] Expanded toolbar for easier one-handed use
- [x] Floating search bar for quick library/source search
- [x] Stats page
- [x] Dynamic shortcuts — open latest chapter from home screen
- [x] Material You additions
- [x] Batch auto-source migration
- [x] View all chapters right in the reader

---

## Extensions

TachiyomiDNP loads standard **Keiyoushi / Mihon-compatible** extensions.

### Supported repository formats

| Format | Supported | Notes |
|---|---|---|
| `repo.json` (with `index_v2`) | ✅ | Preferred; resolves the protobuf index |
| `index.pb` | ✅ | Protobuf, gzip-aware |
| `index.min.json` | ✅ | Legacy fallback |
| Direct index URLs | ✅ | A URL ending in `index.pb` is used verbatim — never `index.pb/index.pb` |
| Gzip-compressed indexes | ✅ | Detected and inflated automatically |

Adding a repo URL that points at a directory *or* directly at an index file both
work. Invalid repositories fail gracefully and fall through to the next format
instead of breaking the extension subsystem.

### Supported extension library versions

`1.4` and `1.6` — identical to Mihon. Extensions declaring anything else are
rejected **before** loading, with an explanatory message rather than a crash.

### Host-provided runtime

Extensions do not bundle shared libraries; the host provides them. These versions
are pinned and enforced by tests:

| Component | Version |
|---|---|
| kotlinx.serialization | 1.11.0 |
| OkHttp | 5.4.0 |
| okhttp-brotli | 5.4.0 |
| okhttp-zstd + zstd-kmp-okio | 5.4.0 / 0.4.0 |
| Extension library | 1.4, 1.6 |
| minSdk | 26 |

See [docs/EXTENSION_RUNTIME.md](docs/EXTENSION_RUNTIME.md) for the full ownership
matrix and the classloader policy.

### Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `AbstractMethodError … GeneratedSerializer` | App built with minSdk < 26 | Update to v1.9.0+ |
| "Unknown error" / 0 chapters on a source that browses fine | Host called the legacy chapter API, which libVersion 1.6 extensions define as a throwing stub | Fixed in v1.9.0 — host now uses `getMangaUpdate` |
| Extension shows "not supported" | `libVersion` outside 1.4/1.6 | Update the extension |
| Source loads forever behind Cloudflare | Geo-block mistaken for a challenge | Fixed in v1.9.0 — WebView now only opens on a real challenge page |
| Corrupt/blank pages on some sources | zstd responses were not really decompressed | Fixed in v1.9.0 — real Zstd decoder now shipped |

Collect logs for a bug report with:

```bash
adb logcat -c
adb logcat | grep -E 'AbstractMethodError|NoSuchMethod|NoClassDef|ClassNotFound|GeneratedSerializer'
```

---

## Download

Get the latest APK from the [Releases](https://github.com/theordinaryguy23/TachiyomiDNP/releases) page.

1. Download the `arm64-v8a` APK (recommended for most devices)
2. Enable "Install from unknown sources" in your device settings
3. Open the APK file and install

---

## Building from Source

### Requirements
- **JDK 17** (OpenJDK recommended)
- **Android SDK** (compileSdk 36, build-tools 36.0.0)
- **NDK** 23.1.7779620
- **Gradle** 8.x (via wrapper — included)
- At least **4 GB RAM** for Gradle daemon

### Environment Setup
```bash
# Ensure Java 17 is installed
java -version  # Should show 17.x.x

# If needed, set JAVA_HOME to your JDK 17 installation path
# export JAVA_HOME=/path/to/jdk-17
# export PATH=$JAVA_HOME/bin:$PATH
```

### Build
```bash
git clone https://github.com/theordinaryguy23/TachiyomiDNP.git
cd TachiyomiDNP

# Run the extension runtime compatibility tests first
./gradlew :app:testStandardDebugUnitTest

# Build release APK (recommended)
./gradlew assembleRelease --no-daemon

# Or build debug APK
./gradlew assembleDebug --no-daemon
```

### Tests

`ExtensionCompatibilityTest` locks in the extension runtime contract and fails the
build if it regresses:

- `minSdk >= 26` (the default-method desugaring guard)
- host kotlinx.serialization / OkHttp versions match the extension ecosystem
- `libVersion` validation: missing, valid, older, newer, malformed
- repository index URL resolution, including the `index.pb/index.pb` case
- Cloudflare challenge vs. geo-block detection

### Output
APKs are generated at:
```
app/build/outputs/apk/standard/release/
├── TachiyomiDNP-1.9.0-release-arm64-v8a.apk
├── TachiyomiDNP-1.9.0-release-armeabi-v7a.apk
├── TachiyomiDNP-1.9.0-release-x86_64.apk
├── TachiyomiDNP-1.9.0-release-x86.apk
└── TachiyomiDNP-1.9.0-release-universal.apk
```

### Gradle Configuration
Key settings in `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4096M
kotlin.daemon.jvmargs=-Xmx4096M
org.gradle.caching=true
```

> **Note:** If you encounter OOM errors during build, increase JVM memory or use `--no-daemon` flag.

---

## Architecture

```
TachiyomiDNP/
├── app/                    # Main application module
│   ├── src/main/           # Source code (Kotlin)
│   ├── src/main/res/       # Resources (layouts, strings, drawables)
│   ├── src/test/           # Extension runtime compatibility tests
│   └── build.gradle.kts    # App-level build config
├── buildSrc/               # Build logic & dependency versions
│   └── src/main/kotlin/    # AndroidVersions, Plugins, Dependencies
├── docs/                   # Extension runtime contract & design docs
├── gradle/                 # Gradle wrapper
├── .github/                # GitHub Actions, issue templates
├── build.gradle.kts        # Root build config
├── settings.gradle.kts     # Project settings
├── gradle.properties       # Gradle JVM args & Android settings
└── gradlew                 # Gradle wrapper script
```

- **Language:** Kotlin
- **UI:** XML layouts + Material Design components
- **Dependency Injection:** Manual (ServiceLocator pattern)
- **Networking:** OkHttp 5 + Retrofit
- **Image Loading:** Coil
- **Database:** StorIO (custom ORM over SQLite)
- **Async:** RxJava + Coroutines
- **Extension loading:** `ChildFirstPathClassLoader` (system → extension → host)

---

## Credits & Attribution

This project stands on the shoulders of giants:

- **[Tachiyomi](https://github.com/tachiyomiorg/tachiyomi)** — The original free and open source manga reader for Android. Copyright 2015 Javier Tomás. Licensed under Apache 2.0.
- **[TachiyomiJ2K](https://github.com/Jays2Kings/tachiyomiJ2K)** — A fork of Tachiyomi with enhanced UI/UX, new features, and Material Design You improvements. Licensed under Apache 2.0. This project is directly based on TachiyomiJ2K.
- **[Mihon](https://github.com/mihonapp/mihon)** — Reference implementation for the extension loading and runtime compatibility subsystems.
- **[Keiyoushi](https://github.com/keiyoushi/extensions-source)** — The extension ecosystem this app targets.

## License

    Copyright 2024-2026 (theordinaryguy23)
    Based on TachiyomiJ2K (Copyright jays2kings)
    Based on Tachiyomi (Copyright 2015 Javier Tomás)

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

## Disclaimer

The developer of this application does not have any affiliation with the content providers available through the app. This app is a reader only — users are responsible for the sources they use.
