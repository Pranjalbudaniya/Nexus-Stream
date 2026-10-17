## Report: P07.2
Status: done
Access proof (raw grep output):
317:### 6.7 Streams bottom sheet ✅
386:### 7.5 Stream fan-out
398:### 7.6 Stream parsing (heuristic; addons format text differently)

Carry-over (branch, checkAll):
- Branch: `p07-2-stream-filters` (branched from `p07-fix1-stream-info` at commit `52001a8`)
- Pre-work checkAll: ✔ PASSED

Files created / modified:
- Created:
  - `app/src/main/java/com/nexus/stream/domain/model/StreamFilters.kt`
  - `app/src/main/java/com/nexus/stream/domain/model/StreamSort.kt`
  - `app/src/main/java/com/nexus/stream/domain/usecase/LanguageCodes.kt`
  - `app/src/main/java/com/nexus/stream/domain/usecase/StreamFiltering.kt`
  - `app/src/main/java/com/nexus/stream/domain/usecase/StreamSorting.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/streams/DropdownOption.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/streams/FilterDropdownChip.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/streams/FilterBar.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/streams/StreamFilterMemory.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/streams/StreamsFilterComputer.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/settings/SettingsDialogs.kt`
  - `app/src/test/java/com/nexus/stream/domain/usecase/LanguageCodesTest.kt`
  - `app/src/test/java/com/nexus/stream/domain/usecase/StreamFiltersTest.kt`
  - `app/src/test/java/com/nexus/stream/domain/usecase/StreamSortTest.kt`
  - `app/src/test/java/com/nexus/stream/presentation/feature/streams/StreamsFilterReducerTest.kt`
  - `app/src/test/java/com/nexus/stream/presentation/feature/streams/StreamsFilterViewModelTest.kt`
- Modified:
  - `app/src/main/java/com/nexus/stream/domain/repository/PlayerPrefsRepository.kt`
  - `app/src/main/java/com/nexus/stream/data/local/prefs/PlayerPrefsRepositoryImpl.kt`
  - `app/src/main/java/com/nexus/stream/domain/usecase/StreamOrdering.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/streams/StreamsFilterReducer.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/streams/StreamsSheet.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/streams/StreamsViewModel.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/streams/StreamDetailsDialog.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/settings/SettingsViewModel.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/settings/SettingsScreen.kt`
  - `app/src/main/res/values/strings.xml`
  - `app/src/test/java/com/nexus/stream/data/local/prefs/PlayerPrefsRepositoryImplTest.kt`
  - `app/src/test/java/com/nexus/stream/presentation/feature/streams/StreamsViewModelTest.kt`
  - `app/src/test/java/com/nexus/stream/presentation/feature/settings/SettingsViewModelTest.kt`
  - `app/src/test/java/com/nexus/stream/domain/usecase/StreamOrderingTest.kt`

Rules V1-V15: implemented / not implemented (reasons):
- V1 (Filter bar): Implemented. Single horizontal scroll row of dropdown chips. Chips hidden when offering fewer than 2 meaningful choices. No duplicate chips. 48dp touch targets with proper shape tokens.
- V2 (Counts): Implemented. Each option's count respects all other active filters computed via `StreamsFilterComputer`. Selecting a filter updates others dynamically.
- V3 (Stale selection): Implemented. Stale selections (e.g. disabled addon or missing language) sanitize to safe defaults without crashing.
- V4 (Language filter): Implemented. Language filter matches ISO codes as well as `multi`/`dualAudio` streams. Streams without language metadata show only when language filter is "Any".
- V5 (Language data): Implemented. Country flag regional indicators and names map to ISO 639-1 codes per spec 4.1. Unknowns ignored. `multi` adds no code.
- V6 (Type filter): Implemented. Filters to Direct or Torrent. Hidden if only one type exists in streams. External links appear only under ALL.
- V7 (Cached): Implemented. Toggle chip visible only when cached streams exist (`debrid?.cached == true`). Filters to cached streams.
- V8 (Sorting): Implemented. BEST, QUALITY, SEEDERS, SIZE are stable. Penalized streams (mismatched episode/season/year, CAM) are sorted last in all modes.
- V9 (Preferences): Implemented. Preferred language and quality boost applied in BEST mode only. "None" and "Auto" have no boost effect. Hide CAM removes CAM streams completely when setting is active.
- V10 (Persistence): Implemented. Sort, preferredLanguage, preferredQuality, and hideCam persist across restarts in `PlayerPrefsRepository`. In-session filters (resolution, language, type, cachedOnly) survive across requests within app process session via `StreamFilterMemory`. Addon filter resets per request. Corrupt stored preferences return safe defaults.
- V11 (Quick play): Implemented. Quick play selects BEST stream using owner's saved preferences and hideCam setting, ignoring on-screen filters and sort. Still skips penalized streams and plays once.
- V12 (Status line): Implemented. Shows "Showing X of Y streams" when any filter is active. Offers "Clear filters" text button which resets filters to defaults while preserving Sort. Shows "No streams match these filters." with "Clear filters" button on zero results.
- V13 (Layout): Implemented. Filter bar scrolls horizontally, menus scale cleanly at larger font/UI settings, sheet jitter guard is preserved.
- V14 (Privacy): Implemented. No stream URLs, torrent info hashes, tokens, or headers are logged or shown.
- V15 (Conventions): Implemented. All user-visible strings in `strings.xml`. Material/theme tokens used throughout. Stock Compose animations only. At most 2 `@Suppress` annotations across new code (0 added). Duplicates audit completed.

Duplicates audit (filter bar, Settings):
- Filter bar: Each chip appears exactly once in the filter bar row. Inside each dropdown menu, each option is listed once with count and selection indicator. "Clear filters" action appears only once in the status row.
- Settings (Player section): Each setting is represented once: Quick play (switch), Preferred language (single-select dialog), Preferred quality (single-select dialog), Hide CAM streams (switch). No duplicated controls, headers, or action buttons.

Suppressions used (max 2, with reasons):
- Existing in repo: 5 (`StreamParsing.kt:21`, `AddonRemoteDataSource.kt:242`, `AddonRepositoryImpl.kt:28`, `WatchActions.kt:31`, `HomeViewModel.kt:108`)
- Added in P07.2: 0 suppressions added. All new code fully complies with Detekt rules.

Simplification pass:
- Extracted dialog composables into `SettingsDialogs.kt` and inlined `PlayerSettingsCards` in `SettingsScreen.kt` to satisfy Detekt function count limit (11 functions per file).
- Separated filtering and sorting algorithms into pure `StreamFiltering.kt` and `StreamSorting.kt` domain use cases.
- Extracted filter count computations into `StreamsFilterComputer.kt` to keep `StreamsFilterReducer.kt` focused and under complexity limits.
- Placed new filter-related ViewModel unit tests into `StreamsFilterViewModelTest.kt` to prevent `StreamsViewModelTest.kt` from exceeding file length thresholds.

Assumptions made:
- ISO 639-1 language code mappings per spec 4.1.
- In-session filter persistence stores `resolution`, `language`, `type`, and `cachedOnly` in `StreamFilterMemory`, while `addonKey` resets per request.

Deviations:
- None.

Verification: checkAll ✔/✘ · release manifest check · URL/log grep · new tests list:
- checkAll: ✔ PASSED (`assembleDebug`, unit tests, `detektDebug`, `detektDebugUnitTest`, `spotlessCheck`, `hexGuard`)
- Release manifest check: ✔ PASSED (`DevToolsActivity` absent from release merged manifest)
- Privacy grep: ✔ PASSED (0 occurrences of `Log.` or `println(` in `app/src/main`)
- New tests list:
  - `LanguageCodesTest` (14 unit tests)
  - `StreamFiltersTest` (10 unit tests)
  - `StreamSortTest` (12 unit tests)
  - `StreamsFilterReducerTest` (11 unit tests)
  - `StreamsFilterViewModelTest` (6 unit tests)
  - `PlayerPrefsRepositoryImplTest` (extended with 5 unit tests for new preferences)
  - `SettingsViewModelTest` (extended with 3 unit tests for player preferences)
  - `StreamOrderingTest` (updated for sort and filter integration)

Public API for later prompts: (StreamFilters, StreamSort, LanguageCodes, StreamFilterMemory, PlayerPrefsRepository additions):
- `StreamSort` (`BEST`, `QUALITY`, `SEEDERS`, `SIZE`)
- `TypeFilter` (`ALL`, `DIRECT`, `TORRENT`)
- `StreamFilters(resolution: Int?, addonKey: String?, language: String?, type: TypeFilter, cachedOnly: Boolean)`
- `LanguageCodes`:
  - `fun languageCodes(languages: StreamLanguages): Set<String>`
  - `fun languageDisplayName(code: String): String`
  - `val SUPPORTED_LANGUAGE_CODES: List<String>`
- `StreamFilterMemory`:
  - `fun rememberFilters(filters: StreamFilters)`
  - `fun currentFilters(): StreamFilters`
  - `fun clear()`
- `PlayerPrefsRepository`:
  - `val sort: Flow<StreamSort>`
  - `val preferredLanguage: Flow<String?>`
  - `val preferredQuality: Flow<Int?>`
  - `val hideCam: Flow<Boolean>`
  - `suspend fun setSort(sort: StreamSort)`
  - `suspend fun setPreferredLanguage(language: String?)`
  - `suspend fun setPreferredQuality(quality: Int?)`
  - `suspend fun setHideCam(hide: Boolean)`

Known limitations:
- Language filtering depends on addons reporting language metadata in titles/descriptions or flags. Streams with unparseable language metadata show only when language filter is "Any".

Open questions:
- None.
