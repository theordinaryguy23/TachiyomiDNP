# Desktop offline chapter downloads — Phase 2G

## Scope

Phase 2G adds local chapter downloads for the JVM-native MangaDex desktop source.

- Download all chapter pages using MangaDex Data Saver quality.
- Store image bytes and stable chapter metadata in `%LOCALAPPDATA%\TachiyomiDNP\downloads\<chapter-id>\`.
- Never persist temporary MangaDex@Home image URLs.
- Show downloaded chapters in the Downloads section.
- Open downloaded chapters directly from Downloads, including when the network is unavailable.
- Delete individual downloaded chapters from the Downloads section.
- Preserve the existing reading-position store so offline reading resumes at the last saved page.

## Storage and safety

- Chapter directory names are validated MangaDex UUIDs.
- Page files use generated sequential names; remote filenames are not used as local paths.
- A chapter is downloaded into a staging directory before it replaces the previous saved copy.
- Downloads are local to the current Windows user and are not synchronized.

## Validation

- Desktop unit tests cover download persistence across store instances, reading page bytes, deletion, empty downloads, and invalid chapter IDs.
- Windows CI runs desktop tests before creating the EXE/MSI and checks the bundled Java 21 runtime.

## Known limitations

- MangaDex is the only desktop source supported by the current reader.
- Download uses Data Saver quality and downloads pages sequentially; pause/resume and download queue management are not yet included.
- Storage usage is not yet displayed and there is no global “delete all” action.
