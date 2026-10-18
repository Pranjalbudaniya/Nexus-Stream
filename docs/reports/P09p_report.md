## Report: P09p
Status: done
Access proof (raw grep output):
```
$ grep -n -E "^### 7\.[56] |^## 10\. " docs/NEXUS_STREAM_PROJECT_MEMORY.md
418:### 7.5 Stream fan-out
430:### 7.6 Stream parsing (heuristic; addons format text differently)
562:## 10. Player (libVLC)

$ grep -n -E "^\| D(10[6-9]|11[0-2]) " docs/NEXUS_STREAM_PROJECT_MEMORY.md | cut -c1-60
133:| D106 | Direct streams and headers: every stream plays 
134:| D107 | Downloads (P16) cover every stream type: direct
135:| D108 | P08a accepted (report of 2026-10-18). `AppError
136:| D109 | P08a.2 decisions: `JLibTorrentBackend` is the o
137:| D110 | Known gap in `DefaultTorrentEngine` (P08a): aft
138:| D111 | P08a.2 accepted (owner phone check B1-B12 passe
139:| D112 | P09p decisions: `resolvePlaybackSource(source: 
```

StreamSource definition and mapper precedence (raw):
```kotlin
// app/src/main/java/com/nexus/stream/domain/model/Stream.kt
sealed interface StreamSource {
    data class Url(
        val url: String,
        val requestHeaders: Map<String, String>,
    ) : StreamSource

    data class Torrent(
        val infoHash: String,
        val fileIdx: Int?,
        val trackers: List<String>,
    ) : StreamSource

    data class External(
        val url: String,
    ) : StreamSource

    data class YouTube(
        val ytId: String,
    ) : StreamSource

    data class Unsupported(
        val kind: String,
    ) : StreamSource
}
```

```kotlin
// app/src/main/java/com/nexus/stream/data/remote/addon/AddonMappers.kt
private fun resolveStreamSource(dto: StreamDto): StreamSource =
    when {
        !dto.url.isNullOrBlank() -> {
            StreamSource.Url(
                url = dto.url,
                requestHeaders =
                    dto.behaviorHints
                        ?.proxyHeaders
                        ?.request
                        .orEmpty(),
            )
        }

        !dto.infoHash.isNullOrBlank() -> {
            val trackers =
                dto.sources
                    .orEmpty()
                    .filter { it.startsWith("tracker:") }
                    .map { it.removePrefix("tracker:") }
            StreamSource.Torrent(
                infoHash = dto.infoHash,
                fileIdx = dto.fileIdx.asIntOrNull(),
                trackers = trackers,
            )
        }

        !dto.externalUrl.isNullOrBlank() -> {
            StreamSource.External(url = dto.externalUrl)
        }

        !dto.ytId.isNullOrBlank() -> {
            StreamSource.YouTube(ytId = dto.ytId)
        }

        else -> {
            StreamSource.Unsupported(kind = resolveUnsupportedKind(dto))
        }
    }
```
When an addon response contains multiple stream fields, `AddonMappers.resolveStreamSource` picks the first non-blank field in order of precedence: `url` wins first, followed by `infoHash`, then `externalUrl`, then `ytId`, and falls back to `Unsupported` if none are present.

Carry-over (branch, checkAll):
- Branch: `p09p-playback-source` (created from `main`, 0 commits ahead before prompt execution).
- Initial `./gradlew checkAll`: PASSED (57 actionable tasks up-to-date).

Files created / modified:
- Created:
  - `app/src/main/java/com/nexus/stream/domain/model/PlaybackSource.kt`
  - `app/src/main/java/com/nexus/stream/domain/usecase/HeaderPolicy.kt`
  - `app/src/main/java/com/nexus/stream/domain/usecase/ResolvePlaybackSource.kt`
  - `app/src/test/java/com/nexus/stream/domain/usecase/HeaderPolicyTest.kt`
  - `app/src/test/java/com/nexus/stream/domain/usecase/ResolvePlaybackSourceTest.kt`
- Modified: None.

Edge cases H1-H12, R1-R24: implemented / not implemented (reasons):
- H1-H12: implemented (all 12 passed in `HeaderPolicyTest`)
  - H1: standard headers (`Referer`, `User-Agent`) kept in input order.
  - H2: case-insensitive duplicate (`Referer`, `referer`) keeps first entry.
  - H3: hop-by-hop and connection-management headers dropped.
  - H4: CRLF, LF, CR, and NUL in value drop entry.
  - H5: invalid header names (spaces, colons, non-RFC 7230 tokens) dropped.
  - H6: printable ASCII (0x20..0x7E) and HTAB (0x09) kept; DEL (0x7F) and non-ASCII dropped.
  - H7: values trimmed; empty/whitespace-only values dropped.
  - H8: value length 4096 kept; 4097 dropped.
  - H9: maximum of 20 header entries kept.
  - H10: total name+value length capped at 16384 bytes, stopping further additions in order.
  - H11: empty map returns empty map.
  - H12: Authorization, Cookie, and custom RFC token headers preserved.
- R1-R24: implemented (all 24 passed in `ResolvePlaybackSourceTest`)
  - R1: `Url` with valid http/https URL and empty headers -> `Direct(url)`.
  - R2: `Url` with headers -> `DirectWithHeaders(url, sanitizedHeaders)`.
  - R3: `Url` with only hop-by-hop headers -> `Direct(url)`.
  - R4: `Url` with unencoded spaces -> encoded as `%20`.
  - R5: `file`, `content`, `intent`, `javascript`, `data` schemes -> `Unsupported(BAD_SCHEME)`.
  - R6: `ftp`, `rtmp`, and relative/scheme-less URLs -> `Unsupported(BAD_SCHEME)`.
  - R7: uppercase scheme -> lowercased in output.
  - R8: blank or whitespace-only URL -> `Unsupported(NO_SOURCE)`.
  - R9: URL length exactly 8192 chars kept; 8193 chars -> `Unsupported(BAD_URL)`.
  - R10: whitespace/newline trimmed ok; inner control character -> `Unsupported(BAD_URL)`.
  - R11: missing or empty authority/host -> `Unsupported(BAD_URL)`.
  - R12: `Url` starting with `magnet:` -> `Torrent(TorrentRequest(hash, trackers, null, null))`.
  - R13: `Url` magnet with `EpisodeHint` -> `Torrent` with hint forwarded.
  - R14: `Url` magnet with `btmh` hash only -> `Unsupported(INVALID_HASH)`.
  - R15: `Url` magnet with request headers -> `Torrent` (headers ignored).
  - R16: `Torrent` source with uppercase hash, trackers, fileIdx, hint -> `Torrent` with lowercase hash.
  - R17: `Torrent` source with invalid hash lengths (39-char hex or 32-char base32) -> `Unsupported(INVALID_HASH)`.
  - R18: `Torrent` source with blank hash -> `Unsupported(INVALID_HASH)`.
  - R19: `Torrent` source with negative or null file index -> `TorrentRequest.fileIdx = null`.
  - R20: `External` source with valid URL -> `External(url)`.
  - R21: `External` source with invalid scheme or blank -> `Unsupported(BAD_SCHEME)` / `Unsupported(NO_SOURCE)`.
  - R22: direct `Url` with hint provided -> `Direct` (hint ignored).
  - R23: `YouTube` source -> `Unsupported(NO_SOURCE)`.
  - R24: `Unsupported` source -> `Unsupported(NO_SOURCE)`.

Suppressions used (max 2, with reasons):
- 0 suppressions used.

Simplification pass:
- Extracted entry validation in `HeaderPolicy.kt` into expression-bodied helper functions to eliminate loop jump statements and keep return counts minimal.
- Used a structured `when` expression in `normalizeDirectUrl` to separate syntax validation from URI assembly without triggering detekt `ReturnCount` or `TooManyFunctions`.
- Replaced magic ASCII control codes with named constants (`MIN_PRINTABLE_ASCII_CODE`, `DEL_ASCII_CODE`, `SCHEME_SEPARATOR`).

Assumptions made:
- None.

Deviations:
- None.

Verification: checkAll ✔/✘ · import and log grep · diff stat · new tests list:
- checkAll: ✔ PASSED (57 actionable tasks, 0 warnings, 0 lint failures)
- import and log grep:
```
$ grep -rn -E "import android\.|Log\.|println\(" app/src/main/java/com/nexus/stream/domain/model/PlaybackSource.kt app/src/main/java/com/nexus/stream/domain/usecase/HeaderPolicy.kt app/src/main/java/com/nexus/stream/domain/usecase/ResolvePlaybackSource.kt
(empty, exit code 1)
```
- diff stat (`git diff --stat main...HEAD`):
```
 .../nexus/stream/domain/model/PlaybackSource.kt    |  31 +++
 .../nexus/stream/domain/usecase/HeaderPolicy.kt    |  72 ++++++
 .../stream/domain/usecase/ResolvePlaybackSource.kt | 162 ++++++++++++
 .../stream/domain/usecase/HeaderPolicyTest.kt      | 177 +++++++++++++
 .../domain/usecase/ResolvePlaybackSourceTest.kt    | 273 +++++++++++++++++++++
 5 files changed, 715 insertions(+)
```
- New tests list:
  - `app/src/test/java/com/nexus/stream/domain/usecase/HeaderPolicyTest.kt` (H1–H12)
  - `app/src/test/java/com/nexus/stream/domain/usecase/ResolvePlaybackSourceTest.kt` (R1–R24)

Public API for later prompts:
- `sealed interface PlaybackSource` (`Direct`, `DirectWithHeaders`, `Torrent`, `External`, `Unsupported`)
- `enum class UnsupportedReason` (`NO_SOURCE`, `BAD_URL`, `BAD_SCHEME`, `INVALID_HASH`)
- `fun sanitizeRequestHeaders(raw: Map<String, String>): Map<String, String>`
- `fun resolvePlaybackSource(source: StreamSource, hint: EpisodeHint?): PlaybackSource`

Known limitations:
- RTMP playback is marked unsupported (`BAD_SCHEME`) because libVLC RTMP playback on Android is unverified.
- Non-HTTP/HTTPS direct schemes (`file`, `content`, `intent`, `data`, `ftp`) are rejected per security and playback policy.

Open questions:
- None.
