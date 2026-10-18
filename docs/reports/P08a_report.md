## Report: P08a
Status: done
Access proof (raw grep output):
480:## 9. Torrent Engine
482:### 9.1 Interface (domain), contract from P08a (D101)
506:### 9.2 Pipeline
522:### 9.3 File selection
534:### 9.4 Local HTTP server
543:### 9.5 Lifecycle, cache, privacy
629:## 13. Verify Before Coding (⚠️ list)

126:| D99 | Torrent library: **jlibtorrent 2.0.12.9** (`com.
127:| D100 | P08 split: **P08a** = headless torrent logic (d
128:| D101 | `TorrentEngine` contract (supersedes the old 9.
129:| D102 | Native code sits behind `TorrentBackend` (`data
130:| D103 | File choice: `pickFile` ignores sample files wh
131:| D104 | Buffering target: pieces covering the first 8 M
132:| D105 | Test torrents: the app ships no content, so the

Carry-over (branch, checkAll):
- Branch: `p08a-torrent-core-logic` (created from `main` at `7682fbe`)
- Initial `git status`: clean
- Initial `./gradlew checkAll`: ✔ PASSED

Files created / modified:
- Created:
  - `app/src/main/java/com/nexus/stream/domain/model/TorrentModels.kt`
  - `app/src/main/java/com/nexus/stream/domain/repository/TorrentEngine.kt`
  - `app/src/main/java/com/nexus/stream/domain/usecase/TorrentFilePicker.kt`
  - `app/src/main/java/com/nexus/stream/domain/usecase/PieceMapping.kt`
  - `app/src/main/java/com/nexus/stream/data/torrent/TorrentBackend.kt`
  - `app/src/main/java/com/nexus/stream/data/torrent/DefaultTorrentEngine.kt`
  - `app/src/test/java/com/nexus/stream/domain/usecase/TorrentFilePickerTest.kt`
  - `app/src/test/java/com/nexus/stream/domain/usecase/PieceMappingTest.kt`
  - `app/src/test/java/com/nexus/stream/data/torrent/DefaultTorrentEngineTest.kt`
- Modified:
  - `app/src/main/java/com/nexus/stream/domain/model/AppError.kt` (added `Torrent(val reason: TorrentFailReason) : AppError` and default `= null` for `Unknown.message`)
  - `app/src/main/java/com/nexus/stream/presentation/common/AddonErrorText.kt` (added `is AppError.Torrent` to exhaustive `when`, authorized by user)
  - `app/src/debug/java/com/nexus/stream/dev/AddonTesterViewModel.kt` (added `is AppError.Torrent` to exhaustive `when`, authorized by user)

Edge cases F1-F15, M1-M9, T1-T17: implemented / not implemented (reasons):
- F1-F15: Implemented in `TorrentFilePicker.kt` and tested in `TorrentFilePickerTest.kt`.
- M1-M9: Implemented in `PieceMapping.kt` and tested in `PieceMappingTest.kt`.
- T1-T17: Implemented in `DefaultTorrentEngine.kt` and tested in `DefaultTorrentEngineTest.kt`.

Suppressions used (max 2, with reasons):
- Added in P08a: 0 suppressions added. All code fully adheres to Detekt complexity, line length, return count, and function count rules.

Simplification pass:
- Decomposed `DefaultTorrentEngine.prepare` into focused private helper methods (`awaitMetadataOrNull`, `resolveFileIndex`, `pollBuffering`) to keep function lengths <= 60 lines and cyclomatic complexity <= 14.
- Defined `sealed interface PieceMapping` in `PieceMapping.kt` to satisfy Detekt's `MatchingDeclarationName` without suppressions.
- Limited return statements in `pickFile` and `resolveFileIndex` to <= 2 to satisfy Detekt `ReturnCount`.
- Wrapped `backend.remove(hash)` in `runCatching` under `NonCancellable` to avoid throwing inside a `finally` block.

Assumptions made:
- Trackers starting with `http://`, `https://`, and `udp://` (case-insensitive) are retained and encoded with UTF-8 `URLEncoder`.
- Default value of `message` in `AppError.Unknown` is `null` to support both constructor shapes.

Deviations:
- Prompt negative scope originally listed "Do NOT touch: anything under presentation/". Adding `AppError.Torrent` caused exhaustive `when` compiler errors in `AddonErrorText.kt` (presentation) and `AddonTesterViewModel.kt` (debug). Stopped and asked user; user authorized updating both files to handle `AppError.Torrent`.

Verification: checkAll ✔/✘ · native-import grep · log grep · diff stat · new tests list:
- checkAll: ✔ PASSED (`assembleDebug`, unit tests, `detektDebug`, `detektDebugUnitTest`, `spotlessCheck`, `hexGuard`)
- native-import grep (`grep -rn -E "com\.frostwire|org\.libtorrent4j|import android\." app/src/main/java/com/nexus/stream/domain app/src/main/java/com/nexus/stream/data/torrent`): ✔ PASSED (0 occurrences)
- log grep (`grep -rn -E "Log\.|println\("` over new main files): ✔ PASSED (0 occurrences)
- diff stat (`git diff --stat main...HEAD`):
```
 app/src/debug/java/com/nexus/stream/dev/AddonTesterViewModel.kt   |   1 +
 app/src/main/java/com/nexus/stream/data/torrent/DefaultTorrentEngine.kt    | 235 +++++++++
 app/src/main/java/com/nexus/stream/data/torrent/TorrentBackend.kt          |  47 ++
 app/src/main/java/com/nexus/stream/domain/model/AppError.kt                |   6 +-
 app/src/main/java/com/nexus/stream/domain/model/TorrentModels.kt           |  58 +++
 app/src/main/java/com/nexus/stream/domain/repository/TorrentEngine.kt      |  16 +
 app/src/main/java/com/nexus/stream/domain/usecase/PieceMapping.kt          |  59 +++
 app/src/main/java/com/nexus/stream/domain/usecase/TorrentFilePicker.kt     |  61 +++
 app/src/main/java/com/nexus/stream/presentation/common/AddonErrorText.kt   |  10 +-
 app/src/test/java/com/nexus/stream/data/torrent/DefaultTorrentEngineTest.kt       | 539 +++++++++++++++++++++
 app/src/test/java/com/nexus/stream/domain/usecase/PieceMappingTest.kt      | 132 +++++
 app/src/test/java/com/nexus/stream/domain/usecase/TorrentFilePickerTest.kt | 177 +++++++
 12 files changed, 1339 insertions(+), 2 deletions(-)
```
- new tests list:
  - `TorrentFilePickerTest` (15 unit tests: F1-F15)
  - `PieceMappingTest` (9 unit tests: M1-M9)
  - `DefaultTorrentEngineTest` (17 unit tests: T1-T17)

Public API for later prompts:
- `TorrentEngine` (`prepare(TorrentRequest): Flow<TorrentState>`, `selectFile(String, Int)`, `stop(String)`)
- `TorrentRequest` (`infoHash: String`, `trackers: List<String>`, `fileIdx: Int?`, `episodeHint: EpisodeHint?`)
- `EpisodeHint` (`season: Int`, `episode: Int`)
- `TorrentFile` (`index: Int`, `path: String`, `sizeBytes: Long`)
- `TorrentState` (`FetchingMetadata`, `NeedsFilePick(List<TorrentFile>)`, `Buffering(Int, Long, Int)`, `Ready(Int, String, Long)`, `Failed(AppError)`)
- `TorrentFailReason` (`INVALID_HASH`, `METADATA_TIMEOUT`, `NO_VIDEO_FILE`, `STORAGE`, `ENGINE_UNAVAILABLE`)
- `TorrentConfig` (`HEAD_BYTES`, `TAIL_BYTES`, `METADATA_TIMEOUT_MS`, `POLL_INTERVAL_MS`)
- `TorrentBackend` (`add(String, String)`, `awaitMetadata(String): TorrentMetadata`, `prioritize(String, Int, List<Int>)`, `snapshot(String, List<Int>): TorrentSnapshot`, `remove(String)`)
- `BackendFile` (`index: Int`, `path: String`, `sizeBytes: Long`, `offsetBytes: Long`)
- `TorrentMetadata` (`files: List<BackendFile>`, `pieceLength: Long`)
- `TorrentSnapshot` (`piecesHave: Int`, `downloadSpeedBps: Long`, `peers: Int`)
- `TorrentBackendException` (`reason: TorrentFailReason`)
- `pickFile(List<TorrentFile>, EpisodeHint?): Int?`
- `isVideoFile(String): Boolean`
- `targetPieces(Long, Long, Long, Long, Long): TargetPieces`
- `bufferingPercent(Int, Int): Int`
- `DefaultTorrentEngine(TorrentBackend)`

Known limitations:
- Headless logic and orchestration only; native `JLibTorrentBackend` implementation arrives in P08a.2.
- Local HTTP streaming server arrives in P08b.

Open questions:
- None.
