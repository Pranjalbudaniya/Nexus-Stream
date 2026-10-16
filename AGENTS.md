# Nexus Stream: Agent Rules

Native Android app that consumes Stremio addons, with a built-in torrent streaming engine and a libVLC player.
Kotlin, Jetpack Compose, Material 3, Clean Architecture + MVVM. Package `com.nexus.stream`. minSdk 31. Phones only. English only.

## Source of truth
- Spec: `docs/NEXUS_STREAM_PROJECT_MEMORY.md`. Read the sections your prompt lists before coding.
- Prompt format and workflow: `docs/PROMPT_STRUCTURE.md`.
- Prompts: `docs/prompts/`. Agent reports: `docs/reports/`.
- Spec legend: ✅ confirmed, 🟡 assumed, ⚠️ unverified. Never treat a ⚠️ item as fact. Use only what the prompt marks as verified.

## Architecture
presentation -> domain <- data. Single Gradle module, packages by layer. Layout is in spec section 4.2.

## Rules
- If a prompt conflicts with the spec, STOP and report. Don't choose silently.
- If a fact the prompt calls "verified" proves wrong, STOP and report. Don't guess.
- minSdk 31. No API-level checks for anything below 31.
- No hardcoded colors or hex outside `presentation/theme/tokens/`. Use `MaterialTheme.colorScheme` or `NexusTheme.extended`.
- Use spacing, shape and type tokens. No magic dp/sp values in feature code.
- `domain/` has no Android imports. DTOs and entities never leave `data/`.
- No new dependency that isn't listed in spec section 3 or in the prompt. If one is needed, STOP and ask.
- All user-visible text goes in `strings.xml`.
- Inject dispatchers. No `GlobalScope`. No blocking calls on Main.
- Every ViewModel exposes `uiState` (StateFlow), `effects` (Flow) and `onEvent()`.
- Every list or network screen implements loading (static skeleton, no animation), error (retry) and empty states.
- Add `@Preview` for each screen-level composable (light and dark).
- Keep diffs small. Don't refactor unrelated files. List unrelated issues in the report instead.
- Don't add features, settings or screens the prompt doesn't list.
- Window insets are applied exactly once. The top-level Scaffold uses WindowInsets(0) and consumes its padding; each screen owns its insets.

## Stop and ask when
- an ambiguity changes the architecture
- a new dependency is needed
- the prompt conflicts with the spec
- a "verified" fact is wrong
- a required permission or system behavior differs from the prompt

## Verify
Run `./gradlew checkAll` (assembleDebug, unit tests, detekt, spotless, hexGuard) and make it pass before reporting done.
The task is created in prompt P00. Before P00 exists, skip this step.

## Git
- One branch per prompt, named like `p00-bootstrap`, `p01-theme-tokens`.
- Small commits with clear messages.
- Never commit directly to `main`, and never push, unless the prompt says so.

## Report
End every prompt with the report format in `docs/PROMPT_STRUCTURE.md` section 5.
Save it to `docs/reports/<prompt-id>_report.md`.
Use exactly the report headings given in the prompt. A report in any other format counts as incomplete.

## Code quality
- Think first: write a short plan (files, functions, reused code) before writing code.
- Write the least code that meets the spec. No dead code, no unused parameters, imports or resources, no speculative abstractions, no copy-paste (extract on the second use).
- Prefer platform and library APIs over custom code. Reuse existing components before adding new ones.
- Keep functions short and single-purpose. Composables take state and lambdas and read state as late as possible (lambda modifiers) to avoid needless recomposition.
- Memoize expensive work (`remember`, `derivedStateOf`). Keep work that belongs in a ViewModel or a pure function out of composition.
- Comments explain why, never what.
- Before reporting, do a cleanup pass: remove anything unused and list what you simplified.
- Animations: only stock Compose, Material, Navigation and Coil animations with default specs. No custom animation frameworks, motion tokens, per-frame color interpolation or animation settings.

