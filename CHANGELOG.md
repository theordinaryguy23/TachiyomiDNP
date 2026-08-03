# Changelog

## v1.8.8

### Bug Fixes
- Fix RepoPresenter getter/setter to preserve original repo URLs ending with index.pb, index.min.json, or repo.json
- Prevent URL path duplication when user enters a direct index file URL
- Preserve original URLs in setter instead of stripping suffixes

### Technical Changes
- Upgrade kotlinx-serialization from 1.8.1 to 1.11.0 for protobuf compatibility
- Fix libVersion extraction to use extensionLib field instead of versionName
- Stop appending /index.min.json to direct index.pb URLs which caused 404 errors

### New Features
- Support protobuf extension index format (Keiyoushi v2)
- Backward compatible with legacy JSON extension repos
- Auto-detect repo.json, index.pb, and index.min.json

## v1.8.7

### Bug Fixes
- Fix direct URL handling for extension repositories (index.pb, repo.json, index.min.json)
- Prevent URL path duplication when user enters a direct index file URL
- Preserve original repo URL in RepoPresenter to prevent unintended path modification

### New Features
- Support protobuf extension index format (Keiyoushi v2)
- Backward compatible with legacy JSON extension repos
- Auto-detect repo.json, index.pb, and index.min.json

## v1.8.6

### Bug Fixes
- Fix version mismatch, APK naming, and updater logic
- Inject keystore from GitHub Secrets for APK signing

### New Features
- Add workflow_dispatch trigger to build workflow

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