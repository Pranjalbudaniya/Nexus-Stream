## Report: P04b
Status: done
Access proof (raw grep output):
252:### 6.2 Home ✅ (hero + rows)
336:### 7.3 Catalog fan-out (Home)
396:### 7.8 Verified Cinemeta facts (P02a live manifest 3.0.14, 2026-10-14)
Carry-over (branch, checkAll):
- Base branch: p04a-app-shell
- Active branch: p04b-home-rows
- Baseline checkAll: PASS (57 actionable tasks, 117 unit tests passed)
Part 0: dependency alias proof (both outputs), AGENTS.md diff, N1-N14 table:
1. Output of `grep -n "navigation" app/build.gradle.kts gradle/libs.versions.toml`:
app/build.gradle.kts:61:    implementation(libs.androidx.navigation.compose)
app/build.gradle.kts:62:    implementation(libs.androidx.hilt.navigation.compose)
gradle/libs.versions.toml:46:androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "androidxNavigationCompose" }
gradle/libs.versions.toml:49:androidx-hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "androidxHiltNavigationCompose" }

2. Output of `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep -E "navigation-compose|hilt-navigation-compose"`:
+--- androidx.navigation:navigation-compose:2.10.2
|    \--- androidx.navigation:navigation-compose-android:2.10.2
|         |         +--- androidx.navigation:navigation-compose:2.10.2 (c)
|         |         \--- androidx.navigation:navigation-compose:2.10.2 (c)
+--- androidx.hilt:hilt-navigation-compose:1.4.0
|    +--- androidx.navigation:navigation-compose:2.9.0 -> 2.10.2 (*)

3. AGENTS.md diff:
```diff
--- a/AGENTS.md
+++ b/AGENTS.md
@@ -47,6 +47,7 @@ The task is created in prompt P00. Before P00 exists, skip this step.
 ## Report
 End every prompt with the report format in `docs/PROMPT_STRUCTURE.md` section 5.
 Save it to `docs/reports/<prompt-id>_report.md`.
+Use exactly the report headings given in the prompt. A report in any other format counts as incomplete.
 
 ## Code quality
 - Think first: write a short plan (files, functions, reused code) before writing code.
```

4. N1-N14 Table:
| Rule | Status | Evidence (file and line) |
|---|---|---|
| N1 | Implemented | `NexusApp.kt:55-58` (`saveState = true`, `restoreState = true`, `popUpTo(startDest) { saveState = true }`) |
| N2 | Implemented | `NexusApp.kt:57` (`launchSingleTop = true`) |
| N3 | Implemented | `NexusApp.kt:55` (`popUpTo(startDest)` pops to Home from any tab; back on Home exits; `NexusNavHost.kt:42` navigateUp) |
| N4 | Implemented | `NexusApp.kt:29-32` (`showBottomBar` only true for routes matching `TopLevelDestination`) |
| N5 | Implemented | `MainActivity.kt:25` (`enableEdgeToEdge()`) and `NexusApp.kt:80` (`Modifier.padding(innerPadding)`) |
| N6 | Implemented | `Routes.kt:1-24`, Compose Navigation persists route state across rotation / process recreation |
| N7 | Implemented | `MainActivity.kt:26,45` through `OneShot<String>` (`OneShot.kt:9-21`), `NexusApp.kt:35-43`, `AddonManagerScreen.kt:80-87` |
| N8 | Implemented | `HomeScreen.kt:70-76` gear icon button navigates to `Settings`; back returns to Home |
| N9 | Implemented | `NexusApp.kt:65,70` tab labels & descriptions; `HomeScreen.kt:73` and `SettingsScreen.kt:33` icon button descriptions |
| N10 | Implemented | `NexusApp.kt:71-72` tab labels use `maxLines = 1`, `overflow = TextOverflow.Ellipsis` |
| N11 | Implemented | `AddonManagerScreen.kt:69` scoped to destination entry via `hiltViewModel()`, surviving rotation |
| N12 | Implemented | `Routes.kt:1-24` all typed `@Serializable` Kotlin objects/classes, zero string routes |
| N13 | Implemented | `MaterialTheme.colorScheme` and `NexusTheme.spacing`/`shapes` tokens used throughout |
| N14 | Implemented | Standard navigation defaults preserved, no custom motion code |

Files created / modified:
- Created:
  - `app/src/main/java/com/nexus/stream/domain/model/HomeRowSpec.kt`
  - `app/src/main/java/com/nexus/stream/domain/usecase/BuildHomeRows.kt`
  - `app/src/main/java/com/nexus/stream/data/remote/addon/HomeRowLoader.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/home/HomeViewModel.kt`
  - `app/src/main/java/com/nexus/stream/presentation/common/AddonErrorText.kt`
  - `app/src/main/java/com/nexus/stream/presentation/common/PosterCard.kt`
  - `app/src/main/java/com/nexus/stream/presentation/common/ContentRow.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/detail/DetailScreen.kt`
  - `app/src/test/java/com/nexus/stream/domain/usecase/BuildHomeRowsTest.kt`
  - `app/src/test/java/com/nexus/stream/data/remote/addon/HomeRowLoaderTest.kt`
  - `app/src/test/java/com/nexus/stream/presentation/feature/home/HomeViewModelTest.kt`
- Modified:
  - `AGENTS.md`
  - `app/src/main/java/com/nexus/stream/presentation/feature/addons/AddonErrors.kt`
  - `app/src/main/java/com/nexus/stream/presentation/feature/home/HomeScreen.kt`
  - `app/src/main/java/com/nexus/stream/presentation/navigation/Routes.kt`
  - `app/src/main/java/com/nexus/stream/presentation/navigation/NexusNavHost.kt`
  - `app/src/main/res/values/strings.xml`

Rules H1-H17: implemented / not implemented (reasons):
- H1: Implemented. Startup seeding runs in `HomeViewModel.init` calling `EnsureDefaultAddons`. Online: installs Cinemeta and emits 4 rows. Offline: sets `seedFailed = true` and shows seed failure with Retry button.
- H2: Implemented. If no addons exist, shows empty state with Add addon button. If addons exist without catalogs, shows no catalog addons state with button to Addons.
- H3: Implemented. Rows load independently; timeouts (8 s) and failures show inline `Failed` state with Retry, other rows remain unaffected.
- H4: Implemented. In `ContentRow`, if `status is RowStatus.Loaded && status.items.isEmpty()`, the whole row is not rendered.
- H5: Implemented. `PosterCard` falls back to `surfaceContainerHigh` box with centered title (max 3 lines) if poster is null or fails to load.
- H6: Implemented. Duplicate IDs in a row are deduped by `CatalogPage`.
- H7: Implemented. `rememberLazyListState` and ViewModel + in-memory cache maintain state and scroll position across rotation and tab navigation.
- H8: Implemented. When addon list updates, rows whose key is unchanged retain their state without refetching; new keys start loading; removed keys are dropped.
- H9: Implemented. M3 `PullToRefreshBox` reloads all rows with `force = true` (clearing cache) and retries failed seed; stops indicator upon completion.
- H10: Implemented. When >= 2 network failures occur and none is loading, top banner displays "Couldn't reach your addons. Check your connection." with Retry all button.
- H11: Implemented. At most 50 items per row; `Semaphore(4)` enforces maximum 4 concurrent row loads.
- H12: Implemented. Addon position first, then manifest catalog order in `BuildHomeRows`.
- H13: Implemented. Duplicate titles across addons disambiguated with `({addon.name})`.
- H14: Implemented. Non-movie/series types (channel -> Channels, tv -> TV, others capitalized) formatted and supported.
- H15: Implemented. Cards have content descriptions, retry buttons labeled, touch targets >= 48 dp, titles ellipsize.
- H16: Implemented. No custom animations or crossfade; 6 static skeleton cards in `surfaceContainerHigh`.
- H17: Implemented. Detail placeholder route `Detail(type, id)` displays type and id with back button returning to Home with scroll position preserved.

Suppressions used (max 2, with reasons):
- 0 suppressions used. (Eliminated previously existing `@file:Suppress("MatchingDeclarationName")` by moving code to `AddonErrorText.kt`).

Simplification pass:
- Extracted `formatErrorMessage` helper in `ContentRow` avoiding spread operator and reflection.
- Used `CatalogSpec` parameter in test helpers to satisfy Detekt's `LongParameterList`.
- Directly re-exported `AddonErrorText` and `toUserMessage` in `feature.addons.AddonErrors` to ensure backwards compatibility without duplication.
- Avoided redundant try-catch in `HomeRowLoader.load` allowing cancellation to propagate naturally and satisfy Detekt's `RethrowCaughtException`.

Assumptions made:
- In-memory cache in `HomeRowLoader` uses a 5-minute TTL and max 50 items per row as decided in prompt memory refs D44, D48, D50, D51, D52.

Deviations:
- None.

Verification: checkAll ✔/✘ · release manifest check · new tests list
- checkAll: ✔ (57 actionable tasks executed/up-to-date, 142 total unit tests passed, detekt passed, spotless passed, hexGuard passed).
- release manifest check: ✔ (`DevToolsActivity` absent from release manifest).
- new tests list:
  - `BuildHomeRowsTest`: 9 tests (Cinemeta fixture 4 rows, non-board catalog exclusions, disabled addons, position order, title disambiguation, blank name fallback, unknown type capitalization, known type formatting, unique stable keys).
  - `HomeRowLoaderTest`: 8 tests (max 50 items, 8 s timeout mapping to Timeout error, 5-minute cache hit, force=true bypass, non-caching of failures, TTL expiry re-fetch, cancellation re-throw, max 4 concurrent loads).
  - `HomeViewModelTest`: 8 tests (init seed call, seed failure state, loading then loaded transition, unchanged row retention on addon toggle, single row retry with force=true, refresh all with force=true, two network failures trigger offline banner, single row failure isolation).

Public API for later prompts: (BuildHomeRows, HomeRowLoader, PosterCard, ContentRow, Detail route)
- `BuildHomeRows`: `operator fun invoke(addons: List<Addon>): List<HomeRowSpec>`
- `HomeRowLoader`: `suspend fun load(spec: HomeRowSpec, force: Boolean = false): Outcome<List<MetaPreview>>`, `fun clear(key: String)`, `fun clearAll()`
- `PosterCard`: `@Composable fun PosterCard(item: MetaPreview, onClick: () -> Unit, modifier: Modifier = Modifier)`
- `ContentRow`: `@Composable fun ContentRow(title: String, status: RowStatus, onRetry: () -> Unit, onItemClick: (type: String, id: String) -> Unit, modifier: Modifier = Modifier)`
- `Detail`: `@Serializable data class Detail(val type: String, val id: String)`

Known limitations:
- Hero banner, Continue Watching, and watchlist are out of scope and deferred to P04c.
- Search and Discover content deferred to P05a/P05b.

Open questions:
- None.
