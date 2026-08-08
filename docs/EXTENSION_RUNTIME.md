# Extension Runtime Contract

This document defines how TachiyomiDNP (the **host**) and Keiyoushi/Mihon-compatible
extensions share a runtime, and records the decisions behind every compatibility
measure in the codebase.

Reference implementations:

- [`mihonapp/mihon`](https://github.com/mihonapp/mihon)
- [`keiyoushi/extensions-source`](https://github.com/keiyoushi/extensions-source)

---

## 1. The runtime model

Extensions are separate APKs loaded into the host process via a
`ChildFirstPathClassLoader`. Keiyoushi's `ExtensionPlugin` declares all shared
libraries with `compileOnly`:

```kotlin
// keiyoushi/extensions-source — gradle/build-logic/.../ExtensionPlugin.kt
compileOnly(libs.bundles.common)   // kotlin-stdlib, coroutines, kotlinx-serialization,
                                   // jsoup, okhttp-core, okhttp-brotli, okhttp-zstd, quickjs …
compileOnly(libs.tachiyomi.lib.v16)
```

That means:

```text
Extension APK
    |
    | compileOnly dependency (NOT bundled)
    v
Host application runtime  ← DNP must provide these classes, at a compatible ABI
```

Extension APKs contain **only** their own implementation classes. Every shared
library is resolved from the host. The host is therefore contractually obliged to
ship the exact libraries in `libs.bundles.common`, at versions ABI-compatible with
what extensions were compiled against.

---

## 2. Root cause: `AbstractMethodError` on `GeneratedSerializer`

### Symptom

```text
java.lang.AbstractMethodError: abstract method
"kotlinx.serialization.KSerializer[]
 kotlinx.serialization.internal.GeneratedSerializer.typeParametersSerializers()"
on receiver java.lang.Class<g1>
```

Reproduced on MangaDex and any extension using `@Serializable` data classes.

### What it was *not*

Version drift was ruled out first — DNP already matched the ecosystem exactly:

| Component | DNP host | Mihon | Keiyoushi extensions |
|---|---|---|---|
| kotlinx.serialization | 1.11.0 | 1.11.0 | 1.11.0 |
| OkHttp | 5.4.0 | 5.4.0 | 5.4.0 |

`ChildFirstPathClassLoader` was also byte-for-byte equivalent to Mihon's. Bumping
versions or adding stubs could not have fixed this.

### What it actually was: `minSdk 23`

DNP built with `minSdk = 23`; Mihon and Keiyoushi both build with `minSdk = 26`.

Below API 26, ART cannot execute Java 8 interface default methods, so **D8
desugars them**: the default body is moved into a synthetic `$-CC` helper class and
the interface method is left purely abstract.

`GeneratedSerializer.typeParametersSerializers()` is exactly such a default method.

The failure chain:

1. Keiyoushi compiles the extension at `minSdk 26`. Its generated `$$serializer`
   classes (`g1` after R8) **do not emit** `typeParametersSerializers()` — they
   inherit the interface default.
2. `compileOnly` means the serialization runtime is loaded from the **host**.
3. DNP's host copy of the interface had its default body stripped by D8 desugaring.
4. The extension's `$$serializer` inherits nothing → `AbstractMethodError`.

Verified directly in the release DEX:

```console
# Before (minSdk 23):
$ strings -a classes*.dex | grep GeneratedSerializer
Lkotlinx/serialization/internal/GeneratedSerializer$-CC;      ← desugared
Lkotlinx/serialization/internal/GeneratedSerializer$DefaultImpls;

# After (minSdk 26):
$ strings -a classes*.dex | grep GeneratedSerializer
Lkotlinx/serialization/internal/GeneratedSerializer;           ← $-CC gone
Lkotlinx/serialization/internal/GeneratedSerializer$DefaultImpls;
```

### Fix

`minSdk 23 → 26`, matching the ecosystem. Guarded by
`ExtensionCompatibilityTest.minSdk must be at least 26 …` so it cannot regress.

**Cost:** drops Android 6.0 / 7.x. Unavoidable — the extension ecosystem's
`compileOnly` contract presumes API 26 default-method support, and no host-side
workaround exists for a method body the compiler deleted.

---

## 3. Serialization strategy: **Option A — host-provided**

The evidence (`compileOnly(libs.bundles.common)`) leaves no choice. Extensions
never ship a serialization runtime, so the host must provide one and keep it
ABI-compatible.

Rejected alternatives:

- **Option B (extension-isolated serialization)** — impossible; extensions do not
  package it, and isolating it would break `KSerializer` instances crossing the
  host/extension API boundary with `ClassCastException`.
- **Option C (version enforcement only)** — necessary but not sufficient; `libVersion`
  gating cannot detect a desugaring-induced ABI break, which is invisible in metadata.

---

## 4. Dependency ownership

`ChildFirstPathClassLoader` resolves in order: **system → extension → host parent**.
That ordering, plus the `compileOnly` contract, determines ownership:

| Dependency | Owner | Why |
|---|---|---|
| Android framework / `java.*` | **System** | Resolved by the system classloader first; never shadowable. |
| Tachiyomi extension API (`eu.kanade.tachiyomi.source.*`) | **Host-shared** | Types cross the boundary; two copies ⇒ `ClassCastException`. |
| kotlinx.serialization | **Host-shared** | `compileOnly` in extensions; `KSerializer` crosses the boundary. |
| OkHttp / Okio | **Host-shared** | `compileOnly`; `OkHttpClient`, `Interceptor`, `Response` cross the boundary. |
| okhttp-brotli / okhttp-zstd | **Host-shared** | `compileOnly`; registered as interceptors on the host client. |
| Jsoup, RxJava, coroutines, QuickJS | **Host-shared** | Part of `libs.bundles.common`, all `compileOnly`. |
| Extension implementation classes | **Extension-isolated** | Private to the extension; found child-first. |
| Extension-bundled third-party libs | **Extension-isolated** | Anything genuinely bundled (not in `bundles.common`) resolves child-first, which is the point of the loader. |

The loader is *not* blanket child-first: the system classloader is consulted before
the extension DEX, so framework classes can never be shadowed, and the parent is the
fallback for everything the extension does not contain — which is how host-shared
libraries resolve to a single copy.

---

## 5. Compatibility stub audit

Policy: **no stub without a demonstrated ABI requirement.**

### `okhttp3.zstd.Zstd` — REMOVED

```text
Extension requiring it: any extension in libs.bundles.common (okhttp-zstd is
                        compileOnly for all Keiyoushi extensions)
Expected API:           okhttp3.zstd.Zstd : CompressionInterceptor.DecompressionAlgorithm
Previous implementation: local no-op stub returning the compressed source unchanged
Why it was wrong:       a no-op silently returns raw zstd bytes as if decompressed,
                        producing corrupt HTML/JSON rather than a clean failure.
                        The premise ("OkHttp ships no okhttp-zstd artifact") was false.
Resolution:             replaced with the real artifacts —
                        com.squareup.okhttp3:okhttp-zstd:5.4.0
                        com.squareup.zstd:zstd-kmp-okio:0.4.0
                        (identical to Mihon/Keiyoushi)
Removal condition:      n/a — this is now a real dependency, not a stub
```

Verified in the release DEX: `Lokhttp3/zstd/Zstd;` is now backed by
`Lcom/squareup/zstd/okio/OkioZstd;`.

### Remaining stubs

None. `app/src/main/java/okhttp3/` no longer exists.

---

## 6. Extension library version validation

DNP matches Mihon exactly:

```kotlin
private const val METADATA_EXTENSION_LIB = "tachiyomix.extensionLib"
private val SUPPORTED_LIB_VERSIONS = listOf(1.4, 1.6)
```

`VALID_LIB_VERSIONS` in Keiyoushi's `Constant.kt` is likewise `listOf("1.4", "1.6")`.

`libVersion` is read from manifest metadata and is distinct from the extension's own
`versionCode`/`versionName`. Extensions outside the supported set are rejected
**before** class loading with an actionable message, never loaded silently.

Covered by tests: missing, valid, older, newer-unsupported, and malformed `libVersion`.

---

## 7. Cloudflare vs. geo-block

`CloudflareInterceptor` requires **all three** conditions before launching WebView:

1. status in `{403, 503}`, **and**
2. `Server` header in `{cloudflare, cloudflare-nginx}`, **and**
3. body contains `challenge-error-title` or `challenge-error-text`

A bare 403/503 — including a Cloudflare-fronted geo-block — never triggers the
bypass, which is what caused the previous infinite-loading loop on Manganato.

Covered by tests: 200, 403 geo-block, 403 challenge, 503 challenge, 503 non-Cloudflare,
403 with no `Server` header.

---

## 8. Repository index formats

`ExtensionApi` resolves in order: `repo.json` (→ `index_v2` protobuf) → `index.pb`
→ `index.min.json`. URLs already ending in a known index filename are used verbatim,
so `…/index.pb` never becomes `…/index.pb/index.pb`. Gzip-compressed indexes are
detected and inflated before parsing. Failures are logged and fall through to the next
format rather than taking down the extension subsystem.

Covered by tests: direct `index.pb`, direct `repo.json`, direct `index.min.json`,
bare directory URL, and sibling-index resolution.

---

## 9. Dependency compatibility matrix

| Component | DNP host | Extension ecosystem | Required relationship |
|---|---|---|---|
| Kotlin | 2.3.10 | 2.4.10 | Host ≥ extension **stdlib ABI**; stdlib is backward compatible, so a minor lag is safe. |
| kotlinx.serialization | 1.11.0 | 1.11.0 | **Must match exactly.** Generated-serializer ABI changed at 1.8.0. |
| OkHttp | 5.4.0 | 5.4.0 | **Must match major.** `CompressionInterceptor` is 5.x-only. |
| Brotli | okhttp-brotli 5.4.0 | okhttp-brotli 5.4.0 | Host-provided, version-locked to OkHttp. |
| Zstd | okhttp-zstd 5.4.0 + zstd-kmp-okio 0.4.0 | same | Host-provided, version-locked to OkHttp. |
| Extension API | Tachiyomi/J2K source API | tachiyomi-lib v1.4 / v1.6 | Host must satisfy both lib versions. |
| Extension library | supports 1.4, 1.6 | emits 1.4, 1.6 | Host set ⊇ ecosystem set. |
| **minSdk** | **26** | **26** | **Host ≥ 26**, or interface default methods desugar and serialization breaks. |

`ExtensionCompatibilityTest` fails the build if `minSdk`, the serialization version,
or the OkHttp version drifts out of this matrix.

---

## 10. Mihon comparison

| Subsystem | DNP behavior | Mihon behavior | Verdict |
|---|---|---|---|
| `ChildFirstPathClassLoader` | system → child → parent, all four overrides | identical | **Keep** — ported verbatim |
| `ExtensionLoader` libVersion check | `tachiyomix.extensionLib`, `[1.4, 1.6]` | identical | **Keep** |
| kotlinx.serialization | 1.11.0, host-provided | identical | **Keep** |
| OkHttp | 5.4.0 | identical | **Keep** |
| Brotli / Zstd | real artifacts | identical | **Ported** — replaced a no-op stub |
| `minSdk` | 26 | 26 | **Ported** — was 23; the root cause |
| `CloudflareInterceptor` | status + `Server` + challenge-marker body check | status + `Server` | **Keep DNP's** — stricter; prevents geo-block false positives. Risk: a future Cloudflare challenge page that drops both marker IDs would be missed; markers are asserted in tests so a miss is caught loudly. |
| Repository parsing | `repo.json` → `index.pb` → `index.min.json`, gzip-aware, direct-URL-safe | `index.min.json` oriented | **Keep DNP's** — broader Keiyoushi format support |
| J2K UI/UX, dynamic categories, Recents | present | absent | **Keep** — DNP/J2K identity, no runtime interaction |

---

## 11. Diagnostics

Extension loading diagnostics are emitted through Timber at debug level and log only
package, version, `libVersion`, classloader identity, and the compatibility result.
Cookies, authentication headers, and credentials are never logged.

---

## 12. Manual verification matrix

Automated tests cover the host-side invariants. The following require a device and
should be re-run before each release:

| Extension | Install | Load | Init | Search | Browse | Details | Chapters | Pages | Restart |
|---|---|---|---|---|---|---|---|---|---|
| MangaDex (serialization-heavy) | | | | | | | | | |
| A Brotli-using source | | | | | | | | | |
| A Zstd-using source | | | | | | | | | |
| A Cloudflare-protected source | | | | | | | | | |
| A plain source | | | | | | | | | |

Watch logcat for regressions:

```bash
adb logcat -c
adb logcat | grep -E 'AbstractMethodError|NoSuchMethod|NoClassDef|ClassNotFound|GeneratedSerializer'
```
