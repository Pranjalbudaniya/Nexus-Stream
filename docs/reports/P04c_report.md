## Report: P04c
Status: done
Access proof (raw grep output):
259:### 6.2 Home ✅ (hero + rows)
413:### 8.1 Room
544:## 11. Continue Watching, Library, History

Carry-over (branch, checkAll):
- Base commit: `7266bf8` (branch `p04b-fix1-insets`)
- Baseline `./gradlew checkAll`: ✔ passed
- Work branch: `p04c-home-hero`

Files created / modified:
Created (main):
- `app/src/main/java/com/nexus/stream/domain/model/ContinueWatchingItem.kt`
- `app/src/main/java/com/nexus/stream/domain/model/ContinueRow.kt`
- `app/src/main/java/com/nexus/stream/domain/model/RowState.kt`
- `app/src/main/java/com/nexus/stream/domain/repository/LibraryRepository.kt`
- `app/src/main/java/com/nexus/stream/domain/repository/ProgressRepository.kt`
- `app/src/main/java/com/nexus/stream/domain/usecase/BuildHero.kt`
- `app/src/main/java/com/nexus/stream/domain/usecase/ContinueWatchingRules.kt`
- `app/src/main/java/com/nexus/stream/data/local/db/dao/LibraryDao.kt`
- `app/src/main/java/com/nexus/stream/data/local/db/dao/ProgressDao.kt`
- `app/src/main/java/com/nexus/stream/data/repository/LibraryRepositoryImpl.kt`
- `app/src/main/java/com/nexus/stream/data/repository/ProgressRepositoryImpl.kt`
- `app/src/main/java/com/nexus/stream/presentation/common/NexusRefreshBox.kt`
- `app/src/main/java/com/nexus/stream/presentation/feature/home/HeroBanner.kt`
- `app/src/main/java/com/nexus/stream/presentation/feature/home/ContinueWatchingRow.kt`

Created (debug):
- `app/src/debug/java/com/nexus/stream/dev/HomeTestDataViewModel.kt`
- `app/src/debug/java/com/nexus/stream/dev/HomeTestDataScreen.kt`

Created (tests & fakes):
- `app/src/test/java/com/nexus/stream/domain/usecase/BuildHeroTest.kt`
- `app/src/test/java/com/nexus/stream/domain/usecase/ContinueWatchingRulesTest.kt`
- `app/src/test/java/com/nexus/stream/presentation/feature/home/HeroPagingTest.kt`
- `app/src/test/java/com/nexus/stream/data/repository/LibraryRepositoryImplTest.kt`
- `app/src/test/java/com/nexus/stream/data/repository/ProgressRepositoryImplTest.kt`
- `app/src/test/java/com/nexus/stream/testutil/FakeLibraryDao.kt`
- `app/src/test/java/com/nexus/stream/testutil/FakeProgressDao.kt`

Modified:
- `AGENTS.md` (appended stock animation policy bullet under Code quality)
- `app/src/main/java/com/nexus/stream/data/local/db/NexusDatabase.kt` (declared libraryDao and progressDao accessors)
- `app/src/main/java/com/nexus/stream/di/DatabaseModule.kt` (provided LibraryDao and ProgressDao)
- `app/src/main/java/com/nexus/stream/di/RepositoryModule.kt` (bound LibraryRepository and ProgressRepository)
- `app/src/main/java/com/nexus/stream/presentation/common/PosterCard.kt` (added progress bar, subtitle, onLongClick, semantics, Coil crossfade)
- `app/src/main/java/com/nexus/stream/presentation/feature/home/HomeScreen.kt` (integrated HeroBanner/skeleton, ContinueWatchingRow, and NexusRefreshBox)
- `app/src/main/java/com/nexus/stream/presentation/feature/home/HomeViewModel.kt` (exposed hero, watchlistIds, continueWatching; handled ToggleWatchlist, RemoveContinue)
- `app/src/main/res/values/strings.xml` (strings for hero banner and continue watching)
- `app/src/debug/res/values/strings.xml` (strings for home test data debug tool)
- `app/src/debug/java/com/nexus/stream/dev/DevToolsActivity.kt` (wired Home test data tool)
- `app/src/test/java/com/nexus/stream/presentation/feature/home/HomeViewModelTest.kt` (extended with hero and watchlist tests)

Rules C1-C20: implemented / not implemented (reasons):
- C1 (Hero qualification): implemented. `BuildHero` inspects rows in order; returns up to 5 items with backgrounds from the first `Loaded` row having >= 3 background items. Empty list if none qualify.
- C2 (Loading): implemented. `HeroSkeleton` static placeholder renders while the first row is loading.
- C3 (Paging): implemented. `HorizontalPager` with 4:5 aspect ratio (`HERO_ASPECT = 0.8f`), dot indicators at bottom, page survives rotation.
- C4 (Images): implemented. Backdrop failure or missing backdrop renders a `surfaceContainerHigh` box with title. Missing logo falls back to headline title text.
- C5 (Info line): implemented. `formatHeroInfo` joins release year, rating with star, and genres using " • " without stray separators.
- C6 (Watchlist button): implemented. Backed by `LibraryRepository` / `LibraryDao` upserting to `library_items`. State toggles between "+ Watchlist" and "In watchlist" with updated accessibility content descriptions.
- C7 (Rapid taps): implemented. Repository transactions serialize writes; toggle logic flips `inWatchlist` and refreshes metadata snapshot.
- C8 (Continue Watching rules): implemented in pure `ContinueWatchingRules`. Unwatched movies > 60s shown; watched movies hidden; series latest unwatched episode shown with "S{season} E{episode}"; items without snapshot name hidden; newest first by `updatedAt DESC`, capped at 20.
- C9 (Progress bar): implemented. `ContinueWatchingItem.progress` clamps 0..1, null for duration <= 0. Thin 4.dp progress bar rendered over poster bottom.
- C10 (Removal): implemented. Long press shows `DropdownMenu` with "Remove from Continue watching" and TalkBack custom action. Deleting calls `removeFromContinueWatching(metaId)`, removing all progress rows for that title; card animates out via `Modifier.animateItem()`, and row hides when empty.
- C11 (Refresh): implemented. Pull-to-refresh re-fetches rows and updates hero source row; continue watching observes Room live via Flow.
- C12 (Insets): implemented. Hero is full-bleed horizontally below the solid top bar without introducing extraneous inset padding; content scrolls properly above bottom navigation bar.
- C13 (AMOLED and themes): implemented. Hero scrim vertical gradient fades directly into `MaterialTheme.colorScheme.background` without banding.
- C14 (Large font and UI size): implemented. Hero texts wrap/ellipsize, action buttons wrap in `FlowRow` without overlapping.
- C15 (Animation policy): implemented. Uses only stock Compose/Material/Coil animations (`animateItem()`, `animateScrollToPage`, crossfade) with default specs. No custom animation frameworks or motion tokens.
- C16 (Navigation): implemented. Hero page, Details button, and Continue watching card tap open `Detail(type, metaId)`.
- C17 (Hero auto-advance): implemented. Advances every 6s using `pagerState.animateScrollToPage` with wrapping via `nextHeroPage`. Pauses while scrolling or touched. Pauses in background (`repeatOnLifecycle(Lifecycle.State.STARTED)`). Skips auto-advance when system `ANIMATOR_DURATION_SCALE == 0f`. Clamps page if item list shrinks.
- C18 (Pull to refresh): implemented. Reusable `NexusRefreshBox` translates content down using `translationY = state.distanceFraction.coerceAtLeast(0f) * PositionalThreshold.toPx()` in `graphicsLayer`. Indicator shows at top gap; content returns smoothly upon refresh finish.
- C19 (Pull-to-refresh edges): implemented. Top app bar stays outside the box and never moves; box uses `clipToBounds()` so content never bleeds outside bounds; vertical pull doesn't conflict with horizontal pager.
- C20 (Reuse): implemented. Reusable `NexusRefreshBox` in `presentation/common/`.

Schema check (git status of app/schemas):
Clean. No schema changes were made; `app/schemas` remains byte-identical.

Suppressions used (max 2, with reasons):
0 new suppressions added in P04c. Only 1 existing suppression in codebase (`@Suppress("TooGenericExceptionCaught")` in `AddonRemoteDataSource.kt`).

Simplification pass:
- Reused `PosterCard` for continue watching by adding optional `progress` and `subtitle` parameters.
- Reused `MetaPreview` for continue watching card by mapping from `ContinueWatchingItem`.
- Pure use cases `BuildHero` and `ContinueWatchingRules` isolated from Android/Compose dependencies.
- Extracted helper `insertCinemetaItems` to keep `HomeTestDataViewModel` methods concise and compliant with detekt's `LongMethod`.
- Avoided reflection / spread operators in `HomeTestDataScreen`.

Assumptions made:
- 100-minute default duration for test movies in debug tool.
- 50-minute default duration for test series in debug tool.

Deviations:
None.

Verification: checkAll ✔/✘ · release manifest check · new tests list:
- `./gradlew checkAll`: ✔ passed (assembleDebug, unit tests, detektDebug, detektDebugUnitTest, spotlessCheck, hexGuard).
- Release manifest check: ✔ passed (`DevToolsActivity` absent from release merged manifest).
- New & extended tests:
  - `BuildHeroTest` (7 tests)
  - `ContinueWatchingRulesTest` (10 tests)
  - `HeroPagingTest` (4 tests)
  - `LibraryRepositoryImplTest` (5 tests)
  - `ProgressRepositoryImplTest` (3 tests)
  - `HomeViewModelTest` (extended with 4 tests for hero and watchlist)
  Total: 33 new/extended unit tests, all passing.

Public API for later prompts: (LibraryRepository, ProgressRepository, ProgressDao.upsert, LibraryDao.upsert, BuildHero, ContinueWatchingRules, PosterCard progress/subtitle):
- `LibraryRepository`: `observeWatchlistIds(): Flow<Set<String>>`, `toggleWatchlist(item: MetaPreview)`
- `ProgressRepository`: `observeContinueWatching(): Flow<List<ContinueWatchingItem>>`, `removeFromContinueWatching(metaId: String)`
- `ProgressDao`: `observeContinueRows(): Flow<List<ContinueRow>>`, `upsert(entity: WatchProgressEntity)`, `deleteByMeta(metaId: String)`
- `LibraryDao`: `observeWatchlistIds(): Flow<List<String>>`, `get(metaId: String): LibraryItemEntity?`, `upsert(entity: LibraryItemEntity)`
- `BuildHero`: `operator fun invoke(rows: List<RowState>): List<MetaPreview>`
- `ContinueWatchingRules`: `operator fun invoke(rows: List<ContinueRow>): List<ContinueWatchingItem>`
- `PosterCard`: optional `progress: Float?`, `subtitle: String?`, `onLongClick: (() -> Unit)?`, `onLongClickLabel: String?`
- `NexusRefreshBox`: `(isRefreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier, content: @Composable BoxScope.() -> Unit)`

Known limitations:
- Progress writing will be implemented by the player in P10.
- Next-episode suggestions will be introduced in P10.
- Library screen will be implemented in P12.

Open questions:
None.
