# TachiyomiDNP Android

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://github.com/theordinaryguy23/TachiyomiDNP-Android)
[![Based on](https://img.shields.io/badge/based%20on-TachiyomiJ2K-green.svg)](https://github.com/Jays2Kings/tachiyomiJ2K)
[![Min SDK](https://img.shields.io/badge/minSdk-23%20(Android%206.0)-orange.svg)](https://developer.android.com/about/versions/marshmallow)
[![Target SDK](https://img.shields.io/badge/targetSdk-36%20(Android%2016)-orange.svg)](https://developer.android.com/about/versions)

A free and open source manga reader for **Android 6.0 (Marshmallow) and above**.

> **TachiyomiDNP Android** is based on the [TachiyomiJ2K](https://github.com/Jays2Kings/tachiyomiJ2K) project — a feature-rich fork of the original [Tachiyomi](https://github.com/tachiyomiorg/tachiyomi) manga reader. This build continues the TachiyomiJ2K legacy with additional optimizations and custom branding.

---

## Minimum Requirements

| Requirement | Minimum | Recommended |
|-------------|---------|-------------|
| **Android** | 6.0 (API 23) Marshmallow | 10.0+ (API 29) |
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
| `app-standard-arm64-v8a-release.apk` | arm64-v8a | ~35 MB | **Recommended** for most devices |
| `app-standard-armeabi-v7a-release.apk` | armeabi-v7a | ~30 MB | Older 32-bit devices |
| `app-standard-x86_64-release.apk` | x86_64 | ~38 MB | Emulators |
| `app-standard-universal-release.apk` | All | ~90 MB | Universal (all ABIs) |

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
- [x] Material Design You additions
- [x] Batch auto-source migration
- [x] View all chapters right in the reader

---

## Download

Get the latest APK from the [Releases](https://github.com/theordinaryguy23/TachiyomiDNP-Android/releases) page.

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
# Set Java 17
export JAVA_HOME=~/jdk-17.0.11+9
export PATH=$JAVA_HOME/bin:$PATH

# Verify
java -version  # Should show 17.x.x
```

### Build
```bash
git clone https://github.com/theordinaryguy23/TachiyomiDNP-Android.git
cd TachiyomiDNP-Android

# Build release APK (recommended)
./gradlew assembleRelease --no-daemon

# Or build debug APK
./gradlew assembleDebug --no-daemon
```

### Output
APKs are generated at:
```
app/build/outputs/apk/standard/release/
├── app-standard-arm64-v8a-release.apk
├── app-standard-armeabi-v7a-release.apk
├── app-standard-x86_64-release.apk
├── app-standard-x86-release.apk
└── app-standard-universal-release.apk
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
TachiyomiDNP-Android/
├── app/                    # Main application module
│   ├── src/main/           # Source code (Kotlin)
│   ├── src/main/res/       # Resources (layouts, strings, drawables)
│   └── build.gradle.kts    # App-level build config
├── buildSrc/               # Build logic & dependency versions
│   └── src/main/kotlin/    # AndroidVersions, Plugins, Dependencies
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
- **Networking:** OkHttp + Retrofit
- **Image Loading:** Coil
- **Database:** Room (via Tachiyomi's custom ORM)
- **Async:** RxJava + Coroutines

---

## Credits & Attribution

This project stands on the shoulders of giants:

- **[Tachiyomi](https://github.com/tachiyomiorg/tachiyomi)** — The original free and open source manga reader for Android. Copyright 2015 Javier Tomás. Licensed under Apache 2.0.
- **[TachiyomiJ2K](https://github.com/Jays2Kings/tachiyomiJ2K)** — A fork of Tachiyomi with enhanced UI/UX, new features, and Material Design You improvements. Licensed under Apache 2.0. This project is directly based on TachiyomiJ2K.

## License

    Copyright 2024-2026 Narendra (theordinaryguy23)
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
