## Report: P08a.2.fix1
Status: done
Old test functions on main (raw):
```
9:    fun buildMagnet_valid40HexHash() {
28:    fun buildMagnet_valid32Base32Hash() {
41:    fun buildMagnet_invalidHashReturnsNull() {
51:    fun buildMagnet_emptyNameAndNoTrackers() {
```
Current test functions (raw):
```
10:    fun buildMagnet_valid40HexHash() {
29:    fun buildMagnet_valid32Base32Hash() {
42:    fun buildMagnet_invalidHashReturnsNull() {
52:    fun buildMagnet_emptyNameAndNoTrackers() {
66:    fun G1_standardMagnet_parsesHashAndTrackers() {
76:    fun G2_uppercaseHashInsideMagnet_lowercased() {
85:    fun G3_rawHexHash_acceptedWithNoTrackers() {
91:    fun G4_rawHashWithSpacesAndTrailingNewline_accepted() {
98:    fun G5_btmhOnly_returnsNull() {
106:    fun G6_hybridBtmhFirstThenBtih_usesBtih() {
115:    fun G7_base32Btih_returnsNull() {
123:    fun G8_httpUrl_returnsNull() {
130:    fun G9_blankOrEmpty_returnsNull() {
136:    fun G10_uppercaseSchemeAndKeys_acceptedOneTracker() {
146:    fun G11_invalidHashLength_returnsNull() {
156:    fun G12_magnetNoTrAndDnWithEscapes_trackersEmpty() {
165:    fun G13_invalidEscape_dropsTrackerOnly() {
174:    fun G14_wrappedAcrossLines_parsedAsOneLine() {
```
Removed production lines in MagnetLink.kt (raw):
```
-object MagnetLink {
-    private val HEX_40_REGEX = Regex("""^[0-9a-fA-F]{40}$""")
-    private val BASE32_32_REGEX = Regex("""^[2-7a-zA-Z]{32}$""")
-
-    fun buildMagnet(
-        infoHash: String,
-        displayName: String?,
-        trackers: List<String>,
-    ): String? {
-        val trimmedHash = infoHash.trim()
-        if (!HEX_40_REGEX.matches(trimmedHash) && !BASE32_32_REGEX.matches(trimmedHash)) {
-            return null
-        }
-        val sb = StringBuilder("magnet:?xt=urn:btih:").append(trimmedHash)
-        if (!displayName.isNullOrBlank()) {
-            val encodedName = encode(displayName.trim())
-            sb.append("&dn=").append(encodedName)
-        for (tracker in trackers) {
-            val trimmedTracker = tracker.trim()
-            if (trimmedTracker.isNotEmpty()) {
-                val encodedTracker = encode(trimmedTracker)
-                sb.append("&tr=").append(encodedTracker)
-        return sb.toString()
-    private fun encode(value: String): String =
-        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
```
Behavioral impact of removed production lines:
None of the removed lines changed the behavior of `buildMagnet`.
- `object MagnetLink {` was converted to `data class MagnetLink(val infoHash: String, val trackers: List<String>) { companion object {` so `MagnetLink` could serve both as the parsed domain model and the container for the static utility `buildMagnet`.
- `HEX_40_REGEX` and `BASE32_32_REGEX` were scoped inside `companion object` and renamed to `BUILD_HEX_40_REGEX` / `BUILD_BASE32_32_REGEX` to avoid conflicting with the file-private `HEX_40_REGEX` used by `parseMagnetLink`. Their regex patterns and matching logic are identical.
- `buildMagnet(...)` and `encode(...)` retain their exact same logic, parameter handling, encoding, and return values.

Restored or matched tests (old name -> restored, or equivalent test name):
- `buildMagnet_valid40HexHash` -> restored (same name, same body)
- `buildMagnet_valid32Base32Hash` -> restored (same name, same body)
- `buildMagnet_invalidHashReturnsNull` -> restored (same name, same body)
- `buildMagnet_emptyNameAndNoTrackers` -> restored (same name, same body)

Files modified:
- `app/src/test/java/com/nexus/stream/domain/usecase/MagnetLinkTest.kt`

Correction to the P08a.2 report (which files were created and which were only modified):
In `docs/reports/P08a_2_report.md`, `MagnetLink.kt` and `MagnetLinkTest.kt` were mistakenly listed under "Created" even though both already existed on `main`. The corrected categorization across P08a.2 is:
- Created:
  - `app/src/main/java/com/nexus/stream/data/torrent/JLibTorrentBackend.kt`
  - `app/src/main/java/com/nexus/stream/di/TorrentModule.kt`
  - `app/src/debug/java/com/nexus/stream/dev/TorrentTesterScreen.kt`
  - `app/src/debug/java/com/nexus/stream/dev/TorrentTesterViewModel.kt`
  - `app/src/test/java/com/nexus/stream/dev/TorrentTesterViewModelTest.kt`
- Modified:
  - `settings.gradle.kts`
  - `gradle/libs.versions.toml`
  - `app/build.gradle.kts`
  - `app/src/main/java/com/nexus/stream/domain/model/TorrentModels.kt`
  - `app/src/main/java/com/nexus/stream/domain/usecase/MagnetLink.kt` (modified: added data class, companion object, and parseMagnetLink)
  - `app/src/main/java/com/nexus/stream/domain/usecase/TargetPieces.kt` (renamed from `PieceMapping.kt`)
  - `app/src/debug/res/values/strings.xml`
  - `app/src/debug/java/com/nexus/stream/dev/DevToolsActivity.kt`
  - `app/src/test/java/com/nexus/stream/domain/usecase/MagnetLinkTest.kt` (modified: preserved P07 buildMagnet tests and added G1-G14 parse tests)

Verification: checkAll ✔/✘ · diff stat:
- checkAll: ✔ (BUILD SUCCESSFUL in 2s, 57 actionable tasks up-to-date/executed, 0 failures, 0 lint/detekt warnings)
- diff stat (`git diff --stat main...HEAD`):
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
 .../nexus/stream/domain/usecase/MagnetLinkTest.kt  | 121 +++++++++
 gradle/libs.versions.toml                          |   3 +
 settings.gradle.kts                                |   4 +
 14 files changed, 1176 insertions(+), 28 deletions(-)
```

Open questions: None.
