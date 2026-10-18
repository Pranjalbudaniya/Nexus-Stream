## Report: P08a.2
Status: done
Access proof (raw grep output):
```
485:## 9. Torrent Engine
487:### 9.1 Interface (domain), contract from P08a (D101)
511:### 9.2 Pipeline
527:### 9.3 File selection
539:### 9.4 Local HTTP server
550:### 9.5 Lifecycle, cache, privacy
636:## 13. Verify Before Coding (⚠️ list)

126:| D99 | Torrent library: **jlibtorrent 2.0.12.9** (`com.
127:| D100 | P08 split: **P08a** = headless torrent logic (d
128:| D101 | `TorrentEngine` contract (supersedes the old 9.
129:| D102 | Native code sits behind `TorrentBackend` (`data
130:| D103 | File choice: `pickFile` ignores sample files wh
131:| D104 | Buffering target: pieces covering the first 8 M
132:| D105 | Test torrents: the app ships no content, so the
133:| D106 | Direct streams and headers: every stream plays 
134:| D107 | Downloads (P16) cover every stream type: direct
135:| D108 | P08a accepted (report of 2026-10-18). `AppError
136:| D109 | P08a.2 decisions: `JLibTorrentBackend` is the o
```
Dependency proof (raw curl, sha256, readelf output):
```
$ curl -sI https://dl.frostwire.com/maven/com/frostwire/jlibtorrent/2.0.12.9/jlibtorrent-2.0.12.9.pom | head -n 5
HTTP/2 200 
server: nginx
date: Sun, 18 Oct 2026 09:40:39 GMT
content-type: application/octet-stream
content-length: 1311

$ curl -sIL https://dl.frostwire.com/maven/com/frostwire/jlibtorrent/2.0.12.9/jlibtorrent-2.0.12.9.jar | grep -E "HTTP/|location:"
HTTP/2 302 
location: https://github.com/frostwire/frostwire-jlibtorrent/releases/download/release/2.0.12.9/jlibtorrent-2.0.12.9.jar
HTTP/2 302 
location: https://release-assets.githubusercontent.com/github-production-release-asset/22651419/8b6b6cca-9726-4bcb-9562-f6fc0228d978?...
HTTP/2 200 

$ curl -sI https://dl.frostwire.com/maven/com/frostwire/jlibtorrent-android-arm64/2.0.12.9/jlibtorrent-android-arm64-2.0.12.9.pom | head -n 5
HTTP/2 200 
server: nginx
date: Sun, 18 Oct 2026 09:40:40 GMT
content-type: application/octet-stream
content-length: 1229

$ curl -sIL https://dl.frostwire.com/maven/com/frostwire/jlibtorrent-android-arm64/2.0.12.9/jlibtorrent-android-arm64-2.0.12.9.jar | grep -E "HTTP/|location:"
HTTP/2 302 
location: https://github.com/frostwire/frostwire-jlibtorrent/releases/download/release/2.0.12.9/jlibtorrent-android-arm64-2.0.12.9.jar
HTTP/2 302 
location: https://release-assets.githubusercontent.com/github-production-release-asset/22651419/428bab5f-480d-4690-83a9-6424dff796d8?...
HTTP/2 200 

$ sha256sum /tmp/jlibtorrent-android-arm64-2.0.12.9.jar
8e48600b74a9f497ce2c7e1e6a025ce550f039658baef986ad634298758d5b9d  /tmp/jlibtorrent-android-arm64-2.0.12.9.jar

$ unzip -l /tmp/jlibtorrent-android-arm64-2.0.12.9.jar
Archive:  /tmp/jlibtorrent-android-arm64-2.0.12.9.jar
  Length      Date    Time    Name
---------  ---------- -----   ----
        0  1980-02-01 00:00   lib/
        0  1980-02-01 00:00   lib/arm64-v8a/
 13384864  1980-02-01 00:00   lib/arm64-v8a/libjlibtorrent-2.0.12.9.so
---------                     -------
 13384864                     3 files

$ readelf -lW /tmp/lib/arm64-v8a/libjlibtorrent-2.0.12.9.so | grep -E "Type|LOAD"
  Type           Offset   VirtAddr           PhysAddr           FileSiz  MemSiz   Flg Align
  LOAD           0x000000 0x0000000000000000 0x0000000000000000 0xc210c0 0xc210c0 R E 0x4000
  LOAD           0xc210c0 0x0000000000c250c0 0x0000000000c250c0 0x0927c0 0x092f40 RW  0x4000
  LOAD           0xcb3880 0x0000000000cbb880 0x0000000000cbb880 0x005bf8 0x00bd38 RW  0x4000
```
API map (capability -> actual jlibtorrent member, from javap):
```
  public void removeListener(com.frostwire.jlibtorrent.AlertListener);
  public void start(com.frostwire.jlibtorrent.SessionParams);
  public void start();
  public void stop();
  public void restart();
  public long downloadRate();
  public int downloadRateLimit();
  public void downloadRateLimit(int);
  public void startDht();
  public void stopDht();
  public void download(com.frostwire.jlibtorrent.TorrentInfo, java.io.File, java.io.File, com.frostwire.jlibtorrent.Priority[], java.util.List<com.frostwire.jlibtorrent.TcpEndpoint>, com.frostwire.jlibtorrent.swig.torrent_flags_t);
  public void download(java.lang.String, java.io.File, com.frostwire.jlibtorrent.swig.torrent_flags_t);
  public void download(com.frostwire.jlibtorrent.TorrentInfo, java.io.File);
  public void remove(com.frostwire.jlibtorrent.TorrentHandle, com.frostwire.jlibtorrent.swig.remove_flags_t);
  public void remove(com.frostwire.jlibtorrent.TorrentHandle);
  public static final com.frostwire.jlibtorrent.swig.remove_flags_t DELETE_FILES;
  public static final com.frostwire.jlibtorrent.swig.remove_flags_t DELETE_PARTFILE;
  public void postTorrentUpdates(com.frostwire.jlibtorrent.swig.status_flags_t);
  public void removeTorrent(com.frostwire.jlibtorrent.TorrentHandle, com.frostwire.jlibtorrent.swig.remove_flags_t);
  public void removeTorrent(com.frostwire.jlibtorrent.TorrentHandle);
  public static final com.frostwire.jlibtorrent.swig.status_flags_t QUERY_DISTRIBUTED_COPIES;
  public static final com.frostwire.jlibtorrent.swig.status_flags_t QUERY_ACCURATE_DOWNLOAD_COUNTERS;
  public static final com.frostwire.jlibtorrent.swig.status_flags_t QUERY_LAST_SEEN_COMPLETE;
  public static final com.frostwire.jlibtorrent.swig.status_flags_t QUERY_PIECES;
  public static final com.frostwire.jlibtorrent.swig.status_flags_t QUERY_VERIFIED_PIECES;
  public static final com.frostwire.jlibtorrent.swig.status_flags_t QUERY_TORRENT_FILE;
  public static final com.frostwire.jlibtorrent.swig.status_flags_t QUERY_NAME;
  public static final com.frostwire.jlibtorrent.swig.status_flags_t QUERY_SAVE_PATH;
  public static final com.frostwire.jlibtorrent.swig.deadline_flags_t ALERT_WHEN_AVAILABLE;
  public boolean havePiece(int);
  public com.frostwire.jlibtorrent.TorrentStatus status(boolean);
  public com.frostwire.jlibtorrent.TorrentStatus status();
  public com.frostwire.jlibtorrent.TorrentStatus status(com.frostwire.jlibtorrent.swig.status_flags_t);
  public void removeUrlSeed(java.lang.String);
  public void removeHttpSeed(java.lang.String);
  public void prioritizePieces(com.frostwire.jlibtorrent.Priority[]);
  public void prioritizeFiles(com.frostwire.jlibtorrent.Priority[]);
  public void setPieceDeadline(int, int, com.frostwire.jlibtorrent.swig.deadline_flags_t);
  public com.frostwire.jlibtorrent.FileStorage files();
  public int pieceLength();
  public int pieceLength();
  public void pieceLength(int);
  public com.frostwire.jlibtorrent.TorrentStatus(com.frostwire.jlibtorrent.swig.torrent_status);
  public com.frostwire.jlibtorrent.swig.torrent_status swig();
  public int downloadRate();
  public int downloadPayloadRate();
  public abstract void alert(com.frostwire.jlibtorrent.alerts.Alert<?>);
public final class com.frostwire.jlibtorrent.alerts.MetadataReceivedAlert extends com.frostwire.jlibtorrent.alerts.TorrentAlert<com.frostwire.jlibtorrent.swig.metadata_received_alert> {
  com.frostwire.jlibtorrent.alerts.MetadataReceivedAlert(com.frostwire.jlibtorrent.swig.metadata_received_alert);
public final class com.frostwire.jlibtorrent.alerts.TorrentErrorAlert extends com.frostwire.jlibtorrent.alerts.TorrentAlert<com.frostwire.jlibtorrent.swig.torrent_error_alert> {
  com.frostwire.jlibtorrent.alerts.TorrentErrorAlert(com.frostwire.jlibtorrent.swig.torrent_error_alert);
public final class com.frostwire.jlibtorrent.alerts.FileErrorAlert extends com.frostwire.jlibtorrent.alerts.TorrentAlert<com.frostwire.jlibtorrent.swig.file_error_alert> {
  com.frostwire.jlibtorrent.alerts.FileErrorAlert(com.frostwire.jlibtorrent.swig.file_error_alert);
```

| §4.2 Capability | Actual jlibtorrent member |
|---|---|
| Lazy session start | `SessionManager.start()`, `SessionManager.addListener(AlertListener)` |
| Save root directory | `File(context.cacheDir, "torrents")` |
| Add magnet | `SessionManager.download(String magnetUri, File saveDir, torrent_flags_t flags)` |
| Find torrent handle | `SessionManager.find(Sha1Hash hash)` |
| Check metadata presence | `TorrentHandle.status().hasMetadata()`, `TorrentHandle.torrentFile()` |
| File list & metadata | `TorrentInfo.files()`, `FileStorage.numFiles()`, `FileStorage.filePath()`, `FileStorage.fileSize()`, `FileStorage.fileOffset()`, `TorrentInfo.pieceLength()` |
| File priority ignoring/choosing | `TorrentHandle.prioritizeFiles(Priority[])`, `Priority.array(Priority, int)` |
| Sequential download | `TorrentFlags.SEQUENTIAL_DOWNLOAD`, `TorrentHandle.setFlags(...)` |
| Piece deadlines | `TorrentHandle.setPieceDeadline(int pieceIndex, int deadlineMs)` |
| Piece availability | `TorrentHandle.havePiece(int pieceIndex)` |
| Snapshot stats | `TorrentStatus.downloadRate()`, `TorrentStatus.numPeers()` |
| Alerts & Error handling | `AlertListener.alert(Alert<?>)`, `MetadataReceivedAlert`, `TorrentErrorAlert`, `FileErrorAlert` |
| Torrent removal | `SessionManager.remove(TorrentHandle, remove_flags_t)` with `SessionHandle.DELETE_FILES` |

Carry-over (branch, checkAll):
- Branch: `p08a-2-jlibtorrent-backend` (branched from `main` at `cb53f3d`)
- Baseline `checkAll`: passes (57 actionable tasks up-to-date)

Files created / modified:
- Created:
  - `app/src/main/java/com/nexus/stream/data/torrent/JLibTorrentBackend.kt`
  - `app/src/main/java/com/nexus/stream/di/TorrentModule.kt`
  - `app/src/main/java/com/nexus/stream/domain/usecase/MagnetLink.kt`
  - `app/src/debug/java/com/nexus/stream/dev/TorrentTesterScreen.kt`
  - `app/src/debug/java/com/nexus/stream/dev/TorrentTesterViewModel.kt`
  - `app/src/test/java/com/nexus/stream/domain/usecase/MagnetLinkTest.kt`
  - `app/src/test/java/com/nexus/stream/dev/TorrentTesterViewModelTest.kt`
- Modified:
  - `settings.gradle.kts`
  - `gradle/libs.versions.toml`
  - `app/build.gradle.kts`
  - `app/src/main/java/com/nexus/stream/domain/model/TorrentModels.kt`
  - `app/src/main/java/com/nexus/stream/domain/usecase/TargetPieces.kt` (renamed from `PieceMapping.kt`)
  - `app/src/debug/res/values/strings.xml`
  - `app/src/debug/java/com/nexus/stream/dev/DevToolsActivity.kt`

Edge cases G1-G14, V1-V8: implemented / not implemented (reasons):
- G1-G14: implemented (all 14 passed in `MagnetLinkTest`)
  - G1: standard magnet with trackers -> infoHash lowercased, trackers decoded
  - G2: uppercase hash inside magnet -> lowercased
  - G3: raw 40-hex string -> accepted with empty trackers
  - G4: raw hash with whitespace/newline -> accepted
  - G5: btmh only -> null
  - G6: hybrid btmh + btih -> uses btih
  - G7: base32 btih -> null
  - G8: http URL -> null
  - G9: blank or empty -> null
  - G10: uppercase scheme and keys -> accepted, 1 tracker
  - G11: 39 and 41 hex chars -> null
  - G12: magnet with no tr and dn with escapes -> trackers empty
  - G13: bad escape %ZZ + good tracker -> bad tracker dropped, good tracker kept
  - G14: wrapped magnet across lines -> parsed as single line
- V1-V8: implemented (all 8 passed in `TorrentTesterViewModelTest`)
  - V1: start with garbage -> inputInvalid = true, running = false, prepare not called
  - V2: start with valid magnet -> input cleared, running = true, prepare called with lowercase hash & trackers, states emitted
  - V3: NeedsFilePick emitted -> selectFile calls engine with hash and index
  - V4: stop while running -> job cancelled, stop called on engine, state = null, running = false
  - V5: Failed emitted and flow completes -> running = false, state = Failed, Start works again
  - V6: ViewModel cleared while running -> collection cancelled
  - V7: after V2 uiState.toString() -> does not contain hash or trackers
  - V8: start twice -> prepare called once

Suppressions used (max 2, with reasons):
- 0 suppressions used.

Simplification pass:
- Extracted `runCatchingLibraryCall` as a private top-level inline function in `JLibTorrentBackend.kt` to safely initialize cause via `Throwable.initCause`, preserve `CancellationException`, rethrow `TorrentBackendException`, and eliminate swallowed or generic exception warnings without adding suppressions.
- Combined `TorrentErrorAlert` and `FileErrorAlert` handling in `onTorrentAlertError(TorrentAlert<*>)`, reducing class method count in `JLibTorrentBackend` to 9 (below detekt limit of 11).
- Structured `parseMagnetLink` and `parseMagnetUri` to use functional mapping and early guard clauses, keeping cyclomatic complexity at 4 and returns at 2.
- Kept `MagnetLink.buildMagnet` in the companion object of `data class MagnetLink` so P07 stream feature code continues to operate without alteration.
- Removed dummy marker `sealed interface PieceMapping` by renaming the file to `TargetPieces.kt`.

Assumptions made:
- Release minification is not currently enabled in `buildTypes`, so no keep rules were added to `proguard-rules.pro`. (The FrostWire keep rule `-keep class com.frostwire.jlibtorrent.swig.libtorrent_jni { *; }` is documented if R8 minification is added).
- Build targets arm64-v8a phone devices; x86 emulators receive `ENGINE_UNAVAILABLE` as specified.

Deviations: None.

Verification: checkAll ✔/✘ · isolation grep · log grep · release manifest check · diff stat · new tests list:
- `checkAll`: ✔ PASSED (57 actionable tasks executed/up-to-date, 0 warnings, 0 lint failures)
- Isolation grep:
```
$ grep -rln "com\.frostwire" app/src
app/src/main/java/com/nexus/stream/data/torrent/JLibTorrentBackend.kt
```
- Log grep:
```
$ grep -rn -E "Log\.|println\(" app/src/main/java/com/nexus/stream/data/torrent/JLibTorrentBackend.kt app/src/main/java/com/nexus/stream/di/TorrentModule.kt app/src/main/java/com/nexus/stream/domain/usecase/MagnetLink.kt app/src/debug/java/com/nexus/stream/dev/TorrentTesterScreen.kt app/src/debug/java/com/nexus/stream/dev/TorrentTesterViewModel.kt
(empty, exit code 1)
```
- Release manifest check:
```
$ ./gradlew :app:processReleaseManifest
BUILD SUCCESSFUL in 1s
$ find app/build/intermediates/merged_manifests/release -name "AndroidManifest.xml" | xargs grep "DevToolsActivity" || echo "DevToolsActivity absent from release merged manifest"
DevToolsActivity absent from release merged manifest
```
- Diff stat (`git diff --stat main...HEAD`):
```
 app/build.gradle.kts                               |   2 +
 .../java/com/nexus/stream/dev/DevToolsActivity.kt  |  27 ++
 .../com/nexus/stream/dev/TorrentTesterScreen.kt    | 279 +++++++++++++++++++++
 .../com/nexus/stream/dev/TorrentTesterViewModel.kt | 130 ++++++++++
 app/src/debug/res/values/strings.xml               |  18 ++
 .../stream/data/torrent/JLibTorrentBackend.kt      | 262 +++++++++++++++++++
 .../main/java/com/nexus/stream/di/TorrentModule.kt |  23 ++
 .../com/nexus/stream/domain/model/TorrentModels.kt |   1 +
 .../com/nexus/stream/domain/usecase/MagnetLink.kt  | 110 ++++++--
 .../usecase/{PieceMapping.kt => TargetPieces.kt}   |   4 +-
 .../nexus/stream/dev/TorrentTesterViewModelTest.kt | 220 ++++++++++++++++
 .../nexus/stream/domain/usecase/MagnetLinkTest.kt  | 145 ++++++++---
 gradle/libs.versions.toml                          |   3 +
 settings.gradle.kts                                |   4 +
 14 files changed, 1161 insertions(+), 67 deletions(-)
```
- New tests list:
  - `app/src/test/java/com/nexus/stream/domain/usecase/MagnetLinkTest.kt` (G1–G14)
  - `app/src/test/java/com/nexus/stream/dev/TorrentTesterViewModelTest.kt` (V1–V8)

Owner phone check B1-B12: pending owner

Public API for later prompts:
- `data class MagnetLink(val infoHash: String, val trackers: List<String>)`
- `fun parseMagnetLink(text: String): MagnetLink?`
- `TorrentModule` (`TorrentBackend` -> `JLibTorrentBackend`, `TorrentEngine` -> `DefaultTorrentEngine`)
- `TorrentTesterScreen(state, onEvent, onBack)`

Known limitations:
- Initial buffering has no stall timeout when 0 peers are available (D110; scheduled for P08b or P08a.fix1).
- Native library packaged for `arm64-v8a` only.

Open questions: None.
