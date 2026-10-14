# Desktop reader progress — Phase 2F

## Scope

Phase 2F adds local reading-position persistence without introducing a database dependency.

- Save the last page index for each MangaDex chapter.
- Restore the saved page when the chapter is opened again.
- Clamp saved positions to the current chapter's available page range.
- Store progress under the user's local application data folder:
  - Windows: `%LOCALAPPDATA%\TachiyomiDNP\data\reader-progress.properties`
  - Fallback: `<user.home>\TachiyomiDNP\data\reader-progress.properties`
- Use an atomic file replacement when supported to reduce the risk of a partially written progress file.
- Persist only chapter IDs and page indexes; MangaDex@Home page URLs are temporary and are never stored.

## Validation

- Unit tests cover persistence across store instances, missing files, negative page indexes, and invalid chapter IDs.
- Windows CI runs `:desktop:test` before packaging.
- The packaging workflow separately verifies that the bundled runtime is Java 21 and the compiled entry point targets Java 21 bytecode.

## Follow-up work

- Offline chapter downloads and a Downloads UI.
- Reading history/library integration.
- Retention and cleanup controls for downloaded chapter files.
