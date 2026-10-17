## Report: P03.1
Status: done
Access proof (raw grep output):
```
325:### 6.8 Settings ✅ (sections)
420:### 7.7 Addon manager
567:## 11. Continue Watching, Library, History
```
Carry-over (branch, checkAll):
- Base branch: `p06-fix2-library` (commit `3ec5b8f`)
- Working branch: `p03-1-addon-store`
- Prior checkAll passed with 0 errors before starting P03.1 changes.

Store response (top-level shape, entry count, size KB, entry keys):
- Top-level shape: JSON object with `"addons"` array (also supported: raw JSON array)
- Entry count: 326 entries in official `stremio-addons.net` response
- Size KB: ~115 KB (114.6 KB)
- Entry keys: `"transportUrl"`, `"manifest"` (manifest contains `id`, `name`, `version`, `description`, `resources`, `types`, `catalogs`, `behaviorHints`, `logo`, etc.)

Watchlist inconsistency root cause and fix:
- Root cause:
  1. `LibraryRepositoryImpl.observeListStatuses()` previously exposed individual database query flows directly, so disparate collectors across `HomeViewModel`, `DetailViewModel`, and poster sheets observed separate streams with possible collection timing skew.
  2. In `DetailViewModel.kt`, `updateWithResult` was constructing a refreshed metadata state without carrying forward existing `listStatus` from `uiState.value`, temporarily clearing or overwriting it before the list flow emitted.
  3. `WatchOptionsSheet` was receiving static status values on creation rather than observing reactive updates.
- Fix:
  1. In `LibraryRepositoryImpl`, `observeListStatuses()` is shared as an eager `StateFlow` via `.stateIn(scope, SharingStarted.Eagerly, emptyMap())` ensuring all observers across the app immediately see the unified current mapping.
  2. In `DetailViewModel`, `updateWithResult` explicitly preserves `current.listStatus`.
  3. Verified with full scenario matrix test suite (`LibrarySyncTest.kt`) covering hero addition, detail status changes, poster sheet removal, row navigation, and process recreation state flow.

Files created / modified:
- Data & Domain:
  - Created `app/src/main/java/com/nexus/stream/domain/model/AddonCategory.kt`
  - Created `app/src/main/java/com/nexus/stream/domain/model/StoreAddon.kt`
  - Created `app/src/main/java/com/nexus/stream/domain/repository/CommunityAddonsRepository.kt`
  - Created `app/src/main/java/com/nexus/stream/data/repository/CommunityAddonsRepositoryImpl.kt`
  - Created `app/src/main/java/com/nexus/stream/domain/usecase/RefreshAllAddons.kt`
  - Created `app/src/main/java/com/nexus/stream/domain/usecase/AddonFilters.kt`
  - Created `app/src/main/java/com/nexus/stream/data/remote/addon/StoreMappers.kt`
  - Modified `app/src/main/java/com/nexus/stream/domain/model/AddonManifest.kt`
  - Modified `app/src/main/java/com/nexus/stream/data/remote/addon/AddonApi.kt`
  - Modified `app/src/main/java/com/nexus/stream/data/remote/addon/AddonDtos.kt`
  - Modified `app/src/main/java/com/nexus/stream/data/remote/addon/AddonMappers.kt`
  - Modified `app/src/main/java/com/nexus/stream/data/remote/addon/AddonRemoteDataSource.kt`
  - Modified `app/src/main/java/com/nexus/stream/data/repository/LibraryRepositoryImpl.kt`
  - Modified `app/src/main/java/com/nexus/stream/di/RepositoryModule.kt`
- Presentation:
  - Created `app/src/main/java/com/nexus/stream/presentation/feature/addons/StoreAddonRow.kt`
  - Created `app/src/main/java/com/nexus/stream/presentation/feature/addons/AddonStoreViewModel.kt`
  - Created `app/src/main/java/com/nexus/stream/presentation/feature/addons/AddonStoreSheet.kt`
  - Created `app/src/main/java/com/nexus/stream/presentation/feature/addons/AddonEmptyViews.kt`
  - Created `app/src/main/java/com/nexus/stream/presentation/feature/addons/AddonManagerList.kt`
  - Modified `app/src/main/java/com/nexus/stream/presentation/feature/addons/AddonCard.kt`
  - Modified `app/src/main/java/com/nexus/stream/presentation/feature/addons/AddonManagerScreen.kt`
  - Modified `app/src/main/java/com/nexus/stream/presentation/feature/addons/AddonManagerViewModel.kt`
  - Modified `app/src/main/java/com/nexus/stream/presentation/feature/detail/DetailViewModel.kt`
  - Modified `app/src/main/res/values/strings.xml`
- Tests & Fixtures:
  - Created `app/src/test/java/com/nexus/stream/domain/model/AddonCategoryTest.kt`
  - Created `app/src/test/java/com/nexus/stream/data/remote/addon/StoreMappersTest.kt`
  - Created `app/src/test/java/com/nexus/stream/data/repository/CommunityAddonsRepositoryTest.kt`
  - Created `app/src/test/java/com/nexus/stream/domain/usecase/RefreshAllAddonsTest.kt`
  - Created `app/src/test/java/com/nexus/stream/domain/usecase/AddonFiltersTest.kt`
  - Created `app/src/test/java/com/nexus/stream/presentation/common/LibrarySyncTest.kt`
  - Created `app/src/test/java/com/nexus/stream/presentation/feature/addons/AddonStoreViewModelTest.kt`
  - Modified `app/src/test/java/com/nexus/stream/presentation/feature/addons/AddonManagerViewModelTest.kt`
  - Created `app/src/test/resources/fixtures/store_manifest.json`
  - Created `app/src/test/resources/fixtures/store_addon_catalog.json`
  - Created `app/src/test/resources/fixtures/store_edge.json`

Rules S1-S21: implemented / not implemented (reasons):
- S1 (Never preloaded): implemented. The store manifest and catalog are only queried on-demand by `CommunityAddonsRepository` when opening or refreshing the store sheet. Never inserted into database.
- S2 (Response shape): implemented. `StoreMappers` tolerantly decodes object with `"addons"` or raw array, dropping invalid URLs/non-objects and falling back cleanly if manifest is omitted.
- S3 (Large list): implemented. Parsing and filtering run on `@DefaultDispatcher`; lists use lazy column with stable keys.
- S4 (Categories): implemented. `AddonCategory` handles CATALOGS, STREAMS, SUBTITLES, and OTHER (e.g. meta-only). Unsupported entries (e.g. addon_catalog only) marked non-usable.
- S5 (Installed list): implemented. "All" groups into sections with each addon once; category chips show matching addons; empty sections hidden.
- S6 (Search): implemented. Case-insensitive, trimmed search over name, description, and host. Clear button provided.
- S7 (Installed state): implemented. Compares normalized URLs against live `addonRepository.observeAll()` flow; flips live to Installed on completion.
- S8 (http entries): implemented. Intercepts `http://` URLs with confirmation dialog ("Unencrypted connection") before proceeding with installation.
- S9 (Family mode): implemented. Filters adult-flagged addons and names/descriptions matching `ContentFilter` when Family mode is on; displays "18+" badge when off.
- S10 (Install failures): implemented. Friendly user-facing error strings; row remains installable; never reveals full URL.
- S11 (Refresh all): implemented. Concurrency semaphore of 3; reports progress; single-flight mutex; includes disabled addons; failure badge on failed cards.
- S12 (Buttons): implemented. Toolbar with "Install" and "Refresh all"; floating button renamed "Add by URL"; no duplicated buttons.
- S13 (Banners): implemented. No-streams and no-catalogs banners provide action button to open store directly on STREAMS/CATALOGS category.
- S14 (Reorder): implemented. Move up/down swaps position relative to neighbor in currently visible filtered list.
- S15 (Sheet behavior): implemented. Smooth dismiss with `skipPartiallyExpanded = true`; `STORE_SHEET_GESTURES_ENABLED = true` constant and fallback Close button.
- S16 (Offline and errors): implemented. Static skeleton loading, friendly error cards with Retry, 10-minute cache allows offline viewing if previously loaded.
- S17 (Privacy): implemented. Displays host only (`toDisplayHost()`), never full URLs in logs, messages, or snackbars.
- S18 (Insets): implemented. Applied cleanly without doubling padding.
- S19 (Large font and UI size): implemented. Horizontal chip scrolling; minimum 48dp action heights; flexible wrapping text.
- S20 (Watchlist consistency): implemented. Shared eager flow in repository; passed scenario matrix A-F in `LibrarySyncTest.kt`.
- S21 (Conventions): implemented. All strings in `strings.xml`; no hex colors; stock Compose animations; `Nexus*` buttons; 0 `@Suppress` added.

Duplicates audit (Addons screen, store sheet):
- Addons screen:
  - Top toolbar: Exactly two actions — "Install" (opens Community Store) and "Refresh all".
  - Floating action button: Exactly one — "Add by URL" (opens manual add sheet).
  - Search bar: Exactly one search input with clear icon.
  - Category row: Unique category chips (All, Catalogs, Streams, Subtitles, Other) with counts.
  - Addon card: Reorder, Refresh, Configure, Copy URL, Remove in single overflow menu.
  - Result: No duplicate controls or repeated information anywhere on the screen.
- Addon Store sheet:
  - Header: Title and Refresh icon button. Close text button rendered conditionally only when gesture dismiss is disabled (`!STORE_SHEET_GESTURES_ENABLED`).
  - Search: Single search field with clear icon.
  - Category row: Filter chips with live search match counts.
  - Addon rows: Exactly one action button per row (Install / Installed / loading spinner), capability chips, and 18+ badge.
  - Result: Completely free of duplicated actions or text.

Sheet API used and jitter fallback:
- API used: `ModalBottomSheet` from `androidx.compose.material3:material3` with `sheetGesturesEnabled = STORE_SHEET_GESTURES_ENABLED`.
- Fallback guard: `STORE_SHEET_GESTURES_ENABLED` constant defined in `AddonStoreSheet.kt` (defaults to `true`). If gesture jitter is observed on a device, flipping it to `false` automatically presents a visible, accessible "Close" `NexusTextButton` in the header for manual dismissal without vertical drag conflicts.

Suppressions used (max 2, with reasons):
- 0 suppressions used in new P03.1 code.

Simplification pass:
- Extracted `AddonEmptyViews.kt` and `AddonManagerList.kt` to keep `AddonManagerScreen.kt` clean and under the Detekt 60-line function limit.
- Simplified `HttpConfirmationDialog` by eliminating unnecessary parameter bindings.
- Used pure domain functions in `AddonFilters` and `AddonCategory` for grouping and counting logic, making unit testing straightforward and avoiding UI state complexity.

Assumptions made:
- Default store manifest URL is `https://stremio-addons.net/api/manifest.json`.
- The first `addonCatalogs` spec entry in the manifest points to the root community catalog (`addon_catalog/all/stremio-addons.net.json`).

Deviations:
- None. All requirements, constraints, and architecture rules followed strictly.

Verification: checkAll ✔/✘ · release manifest check · new tests list:
- `./gradlew checkAll`: ✔ PASSED (57 tasks: assembleDebug, unit tests, detekt, spotless, hexGuard).
- Release manifest check: ✔ Verified `DevToolsActivity` is absent from release merged manifest (`app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`).
- Room schemas check: ✔ `git status app/schemas` is clean; no database schema modifications.
- New unit tests list:
  1. `AddonCategoryTest.kt` (7 tests)
  2. `StoreMappersTest.kt` (8 tests)
  3. `CommunityAddonsRepositoryTest.kt` (6 tests)
  4. `RefreshAllAddonsTest.kt` (6 tests)
  5. `AddonFiltersTest.kt` (7 tests)
  6. `AddonManagerViewModelTest.kt` (extended with 6 tests)
  7. `LibrarySyncTest.kt` (6 scenario matrix tests)
  8. `AddonStoreViewModelTest.kt` (6 tests)

Public API for later prompts: (CommunityAddonsRepository, StoreAddon, AddonCategory/categoriesOf, AddonFilters, RefreshAllAddons):
- `CommunityAddonsRepository`: `suspend fun fetch(forceRefresh: Boolean = false): Outcome<List<StoreAddon>>`
- `StoreAddon`: Immutable data model representing community addons with capability flags (`categories`, `primary`, `p2p`, `isAdult`, `configurable`, `usable`, `hasDetails`)
- `AddonCategory`: Enum (`CATALOGS`, `STREAMS`, `SUBTITLES`, `OTHER`) with helper pure functions `categoriesOf(manifest)` and `primaryCategory(categories)`
- `AddonFilters`: Pure filtering and grouping functions (`filterInstalled`, `groupBySection`, `categoryCounts`, `filterStore`, `storeCategoryCounts`)
- `RefreshAllAddons`: Use case for concurrently refreshing all installed addons with progress updates (`invoke(onProgress): RefreshSummary`)

Known limitations:
- Community addon store entries cannot be customized or configured prior to installation; configuration flows remain post-installation if supported by the addon.
- Stremio account synchronization of installed addons is out of scope (scheduled for P14).

Open questions:
- None.
