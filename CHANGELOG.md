# Changelog

All notable changes to TachiyomiDNP Android will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial release based on TachiyomiJ2K
- Custom branding (TachiyomiDNP, package: `eu.kanade.tachiyomi.dnp`)
- Proper versioning system: `{versionName}-{yyyyMMdd}-{shortSha}`
- APK split by ABI (arm64-v8a, armeabi-v7a, x86_64, x86)
- Release script for automated GitHub releases

### Optimizations
- Memory leak fixes (LibraryUpdateJob, ReaderViewModel RxJava, Coil cache trim)
- Battery optimization (Coil memory cache 40% → 25%)
- Image loading: crossfade off, disk cache 250MB, OkHttp cache 50MB
- Reader preload: 10 pages (up from 6), preload trigger at last 10 pages (up from 5)
- Gradle JVM memory: 4096MB for both Gradle and Kotlin daemon

### Build Configuration
- compileSdk / targetSdk: 36 (Android 16)
- minSdk: 23 (Android 6.0 Marshmallow)
- Kotlin: 2.3.10
- AGP: 8.13.2
- NDK: 23.1.7779620

[Unreleased]: https://github.com/theordinaryguy23/TachiyomiDNP-Android/compare/main...HEAD
