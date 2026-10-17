## Report: P07
Status: done
Access proof (raw grep output):
317:### 6.7 Streams bottom sheet ✅
386:### 7.5 Stream fan-out
398:### 7.6 Stream parsing (heuristic; addons format text differently)
Carry-over (branch, checkAll):
Branch: p07-streams branched from main (commit 7682fbe). checkAll passed cleanly before work started and after each phase.
Files created / modified:
Created:
- app/src/main/java/com/nexus/stream/domain/model/ParsedStream.kt
- app/src/main/java/com/nexus/stream/domain/model/PlaybackCapabilities.kt
- app/src/main/java/com/nexus/stream/domain/usecase/StreamParsing.kt
- app/src/main/java/com/nexus/stream/domain/usecase/StreamOrdering.kt
- app/src/main/java/com/nexus/stream/domain/usecase/MagnetLink.kt
- app/src/main/java/com/nexus/stream/domain/repository/StreamRepository.kt
- app/src/main/java/com/nexus/stream/domain/repository/PlayerPrefsRepository.kt
- app/src/main/java/com/nexus/stream/data/repository/StreamRepositoryImpl.kt
- app/src/main/java/com/nexus/stream/data/local/prefs/PlayerPrefsRepositoryImpl.kt
- app/src/main/java/com/nexus/stream/presentation/feature/streams/ExternalActions.kt
- app/src/main/java/com/nexus/stream/presentation/feature/streams/StreamRow.kt
- app/src/main/java/com/nexus/stream/presentation/feature/streams/StreamsSheet.kt
- app/src/main/java/com/nexus/stream/presentation/feature/streams/StreamsViewModel.kt
- app/src/test/java/com/nexus/stream/domain/usecase/StreamParsingTest.kt
- app/src/test/java/com/nexus/stream/domain/usecase/StreamOrderingTest.kt
- app/src/test/java/com/nexus/stream/domain/usecase/MagnetLinkTest.kt
- app/src/test/java/com/nexus/stream/data/repository/StreamRepositoryTest.kt
- app/src/test/java/com/nexus/stream/data/local/prefs/PlayerPrefsRepositoryImplTest.kt
- app/src/test/java/com/nexus/stream/presentation/feature/streams/StreamsViewModelTest.kt

Modified:
- app/src/main/java/com/nexus/stream/di/RepositoryModule.kt
- app/src/main/java/com/nexus/stream/presentation/common/StreamsSheetHost.kt
- app/src/main/java/com/nexus/stream/presentation/navigation/Routes.kt
- app/src/main/java/com/nexus/stream/presentation/navigation/NexusNavHost.kt
- app/src/main/java/com/nexus/stream/presentation/navigation/NexusApp.kt
- app/src/main/java/com/nexus/stream/presentation/feature/addons/AddonManagerScreen.kt
- app/src/main/java/com/nexus/stream/presentation/feature/settings/SettingsScreen.kt
- app/src/main/java/com/nexus/stream/presentation/feature/settings/SettingsViewModel.kt
- app/src/main/res/values/strings.xml
- app/src/test/java/com/nexus/stream/presentation/feature/settings/SettingsViewModelTest.kt
Rules T1-T20: implemented / not implemented (reasons):
- T1 (No stream addon): implemented; displays empty state with Find streams button routing to Addon Store Streams category.
- T2 (Progressive loading): implemented; progressive flow updates as addons answer, shows searching x of y addons status line, preserves list scroll.
- T3 (Failures): implemented; non-blocking per-addon failure collection; Retry failed retries only failed addons and merges results.
- T4 (Duplicates): implemented; deduplication by torrent hash/fileIdx or direct/external URL with merged addon names.
- T5 (Sorting): implemented; resolution descending, seeders descending, size descending, unknowns last, stable.
- T6 (Filters): implemented; chips for available resolutions with counts; empty state when filter has no match; resets on new request.
- T7 (Unsupported sources): implemented; filters out nzb, rar, zip, ytId, and unknown stream sources.
- T8 (Parsing): implemented; word-boundary parsing for resolution, HDR, codecs, seeders, sizes, and display name fallback.
- T9 (Row layout): implemented; responsive Column layout wrapping at large font sizes; 48dp min touch targets; no secrets displayed.
- T10 (External player): implemented; ExternalActions.openVideo with chooser and MX Player headers extra; ActivityNotFoundException handled; no logging.
- T11 (Torrent rows): implemented; notice effect emitted when torrent engine absent; Copy magnet and Open magnet in menu; hidden on invalid hash.
- T12 (External links): implemented; ACTION_VIEW intent with chooser.
- T13 (Quick play): implemented; plays best playable stream once done; falls back to list if none playable; never triggers twice.
- T14 (Cache): implemented; 2-minute memory cache in StreamRepositoryImpl; bypassed on forceRefresh or onlyAddons; failures/empty results uncommitted.
- T15 (Lifecycle): implemented; cancelling flow/coroutine on dismiss; rotation preserves state; clean replacement on new request.
- T16 (Sheet behavior): implemented; ModalBottomSheet with drag handle; STREAMS_SHEET_GESTURES_ENABLED = true constant fallback; insets handled cleanly.
- T17 (Family mode): implemented; observeActive filters adult addons; StreamOrdering.isFamilySafe filters stream texts with adult keywords.
- T18 (Privacy): implemented; no URLs, tokens, or hashes displayed on screen, in logs, snackbars, or errors.
- T19 (Settings): implemented; Player section in Settings with Quick play switch persisting in DataStore; exactly one row in Player section.
- T20 (Conventions): implemented; all strings in strings.xml, semantic color and shape tokens, stock Material animations, 0 @Suppress added.
Duplicates audit (streams sheet, Settings):
- Streams sheet: Header title appears once; searching status line appears once; resolution filter chips appear once per present resolution; error/empty state actions appear once; each row's action menu options appear once. No duplicated buttons, texts, or rows.
- Settings: Addons row appears once; Family mode card with toggle and description appears once; Player section header and Quick play card with switch and explanation appear once; Appearance placeholder card appears once. Zero duplicated controls or texts.
Sheet API used and jitter fallback:
- API: androidx.compose.material3.ModalBottomSheet with skipPartiallyExpanded = true and BottomSheetDefaults.DragHandle().
- Fallback: const val STREAMS_SHEET_GESTURES_ENABLED = true exposed in StreamsSheet.kt.
Suppressions used (max 2, with reasons):
- 0 suppressions added in P07.
Simplification pass:
- Unified dedupe and sort in StreamOrdering into single-pass keyed aggregation and stable multi-criteria comparator.
- Consolidated StreamsViewModel event handlers into onEvent and handleStreamAction to stay well under Detekt's TooManyFunctions threshold (8 functions vs 11 max).
- Minimal atomic DataStore boolean preference for PlayerPrefsRepository.
Assumptions made:
- 15s per-addon timeout with concurrency limit of 4 (spec owner decision 🟡).
- 2-minute memory cache TTL in StreamRepositoryImpl (spec owner decision 🟡).
- MX Player headers extra array passed to ACTION_VIEW intent as best-effort.
Deviations:
- None.
Verification: checkAll ✔/✘ · release manifest check · URL/log grep · new tests list:
- checkAll: ✔ Passed (assembleDebug, unit tests, detekt, spotless, hexGuard all clean).
- release manifest check: Passed (no DevToolsActivity in release manifest).
- URL/log grep: Passed (0 matches in app/src/main for Log. or println().
- new tests list:
  1. StreamParsingTest: resolutions, HDR, codecs, seeders, sizes, display name fallback.
  2. StreamOrderingTest: dedupe, sort, resolution filtering, bestPlayable, isFamilySafe.
  3. MagnetLinkTest: 40-char hex, 32-char base32, invalid hashes, url encoding.
  4. StreamRepositoryTest: progressive emission, timeout handling, concurrency limit, empty addons, 2-minute cache, cancellation.
  5. PlayerPrefsRepositoryImplTest: default false, persistence, corrupted file fallback.
  6. StreamsViewModelTest: loading states, filter selection/reset, quick play execution, torrent tap notice, menu actions, family mode, dismiss cancellation, retry failed.
  7. SettingsViewModelTest: quickPlay flow reflection and toggle persistence.
Public API for later prompts: (StreamRepository, ParsedStream, StreamOrdering, MagnetLink, PlaybackCapabilities, StreamActions, ExternalActions, PlayerPrefsRepository):
- StreamRepository: fun streams(type: String, videoId: String, forceRefresh: Boolean = false, onlyAddons: Set<String>? = null): Flow<StreamsState>
- ParsedStream: parsed model with StreamKind, resolution, codecs, HDR, sizeBytes, seeders, displayName.
- StreamOrdering: dedupe, sort, filterByResolution, availableResolutions, bestPlayable, isFamilySafe.
- MagnetLink: buildMagnet(infoHash: String, displayName: String?, trackers: List<String>): String?
- PlaybackCapabilities: data class PlaybackCapabilities(val supportsTorrent: Boolean)
- StreamActions: interface StreamActions { fun onPlay(stream: ParsedStream); fun onExternalPlay(stream: ParsedStream) }
- ExternalActions: object ExternalActions { fun openVideo(...); fun openMagnet(...); fun copy(...) }
- PlayerPrefsRepository: interface PlayerPrefsRepository { val quickPlay: Flow<Boolean>; suspend fun setQuickPlay(enabled: Boolean) }
Known limitations:
- Torrent streams cannot play built-in until P08 torrent engine is implemented; currently prompts the user with an explanation and magnet copy/open options.
- Built-in player arrives in P09; external player via Intent chooser is used for video playback.
Open questions:
- None.
