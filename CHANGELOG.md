# Changelog

All notable changes to TachiyomiDNP will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.8.0] - 2026-06-07

### Changed
- **Rebranded from TachiyomiJ2K to TachiyomiDNP** — new identity, new package (`eu.kanade.tachiyomi.dnp`), new GitHub repo
- All in-app links now point to `theordinaryguy23/TachiyomiDNP` (was `Jays2Kings/tachiyomiJ2K`)
- Versioning format changed to `{versionName}-{yyyyMMdd}-{shortSha}` for traceability

### Added
- APK split by ABI (arm64-v8a, armeabi-v7a, x86_64, x86) — smaller downloads per device
- Automated release script for GitHub Releases
- GitHub Actions CI for automated builds
- Proper README with minimum requirements, build instructions, and architecture overview
- CONTRIBUTING.md, SECURITY.md, and issue templates

### Fixed
- **Memory leaks** — LibraryUpdateJob, ReaderViewModel RxJava subscriptions, Coil cache trim
- **Battery drain** — reduced Coil memory cache from 40% → 25%
- **SSL compatibility** — Conscrypt initialization now applied for all Android versions (was only <10)
- **Gradle OOM** — increased JVM memory to 4096MB for both Gradle and Kotlin daemon

### Optimized
- Image loading: crossfade disabled, disk cache 250MB, OkHttp cache 50MB
- Reader preload: 10 pages (up from 6), preload trigger at last 10 pages (up from 5)

[Unreleased]: https://github.com/theordinaryguy23/TachiyomiDNP/compare/v1.8.0...HEAD
[1.8.0]: https://github.com/theordinaryguy23/TachiyomiDNP/releases/tag/v1.8.0
