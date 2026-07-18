# KBminisplit — Specification

A minimal, opinionated Android app for tracking a kettlebell-focused A/B/C workout split.

## 1. Vision & Philosophy

KBminisplit is a single-purpose training tracker. It encodes one workout program — a kettlebell foundation followed by an A (pull) / B (push) / C (squat) strength rotation — and does nothing else.

Design principles, in order of priority:

1. **One screen does one thing.** No menus, no settings, no toggles unless they earn their place.
2. **Tap, don't type.** Logging a set is a single tap. Failing a set is a double tap. No number pads during a workout.
3. **The app decides.** Today's split, today's target reps, today's weight — all derived from history. The user lifts; the app keeps score.
4. **Monochrome, with one signal.** Black, white, and grays. Color appears only as the red / yellow / green session-feedback dot.
5. **Append-only log.** A completed session is a record, not a draft. No edit screens.

## 2. The Workout Program

Every session has the same shape: **KB Flow + one of A / B / C**. The strength portion rotates A → B → C → A …

### 2.1 Warmup

The commute to the gym. **Not tracked in the app.**

### 2.2 KB Flow (every session)

A circuit of three kettlebell movements, repeated three times back-to-back.
The movements are themed to the day's split; the rep scheme is positional
(first movement 32, second 16, third 8 at the full scheme), with per-side
movements annotated "/side":

| Split     | First      | Second             | Third          |
| --------- | ---------- | ------------------ | -------------- |
| A (Pull)  | Swings 32  | High Pulls 16/side | Goblet Squat 8 |
| B (Push)  | Swings 32  | Clean & Press 16/side | Goblet Squat 8 |
| C (Legs)  | Swings 32  | Goblet Squat 16    | Snatch 8/side  |

One kettlebell weight is used for the whole flow. (See §9.3 for KB progression.)

After a weight bump the positional scheme ramps back up, advancing one stage
per **3 completed workouts** (one full A/B/C cycle) at the new weight:

| Workouts at new weight | Scheme        |
| ---------------------- | ------------- |
| 1–3                    | 20 / 10 / 5   |
| 4–6                    | 24 / 12 / 6   |
| 7–9                    | 28 / 14 / 7   |
| 10+                    | 32 / 16 / 8   |

The ramp stage is derived from history (the trailing run of sessions whose
snapshotted KB weight equals the current weight), never persisted. Any weight
change — bump, manual edit, or downgrade — restarts the ramp; a weight that
has never changed (fresh installs) uses the full scheme.

The Tracker records **one set per completed circuit (3 per session)**, not
one per movement-per-round. The movements above are reference labels in
the section header; the user taps a single "Circuit" button after finishing
the whole lap.

### 2.3 Strength block (one of A / B / C)

Two compound movements per day. Each movement is **5 sets**:

- **1 priming set** — ~50% of the working weight, just to grease the movement. Not tracked for progression; tap when done.
- **1 warm-up set** — ~75% of the working weight. Not tracked for progression; tap when done.
- **3 working sets** — at the target weight and target reps. Tracked for progression.
- Alternate movement order each cycle

The priming and warm-up loads are derived from the working weight (§4.3) and shown inside their circles so the target is unambiguous at the rack.

| Day | Movement 1                                | Movement 2              |
| --- | ----------------------------------------- | ----------------------- |
| A   | Lat Pulldown                              | Barbell Row             |
| B   | Bench Press                               | Overhead Press          |
| C   | High-Bar Squat                            | Romanian Deadlift (RDL) |

### 2.4 Cadence

Ideally daily, but the app makes no assumptions. The split simply advances one step per completed session — whenever that is.

## 3. App Surface

Three bottom-nav tabs:

1. **Tracker** — today's workout
2. **Log** — calendar grid of past sessions, colored by feedback
3. **Progression** — weight-over-time charts per movement

Additionally, a **Help/Info** overlay is accessible via the top app bar icon, explaining the app philosophy and programming.

## 4. Tracker

### 4.1 Layout

Single scrollable screen, top to bottom:

```
┌────────────────────────────────────────┐
│  A — Pull           Wed 13 May         │  ← header: split letter + name + date
├────────────────────────────────────────┤
│  KB Flow · 16 kg                       │
│                                        │
│  Swings           ·32                  │  ← movement labels, no per-row button
│  High Pulls       ·16/side             │     (themed to the split, §2.2)
│  Goblet Squats    ·8                   │
│                                        │
│   Circuit 1   Circuit 2   Circuit 3    │  ← three buttons total, one per lap
│       ●           ●           ●        │
├────────────────────────────────────────┤
│  Lat Pulldown · 70 kg · target 10 reps │
│                                        │
│  Prime · Warm-up · Work · Work · Work  │
│  (35)    (52.5)    ●      ●      ●      │  ← prime/warm-up show their load
├────────────────────────────────────────┤
│  Barbell Row · 60 kg · target 12 reps  │
│                                        │
│  Prime · Warm-up · Work · Work · Work  │
│  (30)    (45)      ●      ●      ●      │
└────────────────────────────────────────┘
```

### 4.2 Set buttons — interaction

Each set is a single circular button. Three states:

- **Pending** — outlined, empty
- **Completed** — filled, checkmark glyph (single tap)
- **Failed** — filled with a em-dash glyph (double tap)

A long-press on a completed/failed button reverts it to pending (in-session correction only). Once the session is saved, sets are immutable.

**Feedback on tap:**

- Single tap → short haptic click + scale animation + fill transition
- Double tap → long haptic + alternate glyph
- Long press → light haptic + revert animation

### 4.3 Priming & warm-up sets

The Prime and Warm-up buttons are "done" taps — no rep tracking — but each shows the
weight to plate up *inside* its circle (replaced by the status glyph once tapped). The
number is the equipment setting: load for a traditional lift, assistance pin for an
assisted one.

The loads are derived from the working weight, never stored:

- **Prime** targets 50% of the working load; **Warm-up** targets 75%.
- Rounded to the nearest **2.5 kg** so it is realistic to plate up.
- Floored at **20 kg** for main lifts (A/B/C) and **5 kg** for auxiliaries, and never
  heavier than the working set (so very light or bodyweight movements don't ramp up).
- **Assisted movements** (e.g. Assisted Dips) invert: the number is machine assistance,
  so the same 50%/75% reduction is applied to the *effective* load (bodyweight − pin)
  and the pin is raised to match. This needs a current bodyweight, so the weekly
  check-in (§ bodyweight) is asked up front on any day that programs an assisted lift;
  until one is entered the lead-in sets mirror the working pin.

### 4.4 Completion flow

When **every** button on the screen is in a non-pending state (completed or failed):

1. A modal slides up: "How did that feel?"
2. Three large color dots: 🔴 🟡 🟢 (the only color in the app)
3. User taps one → session is committed → split pointer advances → Tracker re-renders showing the next day's split.

There is **no skip, no save-as-draft, no edit.** The session lives in the log the moment feedback is given.

### 4.5 Mid-session persistence

If the app is killed mid-workout, the in-progress state of every button is restored on next launch. Sessions only commit on feedback.

### 4.6 Empty-tab state for a fresh user

After onboarding completes, the first Tracker render shows **Session A** with all buttons pending, KB weight and strength weights pulled from onboarding entries.

## 5. Log

A calendar grid of past sessions.

### 5.1 Layout

- One small square per day, laid out as 7-column weekly rows (Mon–Sun).
- Months are separated by an **empty row** (a full blank row gap).
- Month name appears as a small label on the left of the first row of each month.
- Newest day at the bottom; the view auto-scrolls to today on tab open.

### 5.2 Cell states

| Cell content             | Meaning                                             |
| ------------------------ | --------------------------------------------------- |
| Solid green/yellow/red   | A session was logged that day with that feedback    |
| `–` (dash)               | A past day with no session                          |
| Empty outlined square    | A future day                                        |
| Today                    | Slightly thicker border, regardless of state        |

Tapping a colored cell reveals a small read-only card: split letter, movements, weights, set outcomes, feedback color. No edit affordance.

### 5.3 Scope

Show from the first logged session (or app install) onward — no infinite past.

## 6. Progression

A vertically scrolling list of charts, one per tracked movement.

### 6.1 Charts

- **X axis:** time — a sliding window over the most recent **8 weeks**, the same fixed scale on every chart.
- **Y axis (left):** working weight (kg), padded outward to clean 2.5 kg multiples, with three faint gridlines (bottom/middle/top).
- A single **solid monochrome line** with a small dot per session; the latest session gets an emphasized dot, and its weight is echoed next to the movement title.
- Drawn with a plain Compose `Canvas` — no chart library.

One chart per:

- Kettlebell (single chart for the KB flow weight)
- Each strength movement on the canonical list (Pulldown, Row, Bench, OHP, Squat, Romanian Deadlift (RDL))

### 6.2 No interactivity beyond scrolling

## 7. Onboarding (first launch)

A short flow, one screen per question, swipe-forward style:

1. "Your kettlebell weight (kg)?" — single numeric entry.
2. "Starting working weights" — six numeric entries (one per strength movement), with sensible placeholder defaults.
3. "Progression and reps" — two values: starting target reps (default **8**) and preferred max reps (default **12**).

On completion the persisted "initial state" is the row in `user_settings`
plus the six `starting_weight` rows; together they form the
`OnboardingDefaults` sentinel that the progression engine reads when a
movement has no session history (§9.2). No synthetic "session 0" row is
written. The app then opens to the Tracker on **Session A**.

## 8. Data Model

Using Room (SQLite). Tables:

### 8.1 `exercise` (static / seeded)

| Column            | Type    | Notes                                              |
| ----------------- | ------- | -------------------------------------------------- |
| `slug`            | TEXT    | PK — `swings`, `lat_pulldown`, `bench`, etc.       |
| `display_name`    | TEXT    |                                                    |
| `category`        | TEXT    | `KB` / `A` / `B` / `C`                             |
| `is_per_side`     | BOOL    | for KB display formatting                          |
| `weight_step_kg`  | REAL    | default 2.5; KB is 2.0                             |
| `min_reps`        | INTEGER | default 8                                          |
| `max_reps`        | INTEGER | default 16                                         |

Seeded at install time and re-checked on every DB open (`INSERT OR IGNORE`)
so catalog rows added in later releases backfill into existing installs.

> Slug is the natural key — stable across releases and human-readable in
> queries. We dropped the synthetic integer `id` since it had no callers.

The catalog carries one extra row, `kb_flow`, used as the sentinel
`exercise_slug` for the three per-session KB-circuit rows in `set_entry`
(§2.2). The named KB movements (swings, clean & press, goblet squat, high
pull, snatch) remain in the catalog as per-split display references but never
appear in `set_entry`.

### 8.2 `user_settings` (singleton row, `id = 0`)

| Column                          | Type    | Notes                                            |
| ------------------------------- | ------- | ------------------------------------------------ |
| `id`                            | INTEGER | PK, always `0`                                   |
| `onboarded_at`                  | INTEGER | epoch millis; null until onboarding completes    |
| `kb_weight_kg`                  | REAL    | current KB weight — initial value captured at onboarding, mutated by the KB-bump prompt (§9.3); each session snapshots this at commit time |
| `starting_target_reps`          | INTEGER | starting target reps (default 8) at onboarding   |
| `standard_max_reps`            | INTEGER | preferred max reps for standard lifts (default 12)|
| `kb_bump_snoozed_at_month`      | TEXT    | ISO `YYYY-MM`; set when user taps "Not yet". Retained for storage compatibility — suppression is purely session-count based (§9.3) |
| `kb_bump_snooze_session_count`  | INTEGER | session count at snooze; suppresses the prompt for 2 sessions |

### 8.2.1 `starting_weight` (one row per strength movement)

| Column         | Type | Notes                                |
| -------------- | ---- | ------------------------------------ |
| `exercise_slug`| TEXT | PK, FK → `exercise.slug`             |
| `weight_kg`    | REAL | onboarding starting working weight   |

Together with `user_settings.kb_weight_kg` and `starting_target_reps`, this is
what the progression engine reads as `OnboardingDefaults` when a movement has
no history.

`saveOnboarding` writes `user_settings` and the six `starting_weight` rows in
two sequential DAO calls; each table's reactive `Flow` invalidates separately,
so for one tick a consumer can see settings present and weights still empty.
The `buildOnboardingDefaults` mapper guards against this by returning `null`
until every strength slug has a `starting_weight` row, so observers (e.g. the
post-onboarding Tracker bootstrap) only ever see a fully-formed
`OnboardingDefaults`.

### 8.3 `session`

| Column         | Type    | Notes                                  |
| -------------- | ------- | -------------------------------------- |
| `id`           | INTEGER | PK, autogenerated                      |
| `date`         | TEXT    | ISO date (local), unique index         |
| `split`        | TEXT    | `A` / `B` / `C`                        |
| `feedback`     | TEXT    | `Red` / `Yellow` / `Green`             |
| `kb_weight_kg` | REAL    | snapshot of KB weight that day         |
| `completed_at` | INTEGER | epoch millis                           |

### 8.4 `set_entry`

One row per set, including KB rounds. The priming and warm-up sets are both stored with
`is_priming = true`, told apart by `set_index` (0 = prime, 1 = warm-up); the three
working sets are `is_priming = false` with `set_index` 1–3. Treating warm-up as a
priming row keeps it out of every progression/deload calculation (which key on
`is_priming = false`) and needs no schema change.

| Column          | Type    | Notes                                                        |
| --------------- | ------- | ------------------------------------------------------------ |
| `id`            | INTEGER | PK, autogenerated                                            |
| `session_id`    | INTEGER | FK → `session.id` (cascade delete)                           |
| `exercise_slug` | TEXT    | FK → `exercise.slug`                                         |
| `set_index`     | INTEGER | 0-based ordinal within (session, exercise)                   |
| `is_priming`    | BOOL    | true for the prime (`set_index` 0) and warm-up (`set_index` 1) sets |
| `target_reps`   | INTEGER | nullable for KB (KB reps are fixed) and priming/warm-up sets |
| `weight_kg`     | REAL    | working weight or KB weight                                  |
| `status`        | TEXT    | `Completed` / `Failed`                                       |

### 8.5 In-progress session (two tables)

Holds the unfinalized button state if the user is mid-workout. Cleared on commit.

`in_progress_session` (singleton, `id = 0`) carries the session header:

| Column         | Type    | Notes                                  |
| -------------- | ------- | -------------------------------------- |
| `id`           | INTEGER | PK, always `0`                         |
| `date`         | TEXT    | ISO date                               |
| `split`        | TEXT    | `A` / `B` / `C`                        |
| `kb_weight_kg` | REAL    | snapshot for this session              |

`in_progress_set` carries one row per button, mirroring `set_entry`:

| Column          | Type    | Notes                                                  |
| --------------- | ------- | ------------------------------------------------------ |
| `id`            | INTEGER | PK, autogenerated                                      |
| `exercise_slug` | TEXT    | FK → `exercise.slug`                                   |
| `set_index`     | INTEGER | 0-based ordinal                                        |
| `is_priming`    | BOOL    |                                                        |
| `target_reps`   | INTEGER | nullable                                               |
| `weight_kg`     | REAL    |                                                        |
| `state`         | TEXT    | `Pending` / `Completed` / `Failed`                     |

Unique index on `(exercise_slug, set_index, is_priming)`. On commit, the UI guarantees no row is in the `Pending` state.

## 9. Progression Rules

All progression is **derived from history** at the moment the Tracker renders. No "current state" table is kept for weights or rep targets.

Progression does not happen if user has failed sets. If feedback from previous weeks is mostly red/yellow ask user for confirmation on progression.

### 9.1 Next split

```
split_for_today = next_after(last_completed_session.split)
where next_after(A)=B, next_after(B)=C, next_after(C)=A
if no sessions: A
```

### 9.2 Strength weight & target reps for a movement

Look at the most recent session in which this movement appeared.

Let `W` = weight used, `R` = target reps used.

Let `max_reps` = user-configured preferred max for the lift.

Let `all_working_completed` = every working set (3 of them) for this movement in that session has `status = completed` (none failed).

Then for today:

- If `all_working_completed` is **false** → repeat: same `W`, same `R`.
- Else if `R < max_reps` → same `W`, target `R + 1`.
- Else (`R == max_reps` and all completed) → `W + weight_step_kg`, target `min_reps`.

If this movement has never been logged → use the onboarding starting values (weight + starting reps), coerced to the movement's `[min_reps, max_reps]` range.

### 9.3 KB weight progression

Time-based, not performance-based. Weight moves along the owned-bell ladder
**8, 10, 12, 16, 20, 24, 28, 32 kg**; after each move the rep scheme ramps
back up over nine workouts (§2.2).

Once the current KB weight has been in use for **3 months** — measured from
the first session of the trailing run of history at that weight — the app
prompts:

> "It's been 3 months — bump KB to {next ladder weight} kg?"

User taps yes / not yet. Once due, the prompt persists every session until
accepted or snoozed; "Not yet" snoozes it for two more sessions, then it asks
again. The prompt never fires at the top of the ladder (32 kg), with an empty
history, or before the current weight has a completed session — which is also
why accepting needs no snooze stamp: the fresh weight's run is empty until
3 months pass again. The KB weight stored on each session is whatever was
active that day.

Off-ladder weights (e.g. 18 kg from the pre-ladder +2 kg rule, or a free-text
edit) simply target the next rung up.

No automatic bumps without confirmation.

### 9.4 Display

The Tracker header shows the **target** weight and reps that the rules above produced. The user just executes.

## 11. Administrative Features

"Secret" administrative menus are accessible via a **5-tap gesture** on the center title text ("KB MiniSplit") in the top app bar. The available options depend on the active tab:

- **Tracker Tab**: Manual split override (Force A/B/C).
- **Log Tab**: Factory reset (Wipe all data).
- **Progression Tab**: 
    - Haptic intensity adjustment (Low/Medium/High).
    - Data Backup/Restore (JSON Export and Import).

## 12. Tech Stack & Architecture

- **Language:** Kotlin
- **UI:** Jetpack Compose, Material 3 (mostly the typography & shapes; the color scheme is overridden with a monochrome palette)
- **Min SDK:** 26 (Android 8.0) — Compose floor
- **Target SDK:** latest stable at build time
- **Persistence:** Room
- **Reactive state:** Kotlin Flow / StateFlow
- **DI:** Hilt (convention; manual DI also fine for an app this small — flag at first PR)
- **Charts:** hand-drawn with Compose `Canvas` (no chart dependency)
- **Build:** Gradle Kotlin DSL, version catalogs (`libs.versions.toml`)
- **Tests:**
  - Unit: JUnit + Turbine for Flows. Progression-rules engine gets exhaustive coverage.
  - Instrumented: Compose UI tests on a couple of critical flows (set tap → state, completion → feedback dialog).

### 12.1 Architecture

MVVM with a clean separation:

```
ui/         Composables + ViewModels (one per screen)
domain/     Pure-Kotlin progression engine, no Android deps
data/       Room DAOs, repositories, mappers
```

The progression engine is a **pure function** of `(history, today's date, settings) → today's prescription`. Everything else flows from there.

## 13. Project Structure

```
KBminisplit/
├── spec.md
├── README.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/
│   └── libs.versions.toml
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/kbminisplit/
        │   │   ├── KBMiniSplitApp.kt        (Application)
        │   │   ├── MainActivity.kt
        │   │   ├── ui/
        │   │   │   ├── theme/               (monochrome Material theme)
        │   │   │   ├── components/          (shared: SetButton, FeedbackDot)
        │   │   │   ├── tracker/
        │   │   │   ├── log/
        │   │   │   ├── progression/
        │   │   │   ├── onboarding/
        │   │   │   └── nav/
        │   │   ├── domain/
        │   │   │   ├── model/               (Session, SetEntry, Prescription)
        │   │   │   └── progression/         (the rules engine)
        │   │   └── data/
        │   │       ├── db/                  (AppDatabase, DAOs)
        │   │       ├── entity/              (Room entities)
        │   │       ├── mapper/
        │   │       └── repository/
        │   └── res/
        ├── test/                            (JVM unit tests; progression engine lives here)
        └── androidTest/                     (Compose UI tests)
```

## 14. Out of Scope (v1)

- Cloud sync, account, multi-device
- Plate calculator
- Light/dark theme switching beyond following the system
- Localization (English-only, kg-only)
- Unit conversion to lb
- Workout reminders / notifications
- Analytics or telemetry
- Watch app, widget, complications
- Sharing sessions

## 15. Open Questions

These are decisions to revisit before locking, or after a first build is in hand:

1. **KB bump cadence.** ~~Calendar month is one rule; "every N sessions" might be more honest if usage is sporadic.~~ Resolved: 3 months at the current weight (measured from the trailing run of sessions at that weight, so sporadic use still counts real exposure), stepping along the owned-bell ladder with a rep ramp (§2.2, §9.3).
2. **Deload / regression.** What if a movement fails repeatedly? Today: it just stalls. Should we surface a "consider deloading 10%" hint after, say, 3 consecutive failed sessions?
3. **Time-of-day in the log.** Session is keyed by local date. Two sessions in one day are not supported (and arguably shouldn't be for this program).

---

*This spec is the source of truth for v1 scope. Implementation deviations require updating this file in the same PR.*
