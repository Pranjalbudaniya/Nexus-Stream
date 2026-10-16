## Report: P06
Status: done
Access proof (raw grep output):
```
291:### 6.6 Detail ✅
418:### 8.1 Room
549:## 11. Continue Watching, Library, History
```
Carry-over (branch, checkAll):
- Branch `p06-detail` branched from `main` (at `6c9771b`).
- Pre-task `./gradlew checkAll`: PASSED.
- Post-task `./gradlew checkAll`: PASSED.

Files created / modified:
- `app/src/main/java/com/nexus/stream/data/remote/addon/AddonRemoteDataSource.kt` (modified: added `FetchedMeta`, `metaWithRaw`)
- `app/src/main/java/com/nexus/stream/data/remote/addon/AddonMappers.kt` (modified: added `parseMeta(rawJson)`)
- `app/src/main/java/com/nexus/stream/data/local/db/dao/MetaCacheDao.kt` (created: `get`, `@Upsert upsert`, `trimTo`)
- `app/src/main/java/com/nexus/stream/data/local/db/NexusDatabase.kt` (modified: added `metaCacheDao()`)
- `app/src/main/java/com/nexus/stream/di/DatabaseModule.kt` (modified: added `provideMetaCacheDao`)
- `app/src/main/java/com/nexus/stream/data/local/db/dao/ProgressDao.kt` (modified: added `observeByMeta`, `deleteByKey`)
- `app/src/main/java/com/nexus/stream/domain/model/EpisodeProgress.kt` (created: `EpisodeProgress`, `EpisodeRef`)
- `app/src/main/java/com/nexus/stream/domain/model/SeasonGroup.kt` (created: `SeasonGroup`)
- `app/src/main/java/com/nexus/stream/domain/model/MetaExtensions.kt` (created: `MetaDetail.toPreview()`)
- `app/src/main/java/com/nexus/stream/domain/repository/MetaRepository.kt` (created: `MetaSource`, `MetaResult`, `MetaRepository`)
- `app/src/main/java/com/nexus/stream/data/repository/MetaRepositoryImpl.kt` (created: multi-addon fallback, cache TTL, trim, error propagation)
- `app/src/main/java/com/nexus/stream/di/RepositoryModule.kt` (modified: bound `MetaRepository`)
- `app/src/main/java/com/nexus/stream/domain/repository/ProgressRepository.kt` (modified: added `observeEpisodeProgress`, `setWatched`)
- `app/src/main/java/com/nexus/stream/data/repository/ProgressRepositoryImpl.kt` (modified: implemented `observeEpisodeProgress`, `setWatched`)
- `app/src/main/java/com/nexus/stream/domain/usecase/EpisodeLogic.kt` (created: `groupEpisodes`, `defaultSeason`, `resumeVideoId`, `isReleased`)
- `app/src/main/java/com/nexus/stream/util/YoutubeLink.kt` (created: `youtubeWatchUrlOrNull`)
- `app/src/main/java/com/nexus/stream/presentation/feature/detail/DetailHeader.kt` (created: backdrop, logo/title, info line, action FlowRow, synopsis)
- `app/src/main/java/com/nexus/stream/presentation/feature/detail/StreamsSheetPlaceholder.kt` (created: ModalBottomSheet placeholder for P07)
- `app/src/main/java/com/nexus/stream/presentation/feature/detail/EpisodeList.kt` (created: season chips, episode items, progress bar, watched check, long press menu)
- `app/src/main/java/com/nexus/stream/presentation/feature/detail/DetailViewModel.kt` (created: uiState, effects, onEvent)
- `app/src/main/java/com/nexus/stream/presentation/feature/detail/DetailScreen.kt` (modified: full screen layout replacing placeholder, insets, error & stale handling)
- `app/src/main/java/com/nexus/stream/presentation/navigation/NexusNavHost.kt` (modified: wired DetailScreen with navigation)
- `app/src/main/res/values/strings.xml` (modified: added detail strings)
- `app/src/test/java/com/nexus/stream/testutil/FakeMetaCacheDao.kt` (created)
- `app/src/test/java/com/nexus/stream/data/local/db/dao/FakeProgressDao.kt` (modified: added `observeByMeta`, `deleteByKey`)
- `app/src/test/java/com/nexus/stream/util/YoutubeLinkTest.kt` (created: regex and validation tests)
- `app/src/test/java/com/nexus/stream/domain/usecase/EpisodeLogicTest.kt` (created: grouping, default season, resume video, isReleased tests)
- `app/src/test/java/com/nexus/stream/data/repository/MetaRepositoryImplTest.kt` (created: cache hit, bypass, expiry, fallback, timeout, stale)
- `app/src/test/java/com/nexus/stream/data/repository/ProgressRepositoryImplTest.kt` (modified: added setWatched and observeEpisodeProgress tests)
- `app/src/test/java/com/nexus/stream/presentation/feature/detail/DetailViewModelTest.kt` (created: state transitions, retry, watchlist, season, trailer, unreleased, watched)

Rules D1-D23: implemented / not implemented (reasons):
- D1: implemented (static skeleton in `surfaceContainerHigh`, no shimmer)
- D2: implemented (instant cache hit, stale banner on refresh failure, error card with retry on cold failure)
- D3: implemented (tried in position order with 10 s timeout per candidate)
- D4: implemented (fallbacks for missing artwork, hidden sections for missing fields, clean bullet separators)
- D5: implemented (logo max 64 dp with fallback to text)
- D6: implemented (overlay back arrow with `statusBarsPadding()`, system back leaves)
- D7: implemented (watchlist toggles and persists in DB, button icon and description update)
- D8: implemented (synopsis collapses to 4 lines, "Read more" only when overflowing, stock `animateContentSize()`)
- D9: implemented (trailer button only with valid YouTube ID regex, external ACTION_VIEW intent with ActivityNotFoundException handling)
- D10: implemented (movie plays defaultVideoId/metaId; series plays resume episode; placeholder sheet opens)
- D11: implemented (numbered seasons ascending, specials last, no-season group, selection survives)
- D12: implemented (thumbnail with fallback, E{n} • title, formatted release date, overview, watched check or progress bar)
- D13: implemented (unreleased episodes dimmed with 0.5f alpha, tapping shows "Not released yet" message)
- D14: implemented (long-press dropdown menu to mark/unmark watched, immediate UI update, library snapshot preserved)
- D15: implemented ("No episode list available." and play disabled for series without episodes)
- D16: implemented (LazyColumn and LazyRow with keyed items)
- D17: implemented (FlowRow for buttons and chips, no clipping or overlap)
- D18: implemented (backdrop starts behind status bar with 0 top padding, bottom content padding uses WindowInsets.navigationBars + spacing.lg once)
- D19: implemented (state kept across configuration change via ViewModel)
- D20: implemented (non-series types display metadata and play button without season UI)
- D21: implemented (zero addon URLs exposed in user strings or errors)
- D22: implemented (stock Compose crossfade and animateContentSize animations only)
- D23: implemented (all navigation routes open DetailScreen with matching type and id)

Schema check (git status of app/schemas):
```
nothing to commit, working tree clean
(app/schemas is byte-identical and completely unmodified)
```

Suppressions used (max 2, with reasons):
- None. (0 suppressions used across all added and modified code).

Simplification pass:
- Extracted `PlayState` and helper calculation functions out of `DetailViewModel` to keep methods per class well under detekt limits.
- Reused `formatErrorMessage` for `AddonErrorText` to avoid JVM array spread operator penalties.
- Reused stock Compose `LinearProgressIndicator` and tokens from `NexusTheme`.

Assumptions made:
- Formatted release date uses system default locale with `FormatStyle.MEDIUM` per specification.
- Trailer external launch safely shows a snackbar if no YouTube app or web browser is available.

Deviations:
- None. All requirements and constraints strictly met.

Verification: checkAll ✔/✘ · release manifest check · new tests list:
- `./gradlew checkAll`: ✔ PASSED (assembleDebug, testDebugUnitTest, detektDebug, detektDebugUnitTest, spotlessCheck, hexGuard).
- Release manifest check: ✔ Verified `DevToolsActivity` is absent from release manifest.
- New tests list:
  1. `com.nexus.stream.util.YoutubeLinkTest`
  2. `com.nexus.stream.domain.usecase.EpisodeLogicTest`
  3. `com.nexus.stream.data.repository.MetaRepositoryImplTest`
  4. `com.nexus.stream.data.repository.ProgressRepositoryImplTest` (extended)
  5. `com.nexus.stream.presentation.feature.detail.DetailViewModelTest`

Public API for later prompts: (MetaRepository, MetaResult, EpisodeLogic, EpisodeProgress, ProgressRepository.setWatched/observeEpisodeProgress, StreamsSheetPlaceholder):
- `MetaRepository`: `suspend fun getMeta(type: String, id: String, forceRefresh: Boolean): Outcome<MetaResult>`
- `MetaResult`: `data class MetaResult(val meta: MetaDetail, val source: MetaSource)`
- `MetaSource`: `enum class MetaSource { FRESH, CACHE, STALE }`
- `EpisodeLogic`: `groupEpisodes(videos)`, `defaultSeason(groups, lastWatchedId)`, `resumeVideoId(videos, progress, now)`, `Video.isReleased(now)`
- `EpisodeProgress`: `data class EpisodeProgress(val videoId: String, val positionMs: Long, val durationMs: Long, val watched: Boolean, val updatedAt: Long)`
- `EpisodeRef`: `data class EpisodeRef(val videoId: String, val type: String, val season: Int? = null, val episode: Int? = null)`
- `ProgressRepository`: `fun observeEpisodeProgress(metaId: String): Flow<Map<String, EpisodeProgress>>`, `suspend fun setWatched(meta: MetaPreview, episode: EpisodeRef, watched: Boolean)`
- `StreamsSheetPlaceholder`: `StreamsSheetPlaceholder(title: String, videoId: String, onDismissRequest: () -> Unit)`

Known limitations:
- Stream fetching and stream selection are deferred to P07 (placeholder bottom sheet displayed for now).

Open questions:
- None.
