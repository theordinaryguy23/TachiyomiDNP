# Changelog

## v1.9.2

### Network & Rate Limiting Fixes
- **Fixed Rate Limiting Bug**: Resolved issue in `RateLimitInterceptor` and `SpecificHostRateLimitInterceptor` where requests bypassed throttling and triggered `HTTP error 429` (Too Many Requests).
- **Automated HTTP 429 Retry**: Added automatic retry backoff (respecting `Retry-After` header or exponential delay) for transient rate limit responses.
- **Enhanced Cloudflare Bypass**: Expanded `CloudflareInterceptor` to handle HTTP 429 Cloudflare Turnstile/Managed Challenges during searches.

### UI & Settings Optimization
- **Cleaned Up Settings**: Streamlined settings categories, hiding internal debug diagnostics in release builds and removing redundant link options.
- **Simplified Backup & Sync Menus**: Consolidated cloud backups into a single fully functional Google Drive sync implementation and removed duplicate non-functional sync menus.
- **Improved Error Messaging**: Display clearer, user-friendly messages when rate limits or HTTP errors occur.

## v1.9.1

### New Features
- **Cloud Sync**: Initial support for Google Drive sync (History, Library, and Categories).
- **Sync Settings**: New dedicated settings menu for cloud synchronization.
- **Improved Extension Repos**: Auto-completion and display now favor the modern `index.pb` format.

### Bug Fixes
- Fixed "Failed to fetch available extensions" caused by incorrect GZIP decompression handling.
- Fixed version compatibility check by increasing supported `LIB_VERSION_MAX` to 1.6.

### Performance & Stability
- Updated various internal dependencies for better stability.
- Improved network response parsing robustness.


## v1.9.0-beta1

### New Features
- Support protobuf extension index format (Keiyoushi v2)
- Backward compatible with legacy JSON extension repos
- Auto-detect repo.json, index.pb, and index.min.json

## v1.8.2

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