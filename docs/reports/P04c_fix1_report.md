## Report: P04c.fix1
Status: done
Changes (files and what changed):
- `app/src/main/java/com/nexus/stream/presentation/feature/home/HeroBanner.kt`:
  - Enclosed hero pager in a Box with `padding(horizontal = NexusTheme.spacing.lg)` and `.clip(NexusTheme.shapes.card)` so corners follow the Roundness setting.
  - Moved dots indicator below the box with `Spacer(modifier = Modifier.height(NexusTheme.spacing.sm))` above it, centered horizontally, and hidden for a single page.
  - Replaced background-fading gradient with a bottom scrim from `Color.Transparent` to `Color.Black` at 75% alpha over the lower 55% of the box (using named constants `SCRIM_START_FRACTION = 0.45f`, `SCRIM_END_FRACTION = 1.0f`, `SCRIM_ALPHA = 0.75f`).
  - Updated hero text and icons on the image to use `Color.White` for readability across all themes (including Light theme).
  - Content padding inside the box set to `NexusTheme.spacing.lg` so large corner radii never clip content.
  - Updated auto-advance timer to `HERO_AUTO_ADVANCE_MS = 10_000L` (10 seconds).
  - Updated `HeroSkeleton` to use `padding(horizontal = NexusTheme.spacing.lg)` and `.clip(NexusTheme.shapes.card)`.
- `app/src/main/java/com/nexus/stream/presentation/feature/home/HomeScreen.kt`:
  - Set `topPadding = NexusTheme.spacing.sm` uniformly for LazyColumn so the rounded hero box floats with comfortable spacing below the top app bar.
- `app/src/test/java/com/nexus/stream/presentation/feature/home/HeroPagingTest.kt`:
  - Added unit test asserting `HERO_AUTO_ADVANCE_MS == 10_000L`.

Edge cases R1-R9: addressed / not addressed (reasons):
- R1 (Roundness Sharp): addressed. Corners are clipped by `NexusTheme.shapes.card`, providing square corners when Roundness is Sharp.
- R2 (Roundness Extra round): addressed. Corners are large, and inner padding of `NexusTheme.spacing.lg` ensures text, logo, and buttons are not clipped.
- R3 (Changing Roundness): addressed. Shape is read via `NexusTheme.shapes.card` dynamically in composition, so changes in Theme Gallery reflect live.
- R4 (Loading / no backdrop): addressed. `HeroSkeleton` and the fallback box use the same horizontal margins (`NexusTheme.spacing.lg`), aspect ratio (`HERO_ASPECT`), and card shape.
- R5 (Light theme): addressed. Hero text on the image uses `Color.White` on top of the black 75% alpha scrim, ensuring high contrast in both light and dark themes.
- R6 (Pull to refresh): addressed. The box moves together with the rest of the content within `NexusRefreshBox` without clipping artifacts.
- R7 (Alignment): addressed. The box uses `padding(horizontal = NexusTheme.spacing.lg)`, exactly matching the left and right alignment of the row titles and first poster card.
- R8 (Auto-advance): addressed. Advances every 10 seconds via `HERO_AUTO_ADVANCE_MS = 10_000L`, pauses while dragged or touched, and restarts afterwards.
- R9 (Large font and UI size): addressed. Buttons wrap in `FlowRow`, titles ellipsize at 2 lines, info line ellipsizes at 1 line.

Verification: checkAll ✔
- `./gradlew checkAll` passed (assembleDebug, unit tests, detektDebug, detektDebugUnitTest, spotlessCheck, hexGuard).
- `git grep -n "6_000\|6000" app/src/main` returned zero results.

Assumptions made:
- Kept 8.dp (`NexusTheme.spacing.sm`) top padding on LazyColumn so the hero box does not abut the solid top bar.

Deviations:
None.

Known limitations:
None for this fix prompt.

Open questions:
None.
