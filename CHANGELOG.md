# Changelog

## v1.8.2

### Bug Fixes
- Fixed Firebase integration — crash logs & analytics now go to our own Firebase project (was incorrectly sending to TachiyomiJ2K upstream)
- Fixed branding: removed all leftover "TachiyomiJ2K" references across 52 locale strings
- Fixed debug application ID suffix (.debugJ2K → .debugDNP)
- Fixed root project name (tachiyomiJ2K → TachiyomiDNP)
- Fixed About page social links pointing to upstream Tachiyomi
- Fixed Weblate translation link in About page

### Performance
- More stable network connections with OkHttp 4.12.0 (downgraded from unstable 5.0.0-alpha.14)

### New Features
- Default extension repo (keiyoushi) pre-configured — no more "Failed to fetch available extensions" on first launch
- APK filenames now include version number (e.g., TachiyomiDNP-1.8.2-Standard-Release.apk)

## v1.8.1

### Bug Fixes
- Fixed "Failed to fetch available extensions" error on first launch

### Performance
- Downgraded OkHttp from 5.0.0-alpha.14 to stable 4.12.0 to fix SocketException crashes

## v1.8.0

- Initial release based on TachiyomiJ2K
- Package: eu.kanade.tachiyomi.dnp