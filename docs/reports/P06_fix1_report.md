# P06 Fix 1: Detail Screen Polish & Shapes Audit Report

## 1. Summary
Executed prompt `P06_fix1_detail_polish.md` covering:
- **Part A (Data & Domain)**: Built `cleanNames` list sanitizer, `headerHeightDp` screen percentage calculator, `watchActions` calculation engine, updated Room `ProgressDao` with `setWatchedMany` transaction and `clearInProgress`, and exposed both via `ProgressRepository`. Added unit test suites for all logic components.
- **Part B (Detail UI & Sheets)**: Updated `DetailHeader` with dynamic height (0.45 * screen height clamped [320dp, 480dp]), backdrop scrim, poster card sizing (120x180dp), status bar insets handling, and 48dp action buttons. Built `ChipRowSection` and `AllItemsSheet` for genres/cast/directors/writers with comma-separated splitting and deduplication. Added download icon placeholder and "Downloads aren't available yet" snackbar. Implemented bottom sheet for watch options (`WatchOptionsSheet`) with unwatch confirmation dialogs.
- **Part C (Home Hero & Shapes Audit)**: Removed details button from `HeroBanner`, made entire banner clickable (`onClickLabel = "Open details"`), added bottom row with description on left and 48dp `NexusIconButton` for watchlist toggling on right. Conducted a codebase-wide audit enforcing `NexusTheme.shapes` (`button`, `chip`, `card`, `poster`) across all buttons, FABs, chips, cards, and image containers, replacing all raw Material buttons with `NexusButton` wrappers. Appended the shapes rule bullet to `AGENTS.md`.

## 2. Changes Made
### Part A: Domain & Data
- `domain/usecase/NameLists.kt`: Created `cleanNames` pure function splitting by `,`, trimming whitespace, and deduplicating while preserving order. Tested in `NameListsTest`.
- `presentation/feature/detail/HeaderHeight.kt`: Created pure `headerHeightDp` computing `(screenHeightDp * 0.45f).coerceIn(320f, 480f)`. Tested in `HeaderHeightTest`.
- `domain/usecase/WatchActions.kt`: Created `watchActions` pure logic generator computing context-dependent actions for movies and series (ToggleWatchlist, SetMovieWatched, SetSeasonWatched, SetAllWatched, RemoveFromContinue). Tested in `WatchActionsTest`.
- `data/local/db/dao/ProgressDao.kt`: Added `@Transaction setWatchedMany` for atomic multi-episode status updates, and `clearInProgress(metaId)` setting position/duration to 0 and watched to false.
- `domain/repository/ProgressRepository.kt` & `data/repository/ProgressRepositoryImpl.kt`: Exposed `setWatchedMany` and `clearInProgress`. Tested in `ProgressRepositoryImplTest`.

### Part B: Detail UI & Sheets
- `presentation/common/NexusButtons.kt`: Created standardized button wrappers (`NexusButton`, `NexusOutlinedButton`, `NexusTonalButton`, `NexusTextButton`, `NexusIconButton`) binding default `NexusTheme.shapes.button`.
- `res/drawable/ic_download.xml`: Created vector drawable for download action using `?android:attr/colorControlNormal`.
- `presentation/feature/detail/DetailHeader.kt`: Implemented dynamic height header, backdrop scrim, 120x180dp poster, status bar insets back button, and 48dp action buttons.
- `presentation/feature/detail/ChipRowSection.kt`: Created horizontal scrolling chip row showing top 5 items and "All (N)" button when items exceed 5.
- `presentation/feature/detail/AllItemsSheet.kt`: Created modal bottom sheet rendering all tags in a flow layout.
- `presentation/feature/detail/WatchOptionsSheet.kt`: Created modal bottom sheet displaying calculated watch actions with contextual labels and icons.
- `presentation/feature/detail/EpisodeList.kt`: Integrated download icon placeholder emitting download event, and wired progress indicator bar on episode thumbnails.
- `presentation/feature/detail/DetailViewModel.kt`: Refactored to manage watch options sheet, unwatch confirmations, download notification effect, bulk watch transactions, and type-safe `SavedStateHandle` arg extraction without crashing in unit tests.
- `presentation/feature/detail/DetailScreen.kt`: Wired sheets, dialogs, snackbar host, and inlined message formatting.
- `res/values/strings.xml`: Added strings for chip sections, watch options, and download notifications.
- `presentation/feature/detail/DetailViewModelTest.kt`: Added unit test coverage for watch options, confirmation dialogs, watchlist toggle, and download notifications.

### Part C: Home Hero & Shapes Audit
- `presentation/feature/home/HeroBanner.kt`: Removed details button; made entire page clickable with `onClickLabel = "Open details"`; added bottom row with two-line description and 48dp watchlist `NexusIconButton`; updated padding to `NexusTheme.spacing.lg` on bottom and sides.
- Global Shapes Audit: Replaced all raw Material `Button`, `OutlinedButton`, `FilledTonalButton`, `TextButton`, `IconButton` with `Nexus*` wrappers; added `shape = NexusTheme.shapes.button` to FAB; added `shape = NexusTheme.shapes.chip` to chips (`FilterChip`, `AssistChip`, `SuggestionChip`); ensured cards use `NexusTheme.shapes.card` and thumbnails use `NexusTheme.shapes.poster`.
- `AGENTS.md`: Appended rule bullet requiring `NexusTheme.shapes` for buttons, chips, FABs, cards, and containers.

## 3. Shapes Audit Table
| File | Control | Old Shape | New Shape | Notes |
| --- | --- | --- | --- | --- |
| `AddAddonSheet.kt` | `OutlinedButton` (paste) | Material stadium default | `NexusTheme.shapes.button` | Replaced with `NexusOutlinedButton` |
| `AddAddonSheet.kt` | `Button` (check) | Material stadium default | `NexusTheme.shapes.button` | Replaced with `NexusButton` |
| `AddAddonSheet.kt` | `Button` (install/update) | Material stadium default | `NexusTheme.shapes.button` | Replaced with `NexusButton` |
| `AddAddonSheet.kt` | `TextButton` (error action) | Material stadium default | `NexusTheme.shapes.button` | Replaced with `NexusTextButton` |
| `AddAddonSheet.kt` | `SuggestionChip` (types) | Material stadium default | `NexusTheme.shapes.chip` | Added explicit `shape = NexusTheme.shapes.chip` |
| `AddAddonSheet.kt` | `SuggestionChip` (resources) | Material stadium default | `NexusTheme.shapes.chip` | Added explicit `shape = NexusTheme.shapes.chip` |
| `AddAddonSheet.kt` | `AssistChip` (catalogs) | Material stadium default | `NexusTheme.shapes.chip` | Added explicit `shape = NexusTheme.shapes.chip` |
| `AddonManagerScreen.kt` | `IconButton` (navigate up) | Material circle default | `NexusTheme.shapes.button` | Replaced with `NexusIconButton` |
| `AddonManagerScreen.kt` | `ExtendedFloatingActionButton` (add) | Material pill default | `NexusTheme.shapes.button` | Added explicit `shape = NexusTheme.shapes.button` |
| `AddonManagerScreen.kt` | `TextButton` (retry seed) | Material stadium default | `NexusTheme.shapes.button` | Replaced with `NexusTextButton` |
| `AddonManagerScreen.kt` | `OutlinedButton` (add cinemeta) | Material stadium default | `NexusTheme.shapes.button` | Replaced with `NexusOutlinedButton` |
| `AddonManagerScreen.kt` | `Button` (add addon empty) | Material stadium default | `NexusTheme.shapes.button` | Replaced with `NexusButton` |
| `AddonManagerScreen.kt` | `TextButton` (confirm remove) | Material stadium default | `NexusTheme.shapes.button` | Replaced with `NexusTextButton` |
| `AddonManagerScreen.kt` | `TextButton` (cancel remove) | Material stadium default | `NexusTheme.shapes.button` | Replaced with `NexusTextButton` |
| `AddonCard.kt` | `SuggestionChip` (catalogs) | Material stadium default | `NexusTheme.shapes.chip` | Added explicit `shape = NexusTheme.shapes.chip` |
| `AddonCard.kt` | `SuggestionChip` (meta) | Material stadium default | `NexusTheme.shapes.chip` | Added explicit `shape = NexusTheme.shapes.chip` |
| `AddonCard.kt` | `SuggestionChip` (streams) | Material stadium default | `NexusTheme.shapes.chip` | Added explicit `shape = NexusTheme.shapes.chip` |
| `AddonCard.kt` | `SuggestionChip` (subtitles) | Material stadium default | `NexusTheme.shapes.chip` | Added explicit `shape = NexusTheme.shapes.chip` |
| `AddonCard.kt` | `AssistChip` (host) | Material stadium default | `NexusTheme.shapes.chip` | Added explicit `shape = NexusTheme.shapes.chip` |
| `AddonCard.kt` | `IconButton` (overflow menu) | Material circle default | `NexusTheme.shapes.button` | Replaced with `NexusIconButton` |
| `SettingsScreen.kt` | `IconButton` (navigate up) | Material circle default | `NexusTheme.shapes.button` | Replaced with `NexusIconButton` |
| `HomeScreen.kt` | `IconButton` (settings gear) | Material circle default | `NexusTheme.shapes.button` | Replaced with `NexusIconButton` |
| `HomeScreen.kt` | `Button` (retry all) | Material stadium default | `NexusTheme.shapes.button` | Replaced with `NexusButton` |
| `HomeScreen.kt` | `Button` (empty state) | Material stadium default | `NexusTheme.shapes.button` | Replaced with `NexusButton` |
| `HeroBanner.kt` | `Button` (details) | Material stadium default | N/A | Removed Details button; entire banner is clickable |
| `HeroBanner.kt` | `OutlinedButton` (watchlist) | Material stadium default | `NexusTheme.shapes.button` | Replaced with 48dp `NexusIconButton` with Add/Check icon |
| `ContentRow.kt` | `Button` (retry) | Material stadium default | `NexusTheme.shapes.button` | Replaced with `NexusButton` |
| `AccentPicker.kt` | `AssistChip` (custom color) | Material stadium default | `NexusTheme.shapes.chip` | Added explicit `shape = NexusTheme.shapes.chip` |
| `AccentPicker.kt` | `TextButton` (turn off dynamic) | Material stadium default | `NexusTheme.shapes.button` | Replaced with `NexusTextButton` |
| `CustomColorSheet.kt` | `TextButton` (use default) | Material stadium default | `NexusTheme.shapes.button` | Replaced with `NexusTextButton` |
| `CustomColorSheet.kt` | `OutlinedButton` (cancel) | Material stadium default | `NexusTheme.shapes.button` | Replaced with `NexusOutlinedButton` |
| `CustomColorSheet.kt` | `Button` (apply) | Material stadium default | `NexusTheme.shapes.button` | Replaced with `NexusButton` |
| `DetailHeader.kt` | `Button` (play) | Material stadium default | `NexusTheme.shapes.button` | Uses `NexusButton` |
| `DetailHeader.kt` | `OutlinedButton` (trailer) | Material stadium default | `NexusTheme.shapes.button` | Uses `NexusOutlinedButton` |
| `DetailHeader.kt` | `OutlinedButton` (watchlist) | Material stadium default | `NexusTheme.shapes.button` | Uses `NexusOutlinedButton` |
| `DetailHeader.kt` | `OutlinedButton` (options) | Material stadium default | `NexusTheme.shapes.button` | Uses `NexusOutlinedButton` |
| `DetailScreen.kt` | `IconButton` (navigate up) | Material circle default | `NexusTheme.shapes.button` | Uses `NexusIconButton` |
| `DetailScreen.kt` | `Button` (addons) | Material stadium default | `NexusTheme.shapes.button` | Uses `NexusButton` |
| `DetailScreen.kt` | `Button` (retry) | Material stadium default | `NexusTheme.shapes.button` | Uses `NexusButton` |
| `DetailScreen.kt` | `TextButton` (confirm dialog) | Material stadium default | `NexusTheme.shapes.button` | Uses `NexusTextButton` |
| `DetailScreen.kt` | `TextButton` (dismiss dialog) | Material stadium default | `NexusTheme.shapes.button` | Uses `NexusTextButton` |
| `ChipRowSection.kt` | `TextButton` (all) | Material stadium default | `NexusTheme.shapes.button` | Uses `NexusTextButton` |
| `ChipRowSection.kt` | `SuggestionChip` (tags) | Material stadium default | `NexusTheme.shapes.chip` | Added explicit `shape = NexusTheme.shapes.chip` |
| `AllItemsSheet.kt` | `SuggestionChip` (all tags) | Material stadium default | `NexusTheme.shapes.chip` | Added explicit `shape = NexusTheme.shapes.chip` |
| `EpisodeList.kt` | `FilterChip` (season selection) | Material stadium default | `NexusTheme.shapes.chip` | Added explicit `shape = NexusTheme.shapes.chip` |
| `EpisodeList.kt` | `IconButton` (download) | Material circle default | `NexusTheme.shapes.button` | Uses `NexusIconButton` |

## 4. Verification Results
- `./gradlew checkAll`: **PASSED**
  - `spotlessKotlin` / `spotlessKotlinCheck`: Clean, 0 issues
  - `detektDebug` / `detektDebugUnitTest`: 0 issues
  - `testDebugUnitTest`: 243 tests completed, 0 failed
  - `assembleDebug`: SUCCESS
  - `hexGuard`: SUCCESS
- Release manifest check: **PASSED** (`DevToolsActivity` verified absent from release manifest via `./gradlew processReleaseMainManifest`)
- Room schema: **PASSED** (`app/schemas` unchanged at version 2)

## 5. Unrelated Issues Found
- `LocalClipboardManager` deprecation warning in Compose (`LocalClipboard` recommended in future).
- `hiltViewModel` deprecation warning moving to `androidx.hilt.lifecycle.viewmodel.compose`.
