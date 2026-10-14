# Desktop reader — Phase 2E

## Current flow

1. Search MangaDex and open a title's details.
2. Load the first page of English chapter metadata.
3. Choose **Read** beside a chapter.
4. Request `GET /at-home/server/{chapterId}` and read the returned chapter hash and `dataSaver` filenames.
5. Build temporary page URLs using `{baseUrl}/data-saver/{hash}/{filename}`.
6. Load one image at a time and use Previous / Next to navigate pages.

## Current scope and limitations

- MangaDex is the first JVM-native desktop source; Android extension runtimes are not reused on Windows.
- Data Saver quality is selected by default to reduce bandwidth.
- Chapter metadata is limited to the first 30 English chapters in this phase.
- The app does not yet persist reading progress, preload pages, cache downloaded chapters, support RTL/vertical reading modes, or expose original-quality selection.
- MangaDex@Home base URLs are temporary and must be resolved again for a later reading session.
- Image/network failures are shown in the reader UI; retry controls and automated API contract tests remain follow-up work.

## Validation checklist

- [ ] Android Build workflow passes.
- [ ] Windows Desktop packaging workflow passes.
- [ ] Manually search for a title, open details, select a chapter, and navigate at least two pages.
- [ ] Verify invalid chapter IDs and network failures display a readable error.
