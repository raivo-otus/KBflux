# KBminisplit — Specification

A minimal, opinionated Android app for tracking a strength program you define yourself.

## 1. Vision & Philosophy

KBminisplit is a training tracker built around one idea: during a session you
should be tapping, not typing. It ships with a kettlebell + barbell split, but
that split is data, not code — the whole program is editable in the app.

Design principles, in order of priority:

1. **One screen does one thing.** No menus, no settings, no toggles unless they earn their place.
2. **Tap, don't type.** Logging a set is a single tap. Failing a set is a double tap. No number pads during a workout.
3. **You decide the programming; the app keeps score.** Which day comes next and which movement comes first are automatic. What you lift, and when it goes up, are yours.
4. **Monochrome, with one signal.** Black, white, and grays. Color appears only as the red / yellow / green session-feedback dot.
5. **Append-only log.** A completed session is a record, not a draft. No edit screens for history.

## 2. The Program

A program is an ordered list of **days**. Each day holds ordered **blocks**, and
each block holds ordered **movements**. Sessions rotate through the days one per
completed session (§9.1).

### 2.1 Warmup

The commute to the gym. **Not tracked in the app.**

### 2.2 Days

A day has a name (shown on the Tracker) and a stable **key** written to every
session logged against it. The key is generated once and never changes, so
renaming, reordering or deleting a day leaves history intact.

### 2.3 Blocks

Two kinds:

- **Standard** — every movement gets its own lead-in and working set buttons.
- **Circuit** — the movements are labels; one button per **round** covers the
  whole lap. All movements share one weight. Used for the kettlebell flow.

Two switches apply to either kind:

- **Rotates** — the movement order shifts by one every time the day comes
  around, so nothing is permanently done last on tired arms. With two movements
  this is a straight alternation; with N it cycles through every starting
  position before repeating.
- **Reveals later** — the block stays hidden until every earlier block is
  resolved. This is the two-stage session: main work first, accessories after.

A circuit block additionally carries its number of rounds, its shared weight, and
an opt-in **kettlebell ladder** (§9.3).

### 2.4 Movements

Every parameter lives on the movement as programmed on that day, so the same
exercise can be programmed differently on two days:

| Field           | Notes                                                          |
| --------------- | -------------------------------------------------------------- |
| name            | Display name; resolved to a stable slug for history             |
| sets            | Number of working sets                                          |
| rep range       | Min–max, e.g. 8–12. Shown as a range; never a moving target     |
| weight          | The live working weight — the single source of truth            |
| increment       | How much a bump moves it: 1, 2, 2.5, 5, 10 kg, or anything typed |
| lead-in sets    | 0 (none), 1 (warm-up), or 2 (prime + warm-up)                   |
| assisted        | The logged number is machine help, so progress *lowers* it      |
| per side        | Reps are counted one side at a time ("8–12/side")               |

Renaming a movement follows it everywhere it appears, including into
already-logged history — the slug is what history stores, and it never changes.

### 2.5 The default program

A fresh install is seeded with the three-day split the app used to hardcode, and
an upgrading install is seeded with the same program carrying its existing
weights, kettlebell size and rep ceiling across.

| Day       | Circuit (3 rounds, ladder)              | Main (rotates)                | Accessories (rotates, reveals later) |
| --------- | --------------------------------------- | ----------------------------- | ------------------------------------ |
| A · Pull  | Swings, High Pull /side, Goblet Squat    | Lat Pulldown, Barbell Row     | Side-Delt Flyes, Tricep Ext, Back Ext |
| B · Push  | Swings, Clean & Press /side, Goblet Squat | Bench Press, Assisted Dips   | Side-Delt Flyes, Bicep Curls, Back Ext |
| C · Legs  | Swings, Goblet Squat, Snatch /side       | High-Bar Squat, RDL           | Side-Delt Flyes, Tricep Ext, Bicep Curls |

Circuit rep ranges are 20–32 / 10–16 / 5–8 by position, spanning what used to be
a four-stage rep ramp; main lifts are 3 × 8–12 with two lead-in sets;
accessories are 3 × 8–12 with none.

### 2.6 Cadence

The app makes no calendar assumptions. The program advances one day per
completed session — whenever that is.

## 3. App Surface

Three bottom-nav tabs:

1. **Tracker** — today's workout
2. **Log** — calendar grid of past sessions, colored by feedback
3. **Program** — the whole split, editable in place

A **Help/Info** overlay is accessible via the top app bar icon.

There is no onboarding wizard. A fresh install seeds the default program and
opens on the Program tab, so the first thing you see is the thing you can
change; every later launch opens on the Tracker.

## 4. Tracker

### 4.1 Layout

Single scrollable screen, top to bottom:

```
┌────────────────────────────────────────┐
│  Pull                   Wed 13 May     │  ← header: day name + date
├────────────────────────────────────────┤
│  Kettlebell flow · 16 kg               │
│                                        │
│  Swings           20–32                │  ← movement labels, no per-row button
│  High Pulls       10–16/side           │
│  Goblet Squats    5–8                  │
│                                        │
│    Round 1     Round 2     Round 3     │  ← one button per lap
│       ●           ●           ●        │
├────────────────────────────────────────┤
│  Lat Pulldown       70 kg        8–12  │
│                                        │
│  Prime · Warm-up · Work · Work · Work  │
│  (35)    (52.5)    ●      ●      ●     │  ← lead-ins show their load
│  ┌──────────────────────────────────┐  │
│  │ All sets done · go to 72.5 kg?   │  │  ← appears once every set is ✓
│  └──────────────────────────────────┘  │
├────────────────────────────────────────┤
│  Barbell Row        60 kg        8–12  │
│                                        │
│  Prime · Warm-up · Work · Work · Work  │
│  (30)    (45)      ●      ●      ●     │
└────────────────────────────────────────┘
```

### 4.2 Set buttons — interaction

Each set is a single circular button. Three states:

- **Pending** — outlined, empty
- **Completed** — filled, checkmark glyph (single tap)
- **Failed** — filled with an em-dash glyph (double tap)

A long-press on a completed/failed button reverts it to pending (in-session
correction only). Once the session is saved, sets are immutable.

**Feedback on tap:**

- Single tap → short haptic click + scale animation + fill transition
- Double tap → long haptic + alternate glyph
- Long press → light haptic + revert animation

A **triple-tap** on a weight opens the editor for it. Editing mid-session changes
both the set in front of you and the program, so it applies next time too.

### 4.3 Lead-in sets

Prime and Warm-up are "done" taps — no rep tracking — but each shows the weight
to plate up *inside* its circle (replaced by the status glyph once tapped). The
number is the equipment setting: load for a traditional lift, assistance pin for
an assisted one. How many a movement gets is programmed per movement (§2.4).

The loads are derived from the working weight, never stored:

- **Prime** targets 50% of the working load; **Warm-up** targets 75%. With a
  single lead-in it is the warm-up.
- Rounded to the nearest **2.5 kg** so it is realistic to plate up.
- Floored at **20 kg**, or at the working weight when that is lighter, so a
  lead-in is never heavier than the set it leads into. A movement light enough
  to make a ramp pointless is simply programmed with no lead-in sets.
- **Assisted movements** invert: the number is machine assistance, so the same
  50%/75% reduction is applied to the *effective* load (bodyweight − pin) and
  the pin is raised to match. This needs a current bodyweight, so the weekly
  check-in (§9.5) is asked up front on any day that programs an assisted
  movement; until one is entered the lead-in sets mirror the working pin.

### 4.4 Completion flow

Blocks marked "reveals later" appear once every earlier block is resolved. When
every button in every block is in a non-pending state:

1. A modal slides up: "How did that feel?"
2. Three large color dots: 🔴 🟡 🟢 (the only color in the app)
3. User taps one → the session is committed → the day pointer advances → the
   Tracker re-renders showing the next day.

There is **no skip, no save-as-draft, no edit.** The session lives in the log the
moment feedback is given.

### 4.5 Mid-session persistence

If the app is killed mid-workout, the in-progress state of every button is
restored on next launch. Sessions only commit on feedback.

A stored session is discarded and rebuilt when it no longer describes today's
workout: a new date, a different day, or a program edit that changed which
movements today needs.

### 4.6 Empty program

A program with no days can prescribe nothing. The Tracker says so and points at
the Program tab.

### 4.7 Rest guide

Marking any set completed or failed (re)starts a rest guide pinned to the bottom
edge of the Tracker: a count-up `m:ss` over a hairline track that fills toward
**3:00**, with a notch at **1:30**.

- **1:30** — enough after priming, warm-up, or sets that felt easy.
- **3:00** — full rest before the next heavy working set.

Soft guidance only, never an alarm: a subtle haptic marks each threshold, past
3:00 the bar dims and keeps counting, and nothing is ever blocked. The guide
hides under the feedback modal and resets with the session. In-memory only — it
survives rotation but not process death.

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

Tapping a colored cell reveals a small read-only card. It replays the session
**as it was performed** — its own order, weights and rep ranges — rather than
describing it in terms of today's program, which may since have changed. A
session logged against a day that no longer exists shows its raw day key. No
edit affordance.

### 5.3 Scope

Show from the first logged session (or app install) onward — no infinite past.

## 6. Program

The whole split, editable in place. Days collapse to a one-line summary and
expand to their blocks and movements.

- **Reordering** uses up/down arrows, not drag. The app has no drag-and-drop
  anywhere else and a list this short does not justify the dependency.
- Tapping a movement opens a sheet with every field from §2.4, plus delete.
- Tapping a block header opens its name and switches (rotates, reveals later,
  and for circuits: rounds and the kettlebell ladder).
- Deleting a day takes its blocks and movements with it. Sessions logged against
  it are kept — history is never touched by a program edit.

The movement registry is never pruned, so a movement dropped from the program
still renders a name in the Log.

## 7. First launch

The default program (§2.5) is seeded and the app opens on the Program tab.
Weights can be corrected there, or in-session by triple-tapping any weight.

## 8. Data Model

Using Room (SQLite). Column names in the tables below are the actual camelCase
Kotlin property names.

### 8.1 `exercise` (the movement registry)

| Column        | Type | Notes                                          |
| ------------- | ---- | ---------------------------------------------- |
| `slug`        | TEXT | PK — stable identity written to `set_entry`    |
| `displayName` | TEXT | The name shown; renaming updates it in place   |

Every programming parameter lives on `program_item`. The registry stays as the
foreign-key target for `set_entry`, so a movement dropped from the program still
resolves to a name. Rows are never deleted, and the seed only ever *inserts* —
it never overwrites a rename.

The table retains `category`, `isPerSide`, `weightStepKg`, `minReps` and
`maxReps` columns from earlier versions. They are vestigial: rebuilding a table
three foreign keys point into buys nothing.

### 8.2 `program_day` / `program_group` / `program_item`

```
program_day     id · dayKey (unique) · name · position
program_group   id · dayId → program_day (cascade) · name · kind · position
                rotates · isDeferred
                rounds · circuitSlug · weightKg · usesLadder
                weightChangedAt · bumpSnoozedAt
program_item    id · groupId → program_group (cascade)
                exerciseSlug → exercise.slug · position
                sets · minReps · maxReps · leadInSets
                weightStepKg · isAssisted · isPerSide · currentWeightKg
```

- `dayKey` is the value written to `session.split`. The seeded program uses
  `"A"`, `"B"`, `"C"` so sessions logged before programs were editable still
  resolve.
- The circuit columns only carry meaning when `kind = CIRCUIT`. `circuitSlug` is
  the sentinel exercise the round rows are stored under; the seeded kettlebell
  groups reuse `kb_flow`.
- **`currentWeightKg` is the only place a working weight is stored.** It is
  written by the bump chip, the Tracker's weight editor, the Program editor and
  the rest-week deload. Rows are updated in place, never deleted and
  reinserted, or the user's accumulated weight is lost.
- Positions are always rewritten to a dense `0..n-1` after any mutation that can
  disturb ordering.

### 8.3 `user_settings` (singleton row, `id = 0`)

| Column                      | Type    | Notes                                              |
| --------------------------- | ------- | -------------------------------------------------- |
| `id`                        | INTEGER | PK, always `0`                                      |
| `onboardedAt`               | INTEGER | Stamped on first visit to the Program tab; only decides which tab opens first |
| `isDarkMode`                | INTEGER | Nullable override                                   |
| `hapticLevel`               | INTEGER | 0 Low / 1 Medium / 2 High                           |
| `bodyweightKg`              | REAL    | Latest weekly check-in                              |
| `bodyweightLoggedAt`        | INTEGER | Drives the staleness prompt                         |
| `restWeekAnchorSessions`    | INTEGER | Session count at the last rest week                 |
| `restWeekSnoozedAtSessions` | INTEGER | Session count at the last snooze, or null           |

`kbWeightKg`, `startingTargetReps`, `standardMaxReps`, `kbBumpSnoozedAtMonth`
and `kbBumpSnoozeSessionCount` are vestigial. The seed reads the first three once
to build the default program, after which the program owns them.

`starting_weight` is likewise vestigial — the seed copies it onto program items
and nothing reads it afterwards.

### 8.4 `session`

| Column         | Type    | Notes                                                     |
| -------------- | ------- | --------------------------------------------------------- |
| `id`           | INTEGER | PK, autogenerated                                          |
| `date`         | TEXT    | ISO date (local), indexed                                  |
| `split`        | TEXT    | The `ProgramDay.key` — column name kept from earlier versions |
| `feedback`     | TEXT    | `Red` / `Yellow` / `Green`                                 |
| `kbWeightKg`   | REAL    | Snapshot of the day's first ladder circuit; 0 when none    |
| `bodyweightKg` | REAL    | Snapshot at commit, for assisted effective load            |
| `completedAt`  | INTEGER | epoch millis                                               |

### 8.5 `set_entry`

One row per set, including circuit rounds. Prime and warm-up are both stored with
`isPriming = true`, told apart by `setIndex` (0 = prime, 1 = warm-up); working
sets are `isPriming = false` with `setIndex` 1–N.

| Column          | Type    | Notes                                                       |
| --------------- | ------- | ----------------------------------------------------------- |
| `id`            | INTEGER | PK, autogenerated                                            |
| `sessionId`     | INTEGER | FK → `session.id` (cascade delete)                           |
| `exerciseSlug`  | TEXT    | FK → `exercise.slug`                                         |
| `setIndex`      | INTEGER | Ordinal within (session, movement)                           |
| `isPriming`     | BOOL    | True for prime and warm-up                                   |
| `targetReps`    | INTEGER | Low end of the rep range; null for circuit rounds            |
| `targetRepsMax` | INTEGER | High end; null on sessions logged before rep ranges existed  |
| `weightKg`      | REAL    |                                                              |
| `status`        | TEXT    | `Completed` / `Failed`                                       |
| `position`      | INTEGER | The movement's ordinal in the session **as performed**, i.e. after rotation. The Log orders by this |

### 8.6 In-progress session (two tables)

Holds the unfinalized button state if the user is mid-workout. Cleared on commit.

`in_progress_session` (singleton, `id = 0`) carries `date` and `dayKey`.

`in_progress_set` carries one row per button:

| Column           | Type    | Notes                                                  |
| ---------------- | ------- | ------------------------------------------------------ |
| `id`             | INTEGER | PK. **Buttons are addressed by this**                  |
| `programGroupId` | INTEGER | The owning block                                       |
| `programItemId`  | INTEGER | The owning movement; `0` for a circuit's round rows     |
| `exerciseSlug`   | TEXT    | FK → `exercise.slug`                                   |
| `setIndex`       | INTEGER |                                                        |
| `isPriming`      | BOOL    |                                                        |
| `targetReps`     | INTEGER | nullable                                               |
| `targetRepsMax`  | INTEGER | nullable                                               |
| `weightKg`       | REAL    |                                                        |
| `state`          | TEXT    | `Pending` / `Completed` / `Failed`                     |
| `position`       | INTEGER |                                                        |

Unique index on `(programGroupId, programItemId, setIndex, isPriming)`. Rows are
addressed by `id` rather than by exercise, because a user-defined day may
legitimately program the same movement twice. On commit, the UI guarantees no row
is in the `Pending` state.

### 8.7 Migrations

Every schema change bumps `DB_VERSION` and adds an explicit `Migration`. No
destructive migrations in release builds — losing a user's training history is
worse than crashing on first launch of a buggy build. Current version: **7**.

The 6 → 7 migration creates structure only; the default program is seeded in
Kotlin on the same open, where it can carry the user's starting weights across
readably. The two in-progress tables are dropped and rebuilt rather than
migrated: a half-finished session is ephemeral, and the new rows are addressed
differently. `MigrationTest` covers the upgrade.

## 9. Progression Rules

**Nothing moves a weight except the user.** There is no progression engine and
nothing is derived from history: a movement's weight is what the program says it
is.

### 9.1 Next day

```
day_for_today = the day after last_completed_session's day, in program order
wrapping at the end; the first day if there is no history
or if that day has since been deleted
```

Session-count driven, never calendar driven — skipping a week doesn't skip a day.

### 9.2 Weight

Reps are a **range**, shown as a range. Anywhere inside it counts, so there is no
per-session rep target to chase.

Complete every working set of a movement and a chip appears offering the next
weight up (one increment; for an assisted movement, one increment *less*
assistance). Taking it writes the new weight to the program — this session keeps
the weight actually lifted. Tapping again gives it back, and reverting a set
retracts the offer.

Fail a set and nothing happens. That is the point: train to failure, then milk
the weight until you clear it again.

"Armed" is not stored anywhere. It is simply the program weight differing from
the session weight, which makes the chip its own undo.

### 9.3 Kettlebell ladder

Bells come in big discrete jumps, so a circuit block can opt into a ladder:
**8, 10, 12, 16, 20, 24, 28, 32 kg**. Unlike a barbell movement this is paced by
time, not performance — three months on one bell, then an offer:

> "It's been 3 months — move up to {next rung} kg?"

The clock is the group's own `weightChangedAt` stamp, so accepting silences the
prompt for another three months and "Not yet" holds it off for two weeks. The
offer only appears before the first round is touched — swapping bells mid-circuit
would invalidate the rounds already logged. Never fires at the top of the ladder.

### 9.4 Rest week

After **24 logged sessions** since the last rest week (≈ 2 months at three a
week) the Tracker offers one. Counting sessions rather than calendar time means
"consistent logging" falls out for free: training less often pushes the prompt
further out instead of nagging someone who hasn't accumulated the fatigue.

Accepting drops **every** movement in the program by its own increment — assisted
movements gain a step of assistance, nothing goes below zero — and resets the
counter. Circuit weights are left alone; the ladder is already the conservative
clock. "Not yet" holds the prompt off for two more sessions.

### 9.5 Bodyweight check-in

Effective load for an assisted movement is bodyweight − pin, so a current
bodyweight is needed to derive it. The Tracker asks when one is missing or older
than **7 days**, and only on a day that actually programs an assisted movement.
The value in force is snapshotted onto each session at commit, so historical
effective load stays fixed even if bodyweight is later corrected.

## 10. Administrative Features

"Secret" administrative menus are accessible via a **5-tap gesture** on the
center title text ("KB MiniSplit") in the top app bar. The available options
depend on the active tab:

- **Tracker Tab**: jump to a specific day of the program.
- **Log Tab**: factory reset (wipe all data).
- **Program Tab**: haptic intensity (Low/Medium/High), and JSON export/import.

Backups are version 2, carrying the program tables and the movement registry
alongside history and settings. A version 1 backup still restores; the seed then
rebuilds the default program from the settings it carried.

## 11. Tech Stack & Architecture

- **Language:** Kotlin
- **UI:** Jetpack Compose, Material 3 (mostly the typography & shapes; the color scheme is overridden with a monochrome palette)
- **Min SDK:** 26 (Android 8.0) — Compose floor
- **Target SDK:** latest stable at build time
- **Persistence:** Room
- **Reactive state:** Kotlin Flow / StateFlow
- **DI:** Hilt
- **Build:** Gradle Kotlin DSL, version catalogs (`libs.versions.toml`)
- **Tests:**
  - Unit: JUnit + Truth + Turbine for Flows. The progression rules get exhaustive coverage.
  - Instrumented: Room round-trips, the 6 → 7 migration, and Compose UI tests on the critical flows.

`java.time.Clock` and the IO `CoroutineDispatcher` are injected everywhere they
are needed, so tests stay deterministic.

### 11.1 Architecture

MVVM with a clean separation:

```
ui/         Composables + ViewModels (one per screen)
domain/     Pure-Kotlin model + rules, no Android deps
data/       Room DAOs, repositories, mappers
```

## 12. Project Structure

```
KBminisplit/
├── docs/spec.md
├── README.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/
│   └── libs.versions.toml
└── app/
    ├── build.gradle.kts
    ├── schemas/                            (exported Room schemas, 1..7)
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/kbminisplit/
        │   │   ├── KBMiniSplitApp.kt        (Application)
        │   │   ├── MainActivity.kt
        │   │   ├── ui/
        │   │   │   ├── theme/               (monochrome Material theme)
        │   │   │   ├── components/          (SetButton, FeedbackDot, RestTimerBar, NumberField)
        │   │   │   ├── tracker/
        │   │   │   ├── log/
        │   │   │   ├── program/
        │   │   │   ├── info/
        │   │   │   ├── main/                (MainShell)
        │   │   │   ├── root/
        │   │   │   ├── mapper/              (in-progress rows → Tracker blocks)
        │   │   │   └── nav/
        │   │   ├── domain/
        │   │   │   ├── model/               (Program, Session, SetEntry, InProgressSet)
        │   │   │   └── progression/         (NextDay, GroupRotation, WeightBump, RestWeek,
        │   │   │                             CircuitBumpPrompt, AcclimatizationLoad,
        │   │   │                             EffectiveLoad, BodyweightPrompt, KbRamp)
        │   │   └── data/
        │   │       ├── db/                  (AppDatabase, DAOs, DefaultProgram, seed)
        │   │       ├── entity/              (Room entities)
        │   │       ├── mapper/
        │   │       ├── di/
        │   │       └── repository/
        │   └── res/
        ├── test/                            (JVM unit tests; the rules live here)
        └── androidTest/                     (Room + migration + Compose UI tests)
```

## 13. Out of Scope

- Cloud sync, account, multi-device
- Plate calculator
- Localization (English-only, kg-only)
- Unit conversion to lb
- Workout reminders / notifications
- Analytics or telemetry
- Watch app, widget, complications
- Sharing sessions or programs

## 14. Open Questions

1. **Two sessions in one day.** Sessions are keyed by local date, so a second
   session on the same day is not supported (and arguably shouldn't be).
2. **Per-day rotation of blocks.** Movements rotate within a block; the blocks
   themselves keep their position. Whether whole blocks should be able to swap
   order is unresolved.
3. **Ladder editability.** The bell ladder is a constant. If someone owns a
   different set of bells, they would need it to be editable.

---

*This spec is the source of truth for scope. Implementation deviations require updating this file in the same PR.*
