## Report: P08b.fix1
Status: done

Raw outputs (step 1):
- `git branch --show-current`:
```
p08b-loopback-range-server
```

- `git log main..HEAD --oneline`:
```
5d467aa (HEAD -> p08b-loopback-range-server) fix(server): socket integration tests, rename RangeSpec, and tighten test assertions
8dea9c8 docs(report): P08b report
d997f3d feat(server): implement generic loopback HTTP server with Range support
```

- `git diff --stat main...HEAD`:
```
 .../com/nexus/stream/data/stream/HttpMessages.kt   | 126 ++++
 .../com/nexus/stream/data/stream/LoopbackServer.kt | 350 +++++++++++
 .../com/nexus/stream/data/stream/RangeRoute.kt     | 190 ++++++
 .../com/nexus/stream/domain/usecase/RangeSpec.kt   |  84 +++
 .../nexus/stream/data/stream/HttpMessagesTest.kt   | 143 +++++
 .../nexus/stream/data/stream/LoopbackServerTest.kt | 644 +++++++++++++++++++++
 .../com/nexus/stream/data/stream/RangeRouteTest.kt | 384 ++++++++++++
 .../nexus/stream/domain/usecase/HttpRangeTest.kt   | 107 ++++
 docs/reports/P08b_report.md                        | 105 ++++
 9 files changed, 2133 insertions(+)
```

- `grep -rn -E "import android\.|Log\.|println\(|GlobalScope" app/src/main/java/com/nexus/stream/domain/usecase/RangeSpec.kt app/src/main/java/com/nexus/stream/data/stream`:
```
(exit code 1 - 0 matches)
```

- `grep -n "127.0.0.1" app/src/main/java/com/nexus/stream/data/stream/LoopbackServer.kt`:
```
95:            val ss = ServerSocket(0, SERVER_BACKLOG, InetAddress.getByName("127.0.0.1"))
```

- `grep -n -E "0\.0\.0\.0|InetSocketAddress\(" app/src/main/java/com/nexus/stream/data/stream/LoopbackServer.kt`:
```
(exit code 1 - 0 matches)
```

- `grep -rn -E "@Suppress|@file:Suppress" app/src/main/java/com/nexus/stream/domain/usecase/RangeSpec.kt app/src/main/java/com/nexus/stream/data/stream`:
```
(exit code 1 - 0 matches)
```

- `grep -n -E "class LoopbackServer|constructor|@Inject|@Singleton" app/src/main/java/com/nexus/stream/data/stream/LoopbackServer.kt`:
```
71:@Singleton
72:class LoopbackServer internal constructor(
76:    @Inject
77:    constructor(
```

- `./gradlew testDebugUnitTest --tests "com.nexus.stream.data.stream.*" --tests "com.nexus.stream.domain.usecase.HttpRangeTest"`:
```
<testsuite name="com.nexus.stream.data.stream.HttpMessagesTest" tests="15" skipped="0" failures="0" errors="0" timestamp="2026-10-19T02:10:38.550Z" hostname="mambo" time="0.052">
<testsuite name="com.nexus.stream.data.stream.LoopbackServerTest" tests="18" skipped="0" failures="0" errors="0" timestamp="2026-10-19T02:10:38.607Z" hostname="mambo" time="0.711">
<testsuite name="com.nexus.stream.data.stream.RangeRouteTest" tests="15" skipped="0" failures="0" errors="0" timestamp="2026-10-19T02:10:39.319Z" hostname="mambo" time="0.044">
<testsuite name="com.nexus.stream.domain.usecase.HttpRangeTest" tests="17" skipped="0" failures="0" errors="0" timestamp="2026-10-19T02:10:39.364Z" hostname="mambo" time="0.003">

BUILD SUCCESSFUL in 4s
```

parseRequestHead and formatResponseHead check (answers a-d):
Raw source of `parseRequestHead`:
```kotlin
fun parseRequestHead(head: String): HttpRequest? {
    if (head.isBlank()) return null

    val lines = head.split(Regex("\r\n|\n"))
    val firstLine = lines.firstOrNull()?.takeIf { it.isNotBlank() }
    val requestLine = firstLine?.let { parseRequestLine(it) }
    val headers = if (requestLine != null) parseHeaders(lines) else null

    return if (requestLine != null && headers != null) {
        HttpRequest(requestLine.first, requestLine.second, headers)
    } else {
        null
    }
}

private fun parseRequestLine(firstLine: String): Pair<String, String>? {
    val parts = firstLine.split(' ')
    val validParts = parts.takeIf { it.size == REQUEST_LINE_PARTS_COUNT } ?: return null
    val (method, target, version) = validParts
    val isMethodValid = METHOD_REGEX.matches(method)
    val isTargetValid = target.startsWith('/')
    val isVersionValid = version == "HTTP/1.0" || version == "HTTP/1.1"

    return if (isMethodValid && isTargetValid && isVersionValid) {
        val rawPath = target.substringBefore('?').substringBefore('#')
        decodePath(rawPath)?.let { path -> method to path }
    } else {
        null
    }
}
```

Signature of `formatResponseHead`:
```kotlin
fun formatResponseHead(
    status: Int,
    headers: List<Pair<String, String>>,
): String
```

Answers to (a)-(d):
(a) **Does `parseRequestHead` accept methods other than GET and HEAD (any uppercase letters)?**
Yes. `parseRequestLine` tests `METHOD_REGEX.matches(method)` where `METHOD_REGEX = Regex("^[A-Z]+$")`. Any uppercase letter method (POST, HEAD, DELETE, OPTIONS, CUSTOM, etc.) parses successfully.
(b) **Which duplicate header wins?**
The first duplicate header wins. In `parseHeaders`:
```kotlin
if (!headers.containsKey(entry.first)) {
    headers[entry.first] = entry.second
}
```
Existing keys are not replaced.
(c) **Why does `formatResponseHead` have a third parameter and who uses it?**
It does NOT have a third parameter in code. Its signature is `formatResponseHead(status: Int, headers: List<Pair<String, String>>): String`. In the P08b report, `statusText: String? = null` was inadvertently listed in the markdown text under "Public API for later prompts". The implementation never contained that parameter and no caller uses it.
(d) **The actual values of the head timeout default and `STREAM_CHUNK_BYTES`:**
- `DEFAULT_HEAD_TIMEOUT_MS = 10_000` (10 seconds, in `LoopbackServer.kt:24`).
- `STREAM_CHUNK_BYTES = 128 * 1024` (128 KiB, in `RangeRoute.kt:7`).
Both values in the source code were already set to the P08b prompt specifications (10,000 ms and 128 KiB), although the P08b report text had inaccurately described them as 5000 and 64 KiB.

Changes made:
- Renamed `HttpRange.kt` to `RangeSpec.kt` via `git mv` so the file name matches its top-level class declaration `RangeSpec`, completely eliminating the need for detekt file-level `@Suppress("MatchingDeclarationName", "ktlint:standard:filename")`.
- Strengthened `HM5` in `HttpMessagesTest.kt` to assert that uppercase methods (GET, HEAD, POST, CUSTOM) parse while non-uppercase or invalid methods return null.
- Strengthened `HM8` in `HttpMessagesTest.kt` to assert that the first header wins among duplicate headers.
- Extended `RawResponse` and `sendRaw` in `LoopbackServerTest.kt` to extract and expose raw `bodyBytes: ByteArray`.
- Implemented `InMemorySource` and added socket-level integration tests `LS16`, `LS17`, and `LS18` in `LoopbackServerTest.kt` verifying `LoopbackServer` and `RangeRoute` end-to-end over real TCP sockets.

Files modified:
- `app/src/main/java/com/nexus/stream/domain/usecase/RangeSpec.kt`: Renamed from `HttpRange.kt` and removed `@file:Suppress`.
- `app/src/test/java/com/nexus/stream/data/stream/HttpMessagesTest.kt`: Added explicit assertions for uppercase methods (POST, HEAD, CUSTOM) in `HM5` and duplicate header resolution in `HM8`.
- `app/src/test/java/com/nexus/stream/data/stream/LoopbackServerTest.kt`: Added `bodyBytes` parsing to `sendRaw`, `InMemorySource`, and tests `LS16`, `LS17`, `LS18`.

Deviations from the P08b prompt that were not reported before (constants, extra parameter, constructor):
- None in the code itself. The previously reported differences (`headTimeoutMs: 5000`, `STREAM_CHUNK_BYTES: 64 KiB`, `statusText: String?`, non-`internal` constructor) were erroneous documentation typos in the P08b report summary text. In the implementation:
  - `DEFAULT_HEAD_TIMEOUT_MS` was already `10_000`.
  - `STREAM_CHUNK_BYTES` was already `128 * 1024`.
  - `formatResponseHead` was already two parameters `(status: Int, headers: List<Pair<String, String>>)`.
  - `LoopbackServer` already had `internal constructor(dispatcher, config = ServerConfig())` and `@Inject constructor(@IoDispatcher dispatcher: CoroutineDispatcher) : this(dispatcher, ServerConfig())`.

Verification: checkAll ✔/✘ · new tests list (LS16-LS18):
- `./gradlew checkAll`: ✔ passed (all 57 actionable tasks up-to-date or passed, including assembleDebug, detekt, spotless, hexGuard, testDebugUnitTest).
- New tests:
  - `LS16_rangeRoute_partialContentOverSocket`: GET request with `Range: bytes=10-19` returns 206, `Content-Range: bytes 10-19/1000`, `Content-Length: 10`, and exact body bytes `10..19` over a real loopback TCP socket.
  - `LS17_rangeRoute_headNoRangeOverSocket`: HEAD request returns 200, `Content-Length: 1000`, `Accept-Ranges: bytes`, and 0 body bytes before connection close over a real loopback TCP socket.
  - `LS18_rangeRoute_postMethodNotAllowedOverSocket`: POST request returns 405 with `Allow: GET, HEAD` over a real loopback TCP socket.

Open questions:
- None.
