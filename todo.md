# KBminisplit — Build Plan

Implementation checklist, in execution order. Each phase ends with something testable.

> Spec is at [spec.md](spec.md). Any implementation deviation updates the spec in the same PR.

---

## Phase 0 — Project scaffold

Goal: empty app launches to a blank screen on an emulator.

- [x] `git init`, add `.gitignore` (Android + JetBrains)
- [x] Initialize Gradle project (Kotlin DSL, version catalog at `gradle/libs.versions.toml`)
- [x] Configure `app/build.gradle.kts`: min SDK 26, target SDK latest stable, Compose enabled, Kotlin 2.x
- [x] Add dependencies: Compose BOM, Material 3, Navigation-Compose, Lifecycle/ViewModel-Compose, Room, Hilt, Vico, Coroutines
- [x] Add test dependencies: JUnit, Turbine, Compose UI test, Room testing, Hilt testing
- [x] Set up package structure: `data/`, `domain/`, `ui/` (mirror §11 of spec)
- [x] Configure `AndroidManifest.xml` — single Activity, portrait-locked, no special permissions
- [x] Create monochrome Material 3 theme (`ui/theme/`): black/white/grays, override Material color scheme, define R/Y/G as feedback-only tokens
- [x] `KBMiniSplitApp` (Hilt `@HiltAndroidApp`) + `MainActivity` (`@AndroidEntryPoint`, sets a `KBMiniSplitTheme { /* empty Scaffold */ }`)
- [x] Verify: `./gradlew assembleDebug` builds; app installs and launches.

## Phase 1 — Domain core (pure Kotlin, no Android deps)

Goal: progression rules implemented as pure functions with exhaustive unit tests. This is the heart of the app — get it right before touching UI.

- [x] Enums: `Split { A, B, C }`, `SetStatus { Pending, Completed, Failed }`, `Feedback { Red, Yellow, Green }`, `Category { KB, A, B, C }`
- [x] Catalog: `Exercise` value class / sealed class with the canonical list — Swings, Clean & Press, Lunge, Goblet Squat, Push-up, Lat Pulldown, Barbell Row, Bench, OHP, High-Bar Squat, Deadlift — each tagged with category, `is_per_side`, `weight_step_kg`
- [x] Domain models: `Session`, `SetEntry`, `Prescription` (today's target for one movement: weight, reps), `DayPlan` (KB block + ordered strength pair)
- [x] `progression/nextSplit.kt` — `nextSplit(history): Split` (defaults to `A`)
- [x] `progression/prescription.kt` — implement §9.2 rules: same/fail → repeat; success & R<16 → R+1; success & R=16 → +step, reset to 8; never logged → onboarding values
- [x] `progression/movementOrder.kt` — alternate Movement 1 / Movement 2 order each cycle (parity on count of past sessions of that split)
- [x] `progression/kbBumpPrompt.kt` — should-prompt logic per §9.3 (first session of new calendar month + prior month had ≥1 session, snooze for 2 sessions if dismissed)
- [x] Unit tests covering: empty history, single past session, all-completed, mixed fail, weight rollover at R=16, KB prompt timing, snooze behavior, movement-order alternation across 6+ cycles
- [x] Verify: `./gradlew test` green, progression engine has no Android imports

## Phase 2 — Data layer (Room)

Goal: a clean repository API the UI can lean on, backed by SQLite.

- [x] Room entities mirroring §8: `ExerciseEntity`, `UserSettingsEntity`, `StartingWeightEntity`, `SessionEntity`, `SetEntryEntity`, `InProgressSessionEntity`, `InProgressSetEntity` (spec §8 updated to record the slug-PK / two-table in-progress / starting-weight deviations)
- [x] DAOs: queries needed by progression (history, by-date, date range), inserts, transactional session+sets insert, in-progress upsert/clear
- [x] `AppDatabase` (version=1, `exportSchema=true` → `app/schemas/`, migration policy documented in KDoc — no destructive migrations)
- [x] Seed exercise catalog via `RoomDatabase.Callback` (idempotent on every open)
- [x] Mappers: entity ↔ domain (`ExerciseMapper`, `SessionMapper`, `SettingsMapper`, `InProgressMapper`)
- [x] Repositories: `SessionRepository`, `SettingsRepository`, `InProgressRepository` — Flow-returning where reactive, suspend for writes
- [x] Hilt module wiring DB + DAOs + `Clock`
- [x] Instrumented tests on in-memory Room: seed correctness + idempotency, session round-trip, observe-between, in-progress upsert/clear, settings + snooze preservation
- [x] Verify in Android Studio: DB inspector shows seeded exercises after first launch

## Phase 3 — Onboarding

Goal: first launch collects starting weights and target reps, then opens Tracker.

- [x] `OnboardingViewModel` holding entered values in a single `StateFlow`
- [x] Three-step composable flow (HorizontalPager driven by step state, button-only nav):
  - [x] Step 1: KB weight (numeric, kg) — pre-filled `16`
  - [x] Step 2: six starting weights — all pre-filled with sensible defaults; user edits in place
  - [x] Step 3: starting target reps — pre-filled `8`, validated to 1–16
- [x] Persist on completion → `UserSettings.onboarded_at` set + six `starting_weight` rows. **Decision**: the persisted `OnboardingDefaults` (settings + starting_weight) is the sentinel — no synthetic "session 0" row. The progression engine already reads these as the baseline when a movement has no history (spec §9.2). Spec §7 + §8.2.1 updated.
- [x] App-launch branch in a root composable (`ui/root/RootApp.kt` + `RootViewModel`) observing `SettingsRepository.observeIsOnboarded()`; routes to `OnboardingScreen` or `MainShell`. `MainActivity` now hosts `RootApp` instead of an empty Scaffold.
- [x] Tightened `buildOnboardingDefaults` mapper to require every strength slug — closes a brief race where `user_settings` had been written but `starting_weight` rows hadn't yet, which would otherwise crash the prescription engine on never-logged movements (spec §8.2.1 deviation note).
- [x] Phase 3 stub for the post-onboarding screen: `ui/main/MainShell` reads today's `TodayPlan` (split + KB weight + both strength prescriptions) via `MainShellViewModel` and renders it as text. Phase 4 replaces this with the real Tracker UI.
- [x] Unit tests on `OnboardingViewModel` (mockk over `SettingsRepository`) covering: step navigation gating, validation (empty / non-numeric / zero / negative / out-of-range reps), comma decimal separator, `complete()` happy path with captured `OnboardingDefaults`, no-op when invalid, idempotency of repeated `complete()`.
- [x] Verify on a fresh install in Android Studio: flow completes, Tracker stub shows Session A with the entered weights.

## Phase 4 — Tracker tab

Goal: the only feature that has to feel right. Tap, see it stick, finish, get prompted, advance.

- [x] `SetButton` composable — three visual states (Pending / Completed / Failed), single-tap / double-tap / long-press gestures, haptic + scale animation, content descriptions for a11y
- [x] `TrackerViewModel`:
  - [x] On init, ask progression engine for today's prescription (split + movement order + per-movement Prescription). Builds the in-progress row set rather than a separate `DayPlan` — the in-progress snapshot *is* the plan. Spec §8.5 already covered this; no spec change.
  - [x] Persist to `InProgressSession` on every change (no debounce — every gesture is a single DAO `UPDATE`; cheap enough).
  - [x] Restore from `InProgressSession` on cold start; re-bootstrap if the snapshot's date or split doesn't match what today expects.
  - [x] `TrackerUiState.Ready.allButtonsResolved` drives the feedback sheet directly off `state`.
- [x] `TrackerScreen` composable rendering §4.1 layout:
  - [x] Header (split letter + name + date)
  - [x] KB Flow section: 5 movement labels (for reference) + 3 circuit buttons total, KB weight shown. **Spec deviation**: spec §4.1 originally mocked 5×3 buttons; corrected to 3 circuit buttons total since the KB flow is one performance unit (one weight, one chart in §6.1) and per-movement tracking would just add unused rows. Sentinel `kb_flow` exercise added to the catalog as the FK target for the 3 `set_entry` rows per session. Spec §2.2, §4.1, §8.1 updated.
  - [x] Strength section: 2 movements, each with Prime + 3 working buttons, weight + target reps shown
- [x] Feedback dialog: bottom modal sheet with R/Y/G dots; large touch targets; `confirmValueChange` vetoes Hidden so the user can't dismiss without picking.
- [x] On feedback tap: commit `Session` + `SetEntry` rows, clear `InProgressSession`, recompute next prescription, refresh (bootstrap re-runs and the next split appears immediately).
- [x] KB-bump prompt — one-button banner above the KB section; "Bump to N+2 kg" / "Not yet". Only shown when no KB set has been touched yet, so accepting a bump can safely reset the in-progress to the new weight. Accepting also stamps a snooze (`history.size` as the session-count baseline) so the prompt doesn't re-fire on the freshly-bootstrapped session before commit.
- [x] `MainShellViewModel` + `TodayPlan` stub retired now that `MainShell` simply hosts `TrackerScreen`.
- [x] `SettingsRepository.bumpKbWeight(newKg)` added so the bump action can update the persisted KB weight and clear any prior snooze. (Spec §8.2: `kb_weight_kg` is the current KB weight, not a frozen onboarding value — the "starting" wording in the column note now reads as the initial value at install rather than an immutable one.)
- [x] Unit tests on `TrackerViewModel` covering: bootstrap on init / no-op / stale-date replace / split-mismatch replace / defaults-not-ready guard, state derivation, gesture handlers, commit + re-bootstrap, no-op while pending, KB bump accept + snooze.
- [ ] Verify on device in Android Studio: tap feedback feels right, mid-workout app kill restores state, completion advances split (manual check; Compose UI tests land in Phase 8).

## Phase 5 — Log tab

Goal: a glance shows the last few months at a feedback glance.

- [x] `LogViewModel`: reads `SessionRepository.observeAll()` and folds it into a `LogContent` of week rows (one Mon-Sun row per week from the earliest session's month through `today + 14 day buffer`). Pure folding logic lives in `ui/log/LogRowBuilder.kt` so it's unit-tested without a ViewModel harness.
- [x] `LogScreen` with `LazyColumn` of week rows (one row per `LogRow.Week`, plus `LogRow.MonthGap` spacers).
- [x] Insert empty row between months, month label at left of first row of each month. Days from an adjacent month inside the current month's grid render as `DayCellState.Outside` (blank padding) so weeks stay Mon-Sun aligned without double-counting boundary days.
- [x] Cell rendering: filled R/Y/G square, `–` for past empty, outlined square for future, thicker (2dp vs 1dp) border for today.
- [x] Auto-scroll to today on first open of the tab via `rememberSaveable hasScrolledToToday` flag — subsequent emits (e.g., session committed from Tracker tab) do not jerk the grid back to today.
- [x] Tap on a colored cell → `ModalBottomSheet` with read-only session detail: split letter, KB weight + circuit statuses, each strength movement with weight + target reps + prime/working set glyphs (✓ / ✗ / ·), feedback pip.
- [x] **Scaffolding deviation**: `MainShell` now hosts a temporary two-tab `TabRow` (Tracker / Log) so the Log screen is reachable for manual verification. Phase 7 replaces this with the three-tab bottom navigation bar. No spec change — spec §11 already calls out the bottom-nav shell as Phase 7 work; this is just a placeholder to keep Phases self-verifiable.
- [x] Unit tests on `buildLogRows` (empty history, month label placement, Mon-Sun week shape, today flag, past/future/Outside states, month gaps, multi-month start, today-row index, future-buffer spilling, all three feedback colors) and on `LogViewModel` (Ready state, Logged cell wiring, `onCellTap` happy path / no-op / detail strength fidelity, `onDismissDetail`).
- [ ] Verify on device in Android Studio: tab into Log, log scrolls smoothly with 6 months of synthetic data, tapping a colored cell opens the detail sheet. (Manual; Compose UI tests land in Phase 8.)

## Phase 6 — Progression tab

Goal: see the line go up.

- [x] `ProgressionViewModel`: per movement, emit a `List<DataPoint>` of (date, weight, target reps) ordered by date
- [x] Vico chart setup with monochrome theming
- [x] `ProgressionScreen`: vertical scroll of one chart per movement (KB + 6 strength)
- [x] Stepped line (weight constant until bump)
- [x] Secondary indicator for target reps at each weight (small label or thin line)
- [x] Pinch-to-zoom on the time axis
- [x] Verify: charts render with synthetic data spanning a few months

## Phase 7 — Navigation shell

Goal: glue the tabs together.

- [x] Bottom navigation bar with three icons: Tracker, Log, Progression
- [x] Nav graph wiring screens
- [x] Decide on tab-state preservation policy (keep tab state across switches, reset Tracker if a new day starts)
- [x] Splash → onboarding-or-main branch on launch

## Phase 8 — Polish & verification

- [ ] Haptic intensity tuning on a real device (not just emulator)
- [ ] Touch target sizes ≥48dp; content descriptions on every interactive element
- [ ] Empty-state copy on Tracker for fresh-onboarded user (single first session) and on Log/Progression with no data
- [ ] Dark mode verification (theme already monochrome; just confirm inversion behaves)
- [ ] Compose UI tests on critical flows:
  - [ ] Tap a set → state changes
  - [ ] Resolve every button → feedback dialog appears
  - [ ] Tap a feedback color → session committed, Tracker shows next split
- [ ] README with build instructions, screenshots, philosophy summary

## Decisions still open (from spec §13)

These don't block work but need answers before locking v1:

- [ ] Single KB weight vs per-movement KB weight
- [ ] Bodyweight loading semantics (now moot for the pull slot since spec settled on Lat Pulldown, but push-up in KB flow has zero weight — confirm it's not on the progression charts)
- [ ] Whether to surface a deload hint after repeated failures
- [ ] Whether priming weight should be persisted for later display
- [ ] KB bump cadence — calendar month vs every N sessions

## Done = working app on a real phone

Acceptance: a fresh install onboards, lets you log a full Session A (KB flow + Lat Pulldown + Row), shows it in the Log with the chosen feedback color, and on Tracker now shows Session B with progressed weights/reps where applicable.
