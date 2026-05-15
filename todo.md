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

- [ ] Room entities mirroring §8: `ExerciseEntity`, `UserSettingsEntity`, `SessionEntity`, `SetEntryEntity`, `InProgressSessionEntity`
- [ ] DAOs: queries needed by progression (history for a movement, last session, list by date range), inserts, deletes for in-progress
- [ ] `AppDatabase` with migrations infrastructure even if v1 has none (set version=1, document policy)
- [ ] Seed exercise catalog on first DB open (via `RoomDatabase.Callback`)
- [ ] Mappers: entity ↔ domain
- [ ] Repositories: `SessionRepository`, `SettingsRepository`, `InProgressRepository` — Flow-returning where reactive, suspend for writes
- [ ] Hilt module wiring DB + DAOs + repos
- [ ] Instrumented tests on in-memory Room: seed correctness, session insert + query, in-progress upsert/clear
- [ ] Verify: DB inspector shows seeded exercises after first launch

## Phase 3 — Onboarding

Goal: first launch collects starting weights and target reps, then opens Tracker.

- [ ] `OnboardingViewModel` holding entered values in a single `StateFlow`
- [ ] Three-step composable flow (HorizontalPager or simple step state):
  - [ ] Step 1: KB weight (numeric, kg)
  - [ ] Step 2: six starting weights (one per strength movement, with placeholders)
  - [ ] Step 3: starting target reps (default 8)
- [ ] Persist on completion → `UserSettings.onboarded_at` set, seed "starting prescriptions" (either as a sentinel row or by writing a synthetic "session 0" row that the progression engine treats as the baseline — decide and document)
- [ ] App-launch branch in `MainActivity` or a root composable: if not onboarded → onboarding graph; else → main nav
- [ ] Verify on a fresh install: flow completes, Tracker opens to Session A with the entered weights showing

## Phase 4 — Tracker tab

Goal: the only feature that has to feel right. Tap, see it stick, finish, get prompted, advance.

- [ ] `SetButton` composable — three visual states (Pending / Completed / Failed), single-tap / double-tap / long-press gestures, haptic + scale animation, content descriptions for a11y
- [ ] `TrackerViewModel`:
  - [ ] On init, ask progression engine for today's `DayPlan`
  - [ ] Maintain in-memory set-state map; persist to `InProgressSession` on every change (debounced is fine)
  - [ ] Restore from `InProgressSession` on cold start
  - [ ] Expose `allButtonsResolved: StateFlow<Boolean>` to drive the feedback dialog
- [ ] `TrackerScreen` composable rendering §4.1 layout:
  - [ ] Header (split letter + name + date)
  - [ ] KB Flow section: 5 movements × 3 round buttons each, KB weight shown
  - [ ] Strength section: 2 movements, each with Prime + 3 working buttons, weight + target reps shown
- [ ] Feedback dialog: bottom modal sheet with R/Y/G dots; large touch targets
- [ ] On feedback tap: commit `Session` + `SetEntry` rows, clear `InProgressSession`, recompute next prescription, refresh
- [ ] KB-bump prompt — show as a one-button banner above the KB section when applicable; "Bump to N+2 kg" / "Not yet"
- [ ] Verify on device: tap feedback feels right, mid-workout app kill restores state, completion advances split

## Phase 5 — Log tab

Goal: a glance shows the last few months at a feedback glance.

- [ ] `LogViewModel`: emit a flat list of `DayCell` items (one per day from first-ever session to today + a buffer of future days)
- [ ] `LogScreen` with a `LazyVerticalGrid` (7 columns) or `LazyColumn` of week rows
- [ ] Insert empty row between months, month label at left of first row
- [ ] Cell rendering: filled R/Y/G square, `–` for past empty, outlined square for future, thicker border for today
- [ ] Auto-scroll to today on first open of the tab
- [ ] Tap on a colored cell → bottom sheet with session detail (split, movements, weights, set outcomes, feedback). Read-only.
- [ ] Verify: log scrolls smoothly with 6 months of synthetic data

## Phase 6 — Progression tab

Goal: see the line go up.

- [ ] `ProgressionViewModel`: per movement, emit a `List<DataPoint>` of (date, weight, target reps) ordered by date
- [ ] Vico chart setup with monochrome theming
- [ ] `ProgressionScreen`: vertical scroll of one chart per movement (KB + 6 strength)
- [ ] Stepped line (weight constant until bump)
- [ ] Secondary indicator for target reps at each weight (small label or thin line)
- [ ] Pinch-to-zoom on the time axis
- [ ] Verify: charts render with synthetic data spanning a few months

## Phase 7 — Navigation shell

Goal: glue the tabs together.

- [ ] Bottom navigation bar with three icons: Tracker, Log, Progression
- [ ] Nav graph wiring screens
- [ ] Decide on tab-state preservation policy (keep tab state across switches, reset Tracker if a new day starts)
- [ ] Splash → onboarding-or-main branch on launch

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
