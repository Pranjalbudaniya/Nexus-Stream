## Report: P06.fix2
Status: done

### Access proof (raw grep output):
```
269:### 6.2 Home ✅ (hero + rows)
296:### 6.6 Detail ✅
423:### 8.1 Room
```

### Carry-over (branch, checkAll):
- Starting branch: `p06-fix2-library` branched from `p06-fix1-detail-polish` (`8993dfe`)
- Initial `./gradlew checkAll`: Passed (all unit tests, assembleDebug, detekt, spotless, hexGuard clean)
- Final `./gradlew checkAll`: Passed (280 unit tests, assembleDebug, detekt, spotless, hexGuard clean)

### Part 0 facts (DB version history, grep results, query/table check, scenario traces, findings and fixes):
1. **Room Version & Schemas:**
   - Current database version: Bumped from 1 to 2 in Part A.
   - Output of `ls app/schemas/*/`:
     ```
     app/schemas/com.nexus.stream.data.local.db.NexusDatabase/1.json
     app/schemas/com.nexus.stream.data.local.db.NexusDatabase/2.json
     ```
   - Output of `git log --oneline -- app/schemas`:
     ```
     87396b2 feat(data,domain): add ListStatus, migration 1-2, poster marks, and Family mode logic
     aa7cbaa feat(db): setup Room v1 schema with 6 entities, AddonDao, and DatabaseModule
     ```
   - Schema version history explanation: The P06.fix1 report mentioned schema version 2 in text due to tentative inspection during previous development, but the actual exported Room schema tracked in git prior to P06.fix2 was strictly `version = 1` (`1.json`). P06.fix2 formally defined `MIGRATION_1_2`, bumped `NexusDatabase.version` to 2, and generated the committed `2.json` schema.

2. **Destructive Fallback Grep Check:**
   - Output of `grep -rn "fallbackToDestructiveMigration\|inMemoryDatabaseBuilder\|allowMainThreadQueries" app/src/main`:
     ```
     (No matches found)
     ```
     Confirmed: Destructive fallback is absent and disabled in production code.

3. **Query vs Entity Table Name Check:**
   - `AddonDao`: Queries reference `addons` -> Matches `AddonEntity` (`tableName = "addons"`)
   - `LibraryDao`: Queries reference `library_items` -> Matches `LibraryItemEntity` (`tableName = "library_items"`)
   - `MetaCacheDao`: Queries reference `meta_cache` -> Matches `MetaCacheEntity` (`tableName = "meta_cache"`)
   - `ProgressDao`: Queries reference `watch_progress` and `library_items` -> Matches `WatchProgressEntity` (`tableName = "watch_progress"`) and `LibraryItemEntity` (`tableName = "library_items"`)

4. **Scenario Traces:**
   - **(i) Detail, mark movie watched:**
     - User action: Tapping "Mark watched" in `WatchOptionsSheet` calls `DetailViewModel.markWatched()`.
     - Written rows:
       1. `watch_progress`: Upserts `WatchProgressEntity` with `watched = 1`, `positionMs = durationMs`, and updated timestamp.
       2. `library_items`: `LibraryRepository.ensureSnapshot()` writes/updates metadata row for poster and name caching.
     - Flow updates:
       1. `ProgressDao.observeContinueRows()` updates the continue watching row (removes completed movie).
       2. `ProgressDao.observeWatchedMovieIds()` emits the movie's ID.
       3. `HomeViewModel.posterMarks` recalculates and emits `PosterMark.WATCHED` for this ID.
       4. `HomeScreen` receives updated `posterMarks` and `PosterCard` renders the check mark badge in the top-right corner.
   - **(ii) Detail, Watchlist toggle & status selection:**
     - User action: Selecting a status (e.g. Watching or Completed) in `ListStatusDialog` calls `DetailViewModel.setListStatus(status)`.
     - Written rows:
       1. `library_items`: `LibraryRepository.setStatus(item, status)` upserts row with `inWatchlist = 1`, `listStatus = status.name`, and updated timestamp.
     - Flow updates:
       1. `LibraryDao.observeListStatuses()` emits updated status map.
       2. `DetailHeader` watchlist button label updates from "Watchlist" to the status name with a check icon.
       3. `HomeViewModel.posterMarks` recalculates badge: `PosterMark.WATCHING` shows a play badge, `PosterMark.WATCHED` shows a check badge.
       4. Hero banner updates its plus/tick button state.
   - **(iii) Force-close survival:**
     - All rows are persisted on disk in SQLite (`nexus_stream.db`). On cold app start, Room queries read directly from disk and re-emit active states to all Flows.
     - The debug `DatabaseDevScreen` inspects row counts and the 20 newest rows of all tables, confirming persistence after kill and relaunch.

5. **Defect Found & Repaired:**
   - `clearInProgress` in P06.fix1 was setting `positionMs = 0`, `durationMs = 0`, and `watched = 0` instead of deleting unwatched records. As a result, the zeroed row remained in `watch_progress` and could remain in Continue Watching as the newest episode.
   - Fix: Updated query to `DELETE FROM watch_progress WHERE metaId = :metaId AND watched = 0`. Tested with series scenario where in-progress episode is deleted while previously watched episodes are preserved.

### Files created / modified:
- **Created:**
  - `app/schemas/com.nexus.stream.data.local.db.NexusDatabase/2.json`
  - `app/src/debug/java/com/nexus/stream/dev/DatabaseDevScreen.kt`
  - `app/src/debug/java/com/nexus/stream/dev/DatabaseDevViewModel.kt`
  - `app/src/main/java/com/nexus/stream/data/local/db/Migrations.kt`
  - `app/src/main/java/com/nexus/stream/data/local/prefs/ContentPrefsRepositoryImpl.kt`
  - `app/src/main/java/com/nexus/stream/domain/model/ListStatus.kt`
  - `app/src/main/java/com/nexus/stream/domain/model/PosterMark.kt`
  - `app/src/main/java/com/nexus/stream/domain/repository/ContentPrefsRepository.kt`
  - `app/src/main/java/com/nexus/stream/domain/usecase/ContentFilter.kt`
  - `app/src/main/java/com/nexus/stream/domain/usecase/PosterActions.kt`
  - `app/src/main/java/com/nexus/stream/domain/usecase/PosterMarks.kt`
  - `app/src/main/java/com/nexus/stream/presentation/common/ListStatusDialog.kt`
  - `app/src/main/java/com/nexus/stream/presentation/common/PosterActionsHost.kt`
  - `app/src/main/java/com/nexus/stream/presentation/common/StreamsSheetHost.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/settings/SettingsViewModel.kt`
  - `app/src/main/res/drawable/ic_movie.xml`
  - `app/src/test/java/com/nexus/stream/data/local/db/Migration1To2Test.kt`
  - `app/src/test/java/com/nexus/stream/data/local/prefs/ContentPrefsRepositoryImplTest.kt`
  - `app/src/test/java/com/nexus/stream/domain/usecase/ContentFilterTest.kt`
  - `app/src/test/java/com/nexus/stream/domain/usecase/PosterActionsTest.kt`
  - `app/src/test/java/com/nexus/stream/domain/usecase/PosterMarksTest.kt`
  - `app/src/test/java/com/nexus/stream/presentation/common/PosterActionsTest.kt`
  - `app/src/test/java/com/nexus/stream/presentation/feature/settings/SettingsViewModelTest.kt`
- **Modified:**
  - `AGENTS.md` (appended 2 rules)
  - `app/src/debug/java/com/nexus/stream/dev/DevToolsActivity.kt`
  - `app/src/debug/res/values/strings.xml`
  - `app/src/main/java/com/nexus/stream/data/local/db/NexusDatabase.kt`
  - `app/src/main/java/com/nexus/stream/data/local/db/dao/LibraryDao.kt`
  - `app/src/main/java/com/nexus/stream/data/local/db/dao/ProgressDao.kt`
  - `app/src/main/java/com/nexus/stream/data/local/db/entity/LibraryItemEntity.kt`
  - `app/src/main/java/com/nexus/stream/data/repository/AddonRepositoryImpl.kt`
  - `app/src/main/java/com/nexus/stream/data/repository/LibraryRepositoryImpl.kt`
  - `app/src/main/java/com/nexus/stream/data/repository/MetaRepositoryImpl.kt`
  - `app/src/main/java/com/nexus/stream/data/repository/ProgressRepositoryImpl.kt`
  - `app/src/main/java/com/nexus/stream/di/DatabaseModule.kt`
  - `app/src/main/java/com/nexus/stream/di/RepositoryModule.kt`
  - `app/src/main/java/com/nexus/stream/domain/model/Addon.kt`
  - `app/src/main/java/com/nexus/stream/domain/model/AddonManifest.kt`
  - `app/src/main/java/com/nexus/stream/domain/repository/AddonRepository.kt`
  - `app/src/main/java/com/nexus/stream/domain/repository/LibraryRepository.kt`
  - `app/src/main/java/com/nexus/stream/domain/repository/ProgressRepository.kt`
  - `app/src/main/java/com/nexus/stream/presentation/common/ContentRow.kt`
  - `app/src/main/java/com/nexus/stream/presentation/common/PosterCard.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/addons/AddAddonSheet.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/addons/AddonCard.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/addons/AddonManagerScreen.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/addons/AddonManagerViewModel.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/detail/DetailHeader.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/detail/DetailScreen.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/detail/DetailViewModel.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/detail/WatchOptionsSheet.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/home/ContinueWatchingRow.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/home/HomeScreen.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/home/HomeViewModel.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/settings/SettingsScreen.kt`
  - `app/src/main/java/com/nexus/stream/presentation/navigation/NexusApp.kt`
  - `app/src/main/res/values/strings.xml`
  - `app/src/test/java/com/nexus/stream/data/local/db/dao/FakeLibraryDao.kt`
  - `app/src/test/java/com/nexus/stream/data/local/db/dao/FakeProgressDao.kt`
  - `app/src/test/java/com/nexus/stream/data/repository/AddonRepositoryImplTest.kt`
  - `app/src/test/java/com/nexus/stream/data/repository/LibraryRepositoryImplTest.kt`
  - `app/src/test/java/com/nexus/stream/data/repository/MetaRepositoryImplTest.kt`
  - `app/src/test/java/com/nexus/stream/data/repository/ProgressRepositoryImplTest.kt`
  - `app/src/test/java/com/nexus/stream/presentation/feature/addons/AddonManagerViewModelTest.kt`
  - `app/src/test/java/com/nexus/stream/presentation/feature/detail/DetailViewModelTest.kt`
  - `app/src/test/java/com/nexus/stream/presentation/feature/home/HomeViewModelTest.kt`
- **Removed:**
  - `app/src/main/java/com/nexus/stream/presentation/feature/detail/StreamsSheetPlaceholder.kt` (replaced by global `StreamsSheetHost.kt`)

### Rules G1-G23: implemented / not implemented (reasons):
- **G1 (Saved state):** Implemented. Marking watched, list status, and watchlist writes persist to disk and survive force-close; the new `DatabaseDevScreen` inspects row counts and table data.
- **G2 (Poster marks):** Implemented. Watched movie shows check; Completed title shows check; Watching shows play badge; badges update live via `ProgressDao.observeWatchedMovieIds()` and `LibraryDao.observeListStatuses()`.
- **G3 (Badge layout):** Implemented. Badge rendered in top-right corner of poster container without overlapping the bottom title or progress bar; tested for accessibility scaling.
- **G4 (Statuses):** Implemented. Five statuses (Plan to watch, Watching, Completed, On hold, Dropped) plus remove; current selection marked; applies immediately; available from Detail and poster action sheet.
- **G5 (Hero tick):** Implemented. Plus button adds as Plan to watch and turns into tick; tick removes directly if Plan to watch, or prompts confirmation dialog if another status is set; does not trigger Detail navigation.
- **G6 (Detail button):** Implemented. Displays "Watchlist" with Add icon when not in list, and the localized status label with Check icon when listed.
- **G7 (Migration):** Implemented. Version 1 to 2 migration adds `listStatus` column with default `'PLAN_TO_WATCH'`; existing watchlist rows preserved; destructive fallback absent; `2.json` schema committed.
- **G8 (Long press):** Implemented. Normal long-press on `PosterCard` opens `PosterActionsHost` sheet via `LocalPosterActions.current.show()`; standard tap navigates to Detail; disabled on skeletons and hero.
- **G9 (Sheet order):** Implemented. Non-series: Play, Detail, Trailer, Watchlist. Series: Detail, Trailer, Watchlist. From Continue watching: includes Remove at end.
- **G10 (Trailer row):** Implemented. Row keeps its position with Checking, Available, or None states without layout jump; opens external viewer with error handling; no crash if no intent handler.
- **G11 (Direct play):** Implemented. Play action opens global `StreamsSheetHost` via `LocalPlayback` without opening Detail; Detail's play and episode taps share the same global host.
- **G12 (Sheet behavior):** Implemented. `skipPartiallyExpanded = true`, no inner scrolling, smooth dismiss; navigation bar insets applied cleanly.
- **G13 (Family mode default):** Implemented. Defaults to `true` on fresh install and upgrade; persists via Preferences DataStore; turning off prompts confirmation dialog; turning on applies immediately.
- **G14 (Family mode effect):** Implemented. Adult addons excluded from Home rows, meta lookups, and capability banners via `AddonRepository.observeActive()`; matching adult items dropped at row mapping time; empty rows hidden.
- **G15 (Toggling):** Implemented. Toggling Family mode updates Home instantly through reactive StateFlow combination without requiring a network refetch.
- **G16 (Keyword filter):** Implemented. Whole-word conservative regex filter matches adult terms while preserving mainstream titles ("Sex Education", "Hardcore Henry", etc.).
- **G17 (Locked addons):** Implemented. Adult addons display "18+" badge, "Hidden by Family mode" subtitle, and disabled toggle switch; add-by-URL preview displays warning and disables Install.
- **G18 (Continue row):** Implemented. Long-press opens poster sheet with "Remove from Continue watching"; removes unwatched rows via `clearInProgress` while preserving watched rows.
- **G19 (No duplicates):** Implemented. Audited all screens (Home, Detail, Addons, Settings) ensuring every control and piece of information appears only once.
- **G20 (Rapid actions):** Implemented. Rapid taps on hero, sheet, and dialog execute via coroutines and database transactions without race conditions or inconsistent states.
- **G21 (Offline):** Implemented. Poster sheet functions offline: trailer falls back to "No trailer available" if meta cache is absent; Play and Detail actions remain accessible.
- **G22 (Accessibility):** Implemented. Poster cards provide `onLongClickLabel = stringResource(R.string.cd_open_actions)`; sheet rows are at least 48 dp with labels; status dialog uses `selectableGroup`.
- **G23 (Conventions):** Implemented. All user-visible strings in `strings.xml`; no hex colors in UI; stock Compose animations only; `Nexus*` button wrappers and shape tokens used throughout.

### Migration (old version, new version, SQL, schema JSON diff):
- **Old version:** 1
- **New version:** 2
- **SQL statement:**
  ```sql
  ALTER TABLE library_items ADD COLUMN listStatus TEXT NOT NULL DEFAULT 'PLAN_TO_WATCH'
  ```
- **Schema JSON diff (`1.json` vs `2.json`):**
  ```diff
  --- app/schemas/com.nexus.stream.data.local.db.NexusDatabase/1.json
  +++ app/schemas/com.nexus.stream.data.local.db.NexusDatabase/2.json
  @@ -1,8 +1,8 @@
   {
     "formatVersion": 1,
     "database": {
  -    "version": 1,
  -    "identityHash": "6a936834c7441c3cb52e52ab02d84e4f",
  +    "version": 2,
  +    "identityHash": "bb3f482802168f2ae4b8553648a3478b",
       "entities": [
  @@ -153,7 +153,7 @@
         },
         {
           "tableName": "library_items",
  -        "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`metaId` TEXT NOT NULL, `type` TEXT NOT NULL, `name` TEXT NOT NULL, `poster` TEXT, `background` TEXT, `inWatchlist` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`metaId`))",
  +        "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`metaId` TEXT NOT NULL, `type` TEXT NOT NULL, `name` TEXT NOT NULL, `poster` TEXT, `background` TEXT, `inWatchlist` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, `listStatus` TEXT NOT NULL DEFAULT 'PLAN_TO_WATCH', PRIMARY KEY(`metaId`))",
           "fields": [
             {
               "fieldPath": "metaId",
  @@ -194,6 +194,13 @@
               "columnName": "addedAt",
               "affinity": "INTEGER",
               "notNull": true
  +          },
  +          {
  +            "fieldPath": "listStatus",
  +            "columnName": "listStatus",
  +            "affinity": "TEXT",
  +            "notNull": true,
  +            "defaultValue": "'PLAN_TO_WATCH'"
             }
           ],
           "primaryKey": {
  @@ -290,7 +297,7 @@
       ],
       "setupQueries": [
         "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
  -      "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '6a936834c7441c3cb52e52ab02d84e4f')"
  +      "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'bb3f482802168f2ae4b8553648a3478b')"
       ]
     }
   }
  ```

### Duplicates audit (Home, Detail, Addons, Settings):
- **Home:**
  - Hero banner: Contains exactly one Play/Resume button and one Watchlist toggle icon button. Title, genres, and metadata appear once.
  - Continue Watching: Each card displays poster, title, progress bar, and badge once. Long-press triggers the sheet (replacing the previous dropdown menu), eliminating duplicate action buttons.
  - Content Rows: Each card shows its poster and mark badge once. Row header displays title and type once.
- **Detail:**
  - Action row: Contains exactly one Watchlist button, one Watched toggle button, and one Share button.
  - Watch Options Sheet: Tapping Watchlist opens the options sheet whose top row links to `ListStatusDialog`. No duplicate "Add to watchlist" buttons.
  - Episode list: Each episode shows episode number, title, and status icon once; no duplicate play controls.
- **Addons:**
  - AddonCard: Exactly one enable/disable switch, one set of type chips, and one "18+" badge (when adult). No redundant status text.
  - AddAddonSheet: Exactly one URL text input, one preview card, and one Install button.
- **Settings:**
  - Exactly one Family mode switch with dynamic subtitle displaying adult addon count when active.
  - Exactly one Addons navigation row and one disabled Appearance row. No repeated configuration controls.

### AGENTS.md diff:
```diff
diff --git a/AGENTS.md b/AGENTS.md
index d83ceac..61ee2d5 100644
--- a/AGENTS.md
+++ b/AGENTS.md
@@ -60,4 +60,6 @@ Use exactly the report headings given in the prompt. A report in any other forma
 - Before reporting, do a cleanup pass: remove anything unused and list what you simplified.
 - Animations: only stock Compose, Material, Navigation and Coil animations with default specs. No custom animation frameworks, motion tokens, per-frame color interpolation or animation settings.
 - Buttons, chips, FABs, cards and image containers take their shape from NexusTheme.shapes (button, chip, card, poster); use the Nexus* button wrappers, never the Material defaults.
+- Every control and piece of information appears once per screen. No duplicate buttons or repeated text.
+- Content lists use AddonRepository.observeActive and ContentFilter so Family mode applies everywhere.
```

### Suppressions used (max 2, with reasons):
1. `@Suppress("LongParameterList")` on `HomeViewModel`: Requires repositories for addons, catalog, meta, library, watch progress, and content preferences.
2. `@Suppress("LongParameterList")` on `AddonRepositoryImpl`: Pre-existing suppression from initial architecture for constructor dependencies.
*(No new suppressions added in P06.fix2).*

### Simplification pass:
- Extracted and unified `LocalPosterActions` in `PosterActionsHost.kt` so all poster long-press interactions across the application share one sheet implementation and ViewModel.
- Unified `StreamsSheetHost` under `LocalPlayback` in `NexusApp.kt`, completely removing the duplicate `StreamsSheetPlaceholder.kt` from the Detail feature.
- Reused `ListStatusDialog` between the poster action sheet and the detail watch options sheet.
- Inlined redundant single-use `PreviewContent` composable in `AddAddonSheet.kt`.
- Extracted `handleRefresh()` in `AddonManagerViewModel.kt` to keep cyclomatic complexity within detekt thresholds.

### Assumptions made:
- Default status when adding an item to the list without explicitly picking a status is `PLAN_TO_WATCH`.
- Automatic transitions to `WATCHING` (upon playback start) and `COMPLETED` (upon playback finish) are deferred to later player prompts (P10) per specification.
- Family mode keyword filtering is conservative and operates with word boundaries to avoid false positives on legitimate titles.

### Deviations:
None.

### Verification: checkAll ✔/✘ · release manifest check · new tests list:
- **`checkAll`:** ✔ (All 57 Gradle tasks passed up-to-date; assembleDebug, unit tests, detekt, spotless, hexGuard clean)
- **Release manifest check:** Verified `DevToolsActivity` is absent from release merged manifest (`app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`) and present only in debug manifest.
- **New & Updated Tests List (280 total unit tests passing):**
  - `ContentFilterTest` (10 tests: keyword boundaries, adult genres, case-insensitivity, safe title preservation)
  - `PosterMarksTest` (7 tests: watched movie id, COMPLETED, WATCHING, precedence, unlisted statuses)
  - `PosterActionsTest` (5 domain tests: non-series play, series exclusion, trailer states, continue watching remove)
  - `com.nexus.stream.presentation.common.PosterActionsTest` (8 presentation tests: UI row mappings and actions)
  - `Migration1To2Test` (2 tests: SQL migration syntax and schema integrity)
  - `ContentPrefsRepositoryImplTest` (3 tests: default true, toggle persistence, missing key fallback)
  - `LibraryRepositoryImplTest` (updated for statuses: status persistence, unlist behavior, snapshot refresh)
  - `ProgressRepositoryImplTest` (extended: `clearInProgress` deletes only `watched = 0` rows for series)
  - `AddonRepositoryImplTest` (extended: `observeActive` filters adult addons when Family mode is enabled)
  - `HomeViewModelTest` (extended: adult content filtering at row map time, poster marks observation, hero tick/confirm)
  - `DetailViewModelTest` (extended: list status updates, status label bindings)
  - `SettingsViewModelTest` (4 tests: initial state, adult addon count subtitle, toggle confirmation dialog)
  - `AddonManagerViewModelTest` (extended: adult addon 18+ badge, locked switch, URL preview warning)

### Public API for later prompts:
- `ListStatus` (`PLAN_TO_WATCH`, `WATCHING`, `COMPLETED`, `ON_HOLD`, `DROPPED`)
- `LibraryRepository.setStatus(item: MetaPreview, status: ListStatus?)`
- `LibraryRepository.observeListStatuses(): Flow<Map<String, ListStatus>>`
- `PosterActionsHost` / `LocalPosterActions` (`LocalPosterActions.current.show(item: MetaPreview, fromContinue: Boolean)`)
- `StreamsSheetHost` / `LocalPlayback` (`LocalPlayback.current.open(request: PlaybackRequest)`)
- `ContentFilter` (`isAdultText(text: String)`, `MetaPreview.isAdultContent()`)
- `ContentPrefsRepository` (`val familyMode: Flow<Boolean>`, `suspend fun setFamilyMode(enabled: Boolean)`)
- `AddonRepository.observeActive(): Flow<List<Addon>>`
- `PosterMark` (`WATCHED`, `WATCHING`), `buildPosterMarks(statuses, watchedMovieIds)`

### Known limitations:
- Video stream extraction and real stream sheet UI are placeholder until P07-P09.
- Automatic status update to `WATCHING` on play is scheduled for P10.
- Status tabs on the Library screen will be implemented in P12.

### Open questions:
None.
