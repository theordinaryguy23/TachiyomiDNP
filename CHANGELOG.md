# Changelog

All notable changes to TachiyomiDNP will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.8.0] - 2026-06-07

### Fixed
- **Memory leaks** — LibraryUpdateJob, ReaderViewModel RxJava subscriptions, Coil cache trim
- **SSL compatibility** — Conscrypt initialization now applied for all Android versions (was only <10)
- **Gradle OOM** — increased JVM memory to 4096MB for both Gradle and Kotlin daemon

### Optimized
- **Battery** — Coil memory cache reduced from 40% → 25%
- **Image loading** — crossfade disabled, disk cache 250MB, OkHttp cache 50MB
- **Reader preload** — 10 pages (up from 6), preload trigger at last 10 pages (up from 5)

[Unreleased]: https://github.com/theordinaryguy23/TachiyomiDNP/compare/v1.8.0...HEAD
[1.8.0]: https://github.com/theordinaryguy23/TachiyomiDNP/releases/tag/v1.8.0
