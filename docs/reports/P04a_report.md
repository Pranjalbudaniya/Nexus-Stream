# P04a App Shell & Navigation - Implementation Report

## Prompt Metadata
- **Prompt ID:** P04a
- **Branch:** p04a-app-shell
- **Date:** 2026-10-15
- **Status:** PASS

## Access Proof
- Line 145: `### 4.2 Package layout`
- Line 245: `### 6.1 Navigation`
- Line 295: `### 6.8 Settings ✅ (sections)`

## Work Completed
- Updated `AGENTS.md` loading state rule: changed `loading (shimmer)` to `loading (static skeleton, no animation)` with zero other changes.
- Added Navigation Compose (`androidx.navigation:navigation-compose:2.8.9`) and Hilt Navigation Compose (`androidx.hilt:hilt-navigation-compose:1.2.0`) to `app/build.gradle.kts`.
- Created `OneShot<T>` observable consume-once state holder in `presentation/common/OneShot.kt` with unit tests covering setting, observing, single consumption, and null returns in `presentation/common/OneShotTest.kt`.
- Created type-safe `@Serializable` navigation routes in `presentation/navigation/Routes.kt` (`Home`, `Discover`, `Search`, `Library`, `Settings`, `Addons(prefillUrl)`).
- Created `TopLevelDestination` enum defining the 4 bottom bar destinations (`HOME`, `DISCOVER`, `SEARCH`, `LIBRARY`) with routes, label string resource IDs, and icons.
- Created placeholder screens for `HomeScreen` (with `TopAppBar` and settings gear action), `DiscoverScreen`, `SearchScreen`, and `LibraryScreen`, each with static placeholder text, theme tokens, and `@Preview` annotations for light and dark themes.
- Created `SettingsScreen` with a top app bar, back navigation, functional "Addons" row navigating to `Addons()`, and disabled "Appearance" row explaining debug DevTools usage.
- Updated `AddonManagerScreen` with `onNavigateUp` back navigation support, `prefillUrl` consume-once handling via `rememberSaveable`, and default `hiltViewModel()` injection.
- Implemented `NexusNavHost` hosting all 6 routes with type-safe routing.
- Implemented `NexusApp` shell with M3 `Scaffold`, conditional `NavigationBar` visibility (only visible on top-level routes), destination selection state, and one-shot deep-link handoff.
- Updated `MainActivity` to instantiate `OneShot<String>()`, extract deep links via `AddonUrlBuilder.fromDeepLink` in `onCreate` and `onNewIntent`, clear intent data, and host `NexusApp`.

## Architecture and Design Decisions
- **`OneShot<T>` Rationale:** Avoids event replay issues and missed events across Activity lifecycle and Compose recomposition boundaries by encapsulating a mutable `State<T?>` with an atomic-like `consume()` method that returns the payload and clears the state.
- **Bottom Bar Visibility Pattern:** Evaluated dynamically using `navController.currentBackStackEntryAsState()` and `TopLevelDestination.entries.any { destination -> currentDestination?.hasRoute(destination.route::class) == true }`. Sub-screens (`Settings`, `Addons`) automatically hide the navigation bar.
- **Deep-link Handoff Pattern:** Handled via `OneShot<String>` populated by `MainActivity` and observed inside `NexusApp` with `LaunchedEffect`. Single-top launch mode allows both cold start and warm re-entry to safely navigate to `Addons(prefillUrl = url)` and trigger the install dialog once.

## Verification Results
- `./gradlew checkAll`: PASS (117 unit tests passed across 20 test classes; assembleDebug, detektDebug, spotlessCheck, hexGuard all passed).
- `apkanalyzer manifest print`: PASS (`android:launchMode="1"` present on `MainActivity`; `stremio` scheme intent-filter verified).
- Zero URL logging: PASS (`grep -rn "Log\.\|println(" app/src/main` returned 0 matches).
- AGENTS.md diff: PASS (exactly 1 line changed).

## Screen Inventory
| Screen | Route | Top Bar | Bottom Bar | State / Actions |
|---|---|---|---|---|
| Home | `Home` | `TopAppBar` with title and gear action | Visible | Placeholder text, gear opens Settings |
| Discover | `Discover` | `TopAppBar` with title | Visible | Placeholder text |
| Search | `Search` | `TopAppBar` with title | Visible | Placeholder text |
| Library | `Library` | `TopAppBar` with title | Visible | Placeholder text |
| Settings | `Settings` | `TopAppBar` with back navigation | Hidden | Addons row (navigates), Appearance row (disabled) |
| Addon Manager | `Addons(prefillUrl)` | `TopAppBar` with back navigation | Hidden | Full addon manager; consumes prefillUrl once |

## Diff Summary
```
 AGENTS.md                                          |   2 +-
 app/build.gradle.kts                               |   2 +
 app/src/main/java/com/nexus/stream/MainActivity.kt |  13 +--
 .../nexus/stream/presentation/common/OneShot.kt    |  21 ++++
 .../feature/addons/AddonManagerScreen.kt           |  31 ++++-
 .../feature/discover/DiscoverScreen.kt             |  58 ++++++++++
 .../stream/presentation/feature/home/HomeScreen.kt |  73 ++++++++++++
 .../presentation/feature/library/LibraryScreen.kt  |  58 ++++++++++
 .../presentation/feature/search/SearchScreen.kt    |  58 ++++++++++
 .../feature/settings/SettingsScreen.kt             | 126 +++++++++++++++++++++
 .../stream/presentation/navigation/NexusApp.kt     |  88 ++++++++++++++
 .../stream/presentation/navigation/NexusNavHost.kt |  54 +++++++++
 .../nexus/stream/presentation/navigation/Routes.kt |  23 ++++
 .../presentation/navigation/TopLevelDestination.kt |  37 ++++++
 app/src/main/res/values/strings.xml                |  24 ++++
 .../stream/presentation/common/OneShotTest.kt      |  32 ++++++
 16 files changed, 690 insertions(+), 10 deletions(-)
```

## Open Issues / Follow-ups
- None. Ready for P04b (Home catalog rows / board presentation).
