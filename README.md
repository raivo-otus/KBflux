# KBminisplit

A minimalist, high-opinion strength training tracker. It ships with a kettlebell
+ barbell split, and lets you rewrite that split from inside the app.

## Philosophy

- **Low Friction**: Tracking is large buttons and gestures. Single tap for a completed set, double tap for a failed one, long press to undo.
- **Your Programming**: Days, blocks, movements, sets, rep ranges, increments — all editable in the Program tab.
- **You Move the Weight**: Reps are a range, not a target. Complete every set and the app *offers* the next weight up; fail one and nothing changes. Train to failure, then milk the weight until you clear it again.
- **Feedback-Driven**: After each session you rate how it felt, and that colours the calendar.

## Features

- **Tracker** — today's session, built from your program. Movements inside a block rotate each cycle so the same lift is never permanently last; a block can stay hidden until the earlier work is done.
- **Log** — a calendar grid coloured by session feedback; tap a day to replay it exactly as performed.
- **Program** — the whole split, editable in place: add days, group movements into blocks, set rep ranges, increments, lead-in sets, assisted movements.
- Kettlebell circuits track rounds and can climb the bell ladder with a prompt every three months.
- A rest-week prompt after 24 logged sessions, which deloads every movement one increment.
- Robust state restoration (app-kill safe), and JSON backup/restore.

## Tech Stack

- **UI**: Jetpack Compose (Material 3), monochrome palette
- **Architecture**: MVVM with a Domain/Data/UI split
- **Dependency Injection**: Hilt
- **Database**: Room
- **Testing**: JUnit 4, Truth, Turbine, MockK, Compose UI Test, Room `MigrationTestHelper`

## Build Instructions

### Prerequisites
- Android Studio Ladybug (or newer)
- JDK 21 (the Gradle daemon is pinned to the JetBrains Runtime)

### Steps
1. Clone the repository.
2. Open the project in Android Studio.
3. Sync Gradle.
4. Run the `app` module on an emulator or a real device (min SDK 26).

## Spec

The detailed implementation specification can be found in [docs/spec.md](docs/spec.md).
