## Report: P07.fix1
Status: done
Access proof (raw grep output):
```
$ git grep -n "streamRepository\.streams" app/src/
app/src/test/java/com/nexus/stream/presentation/feature/streams/StreamsViewModelTest.kt:175:            every { streamRepository.streams(sampleRequest.type, sampleRequest.videoId, false, null) } returns
app/src/test/java/com/nexus/stream/presentation/feature/streams/StreamsViewModelTest.kt:224:            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow
app/src/test/java/com/nexus/stream/presentation/feature/streams/StreamsViewModelTest.kt:258:            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow
app/src/test/java/com/nexus/stream/presentation/feature/streams/StreamsViewModelTest.kt:288:            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow
app/src/test/java/com/nexus/stream/presentation/feature/streams/StreamsViewModelTest.kt:310:            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow
app/src/test/java/com/nexus/stream/presentation/feature/streams/StreamsViewModelTest.kt:339:            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow
app/src/test/java/com/nexus/stream/presentation/feature/streams/StreamsViewModelTest.kt:422:            every { streamRepository.streams(seriesRequest.type, seriesRequest.videoId, false, null) } returns
app/src/test/java/com/nexus/stream/presentation/feature/streams/StreamsViewModelTest.kt:444:            every { streamRepository.streams(sampleRequest.type, sampleRequest.videoId, false, null) } returns
app/src/test/java/com/nexus/stream/presentation/feature/streams/StreamsViewModelTest.kt:481:            every { streamRepository.streams(sampleRequest.type, sampleRequest.videoId, false, null) } returns
app/src/test/java/com/nexus/stream/presentation/feature/streams/StreamsViewModelTest.kt:588:            every { streamRepository.streams(sampleRequest.type, sampleRequest.videoId, false, null) } returns
app/src/test/java/com/nexus/stream/presentation/feature/streams/StreamsViewModelTest.kt:591:                streamRepository.streams(
app/src/test/java/com/nexus/stream/presentation/feature/streams/StreamsViewModelTest.kt:627:            every { streamRepository.streams(sampleRequest.type, sampleRequest.videoId, false, null) } returns
app/src/test/java/com/nexus/stream/presentation/feature/streams/StreamsViewModelTest.kt:655:            every { streamRepository.streams(sampleRequest.type, sampleRequest.videoId, false, null) } returnsMany
```
Carry-over (branch, checkAll):
- Branch: `p07-fix1-stream-info` branched off `p07-streams`.
- Base `checkAll` on `p07-streams` was clean and green.

Root cause of the stale results and the fix:
- Root cause:
  1. `StreamsViewModel.handleSetRequest` had a guard `if (_uiState.value.request == request) return`, which prevented reopening the sheet for the same request from restarting the collection flow or checking for changed active addons.
  2. `StreamRepositoryImpl` cached streams using a coarse cache key `(type, videoId)`. Cached streams were aggregated across all addons at the time of the previous fetch. If an addon was disabled or added in the meantime, the cache served the old combined list regardless of which addons were now active.
- Fix:
  1. `StreamsViewModel.handleSetRequest` now cancels existing collection and addon jobs, resets uiState, and starts a fresh flow on every sheet opening.
  2. `StreamRepositoryImpl` now maintains a per-addon cache keyed by `AddonCacheKey(transportUrl, type, videoId)` with a 2-minute TTL. On collection, active addons are resolved first; only active addons with unexpired cache entries are served from cache, and inactive/disabled addons are never included. Network errors are never cached. Disabling an addon immediately excludes its entries from subsequent emissions.

Files created / modified:
- Created:
  - `app/src/main/java/com/nexus/stream/domain/model/AudioTag.kt`
  - `app/src/main/java/com/nexus/stream/domain/model/ReleaseType.kt`
  - `app/src/main/java/com/nexus/stream/domain/model/DebridMark.kt`
  - `app/src/main/java/com/nexus/stream/domain/model/StreamLanguages.kt`
  - `app/src/main/java/com/nexus/stream/domain/model/StreamMatch.kt`
  - `app/src/main/java/com/nexus/stream/domain/usecase/StreamMatching.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/streams/StreamDetailsDialog.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/streams/StreamSkeletonRow.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/streams/StreamsFilterReducer.kt`
  - `app/src/test/java/com/nexus/stream/domain/usecase/StreamMatchingTest.kt`
- Modified:
  - `app/src/main/java/com/nexus/stream/domain/model/ParsedStream.kt`
  - `app/src/main/java/com/nexus/stream/domain/usecase/StreamParsing.kt`
  - `app/src/main/java/com/nexus/stream/domain/usecase/StreamOrdering.kt`
  - `app/src/main/java/com/nexus/stream/data/repository/StreamRepositoryImpl.kt`
  - `app/src/main/java/com/nexus/stream/presentation/common/PlaybackRequest.kt`
  - `app/src/main/java/com/nexus/stream/presentation/common/PosterActionsHost.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/detail/DetailScreen.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/streams/StreamsSheetHost.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/streams/StreamsSheet.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/streams/StreamRow.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/streams/StreamsViewModel.kt`
  - `app/src/main/res/values/strings.xml`
  - `app/src/test/java/com/nexus/stream/data/repository/StreamRepositoryTest.kt`
  - `app/src/test/java/com/nexus/stream/domain/usecase/StreamParsingTest.kt`
  - `app/src/test/java/com/nexus/stream/domain/usecase/StreamOrderingTest.kt`
  - `app/src/test/java/com/nexus/stream/presentation/feature/streams/StreamsViewModelTest.kt`

Rules U1-U15: implemented / not implemented (reasons):
- U1 (Addon turned off): implemented. `StreamRepositoryImpl` filters active addons per collection; `StreamsViewModel` clears streams and restarts flow on `SetRequest`; disabling addon immediately excludes its rows.
- U2 (Cache): implemented. Per-addon cache keyed by `(transportUrl, type, videoId)` with 2-minute TTL; failures never cached; bypass via `forceRefresh`.
- U3 (Card info): implemented. Stremio layout: addon name & quality line, full file name (up to 3 lines), seeders/size/source site line, flags and language text, chip badges row, footer badges row.
- U4 (Unknown fields): implemented. Missing info parts omit separator and leave no empty lines or placeholders.
- U5 (Torrentio sample): implemented. Section 2 fixture parsed: file name, 527 seeders, 359.77 MB, NyaaSi, flag 🇬🇧, Dubbed, Dual audio.
- U6 (Languages): implemented. Unicode flag surrogate pairs extracted via JVM standard library `codePointAt` (`0x1F1E6..0x1F1FF`), deduped, names shown as text.
- U7 (Wrong file): implemented. Different episode/season/year shows warning badge (`errorContainer`/`onErrorContainer`), sorted last via penalty sort, skipped by Quick Play unless nothing else playable.
- U8 (Season packs): implemented. Detected via season markers without episode, `isPack` badge displayed, pack with matching season is marked `StreamMatch.OK`.
- U9 (Addon filter): implemented. Hidden with < 2 active stream addons, shown with >= 2 active stream addons with counts, duplicate names disambiguated (`Name`, `Name (2)`), resets when selected addon disappears.
- U10 (Filter counts): implemented. Cross-filtering computed: resolution counts reflect selected addon, addon counts reflect selected resolution; empty filter shows "No streams match these filters" with "Clear filters" button.
- U11 (Details dialog): implemented. `StreamDetailsDialog` shows all parsed fields, match explanation, special headers explanation without secrets, selectable raw `infoLines`, Close button only.
- U12 (Privacy): implemented. No URL, token, hash, or header value displayed on screen, in logs, snackbars, errors, or details dialog.
- U13 (Layout): implemented. Text and chip badges wrap gracefully with `FlowRow`, min 48 dp tap targets, no duplicate text or badges.
- U14 (Sheet): implemented. Jitter guard (250ms) and fallback constant preserved, single scrollable child.
- U15 (Conventions): implemented. All user strings in `strings.xml`, no hardcoded hex/colors outside theme tokens, stock animations, `Nexus*` wrappers and shape tokens, only 1 `@Suppress("TooManyFunctions")` on `StreamParsing`, duplicates audit verified.

Duplicates audit (streams sheet):
- Header line: Addon name and extra title details without duplicate resolution text (`resolveHeaderTitle` strips duplicate resolution strings matching regex).
- File name line: Clean raw file name or filename from behaviorHints.
- Stats line: Seeders, formatted file size, and source site formatted with `·` without repeating addon or resolution.
- Languages line: Unicode emoji flags and language descriptor names (English, Dubbed, Dual audio, Multi) appear once, deduped.
- Quality & format badges: Resolution badge (`4K`, `1080p`, etc.) appears once in the badges row. Release type (`WEB-DL`, `BLURAY`, etc.), Codec (`HEVC`, `AVC`, `AV1`), HDR (`HDR`, `HDR10`, `HDR10+`, `DV`), Audio (`Atmos 7.1`, etc.), Debrid (`Cached RD`), and Pack (`Pack`) appear only once.
- Footer badges: `via Addon (+N)` appears once in primary color. Stream type badge (`Direct`, `Torrent`, `External Link`), warning badges (`Different episode?`, `Different season?`, `Different year?`, `Low quality (CAM)`), and `Needs special headers` badge appear only once.
- Filter chips: Resolution chip and Addon chip rows have unique chips with counts.
- Overflow menu: Action items appear once (Play, Details, Copy link / Copy magnet / Open magnet, Copy file name).

Suppressions used (max 2, with reasons):
- Total suppressions: 1 (limit: max 2).
- `app/src/main/java/com/nexus/stream/domain/usecase/StreamParsing.kt`: `@Suppress("TooManyFunctions")` (16 public parser functions kept together for cohesive parsing of resolution, site, flags, languages, audio, channels, release types, debrid, pack, and fixtures).
- No other `@Suppress` annotations added anywhere in feature or test code.

Simplification pass:
- `StreamsViewModel` member functions reduced from 18 to 10 by extracting pure state transformations into `StreamsFilterReducer` and grouping stream action events.
- `StreamRow` functions reduced from 13 to 11 by moving `StreamSkeletonRow` to `StreamSkeletonRow.kt`, inlining single-call helpers `formatFileSize` and `buildAddonLabel`, and extracting badge collection to `collectBadges`.
- `collectBadges` cyclomatic complexity reduced from 17 to 12 by using `codec.name` directly.
- Emoji flag parsing implemented via standard Java/JVM unicode codepoint scanning (`Character.codePointAt` in `0x1F1E6..0x1F1FF`) with zero regex fragility or third-party libraries.
- Removed unused imports and unneeded constants.

Assumptions made:
- When multiple stream-capable addons have identical manifest names (e.g., multiple local or community Torrentio instances with different configs), disambiguation preserves addon order by position/list index (`Name`, `Name (2)`).
- Cross-filtering displays total counts for "All resolutions" and "All addons" reflecting the other active filter.

Deviations:
- None.

Verification: checkAll ✔/✘ · release manifest check · URL/log grep · new tests list:
- `./gradlew checkAll`: ✔ PASS (all 57 tasks: compileDebugKotlin, testDebugUnitTest, assembleDebug, detektDebug, detektDebugUnitTest, spotlessCheck, hexGuard).
- Release manifest check: Verified zero occurrences of `DevToolsActivity` in release manifests.
- URL/log grep: `git grep -n "Log\.\|println(" app/src/main` returned no occurrences.
- New/extended tests list:
  - `StreamParsingTest`:
    - `parse_torrentioLikeMovieSample_extractsAllFields`
    - `parse_torrentioSeriesSample_extractsSeasonEpisodeAndSite`
    - `parse_mediaFusionAndCometSamples_extractsDebridAndHeaders`
    - `parseEmojiFlags_extractsAndDedupes`
    - `parseAudioAndChannels_extractsTagsAndChannels`
    - `parseReleaseType_detectsRemuxAndWebDl`
    - `parseDebrid_detectsCachedStatus`
    - `parseIsPack_detectsSeasonPacks`
  - `StreamMatchingTest`:
    - `matchOf_seriesMatching_detectsOkWrongEpisodeWrongSeasonAndPacks`
    - `matchOf_movieMatching_detectsExactWithinOneYearAndMismatch`
    - `matchOf_missingOrUnparseable_returnsUnknown`
  - `StreamOrderingTest`:
    - `sort_penalizesMismatchAndCamBelowCleanStreams`
    - `bestPlayable_skipsPenalizedStreams`
    - `crossFilterCounts_computesCountsWithOtherFilterApplied`
    - `filterByAddon_keepsOnlyStreamsMatchingAddonKey`
  - `StreamRepositoryTest`:
    - `perAddonCache_reusesResultsWithinTTL`
    - `perAddonCache_disablingAddonImmediatelyRemovesItsStreams`
    - `perAddonCache_enablingAddonQueriesAndIncludesIt`
    - `perAddonCache_differentVideoIdDoesNotShareCache`
    - `perAddonCache_failuresAreNotCached`
  - `StreamsViewModelTest`:
    - `addonFilter_hiddenWithOneAddon_shownWithTwoAddons`
    - `duplicateAddonNames_receiveSuffixes`
    - `addonFilter_resetsWhenAddonDisappears`
    - `clearFilters_resetsBothResolutionAndAddonFilters`
    - `showDetails_populatesDetailsStream_andDismissClearsIt`
    - `copyFileName_emitsCopyEffectAndSnackbar`
    - `playbackRequest_paramsReachMatcher_andPenalizesMismatch`

Public API for later prompts: (ParsedStream fields, StreamLanguages, StreamMatching, per-addon cache, PlaybackRequest season/episode/year):
- `ParsedStream`: `addonKeys: List<String>`, `fileName: String?`, `sourceSite: String?`, `languages: StreamLanguages`, `audio: Set<AudioTag>`, `channels: String?`, `releaseType: ReleaseType?`, `debrid: DebridMark?`, `isPack: Boolean`, `infoLines: List<String>`, `match: StreamMatch`.
- `StreamLanguages`: `StreamLanguages(flags: List<String>, names: List<String>, dubbed: Boolean, dualAudio: Boolean, multi: Boolean)`.
- `AudioTag`: enum `[ATMOS, DDP, DD, DTS_HD, DTS, AAC, AC3, TRUEHD, OPUS, FLAC]`.
- `ReleaseType`: enum `[REMUX, BLURAY, WEBDL, WEBRIP, HDTV, DVDRIP, CAM]`.
- `DebridMark`: `DebridMark(cached: Boolean, service: String)`.
- `StreamMatch`: enum `[OK, WRONG_SEASON, WRONG_EPISODE, WRONG_YEAR, UNKNOWN]`.
- `StreamMatching`: `fun matchOf(fileName: String?, season: Int?, episode: Int?, year: Int?): StreamMatch`.
- `PlaybackRequest`: includes `season: Int? = null`, `episode: Int? = null`, `year: Int? = null`.
- `StreamRepositoryImpl`: per-addon cache keyed by `AddonCacheKey(transportUrl, type, videoId)`.

Known limitations:
- Streams without parseable season/episode (e.g. daily dates or unconventional absolute anime numbering without "E\d") fall back to `StreamMatch.UNKNOWN` and sort by resolution/seeders/size without mismatch penalty.

Open questions:
- None.
