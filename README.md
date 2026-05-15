# KBminisplit

A minimalist, high-opinion strength training tracker for a specific Kettlebell + Barbell/Dumbbell program.

## Philosophy

- **One Big Number**: Focus on one key metric per movement (weight) and one target reps goal.
- **Low Friction**: Tracking is done via large buttons with gestures. Single tap for success, double tap for failure.
- **Auto-Progression**: The app calculates your next workout based on your history. No manual entry of "what did I do last time?".
- **Feedback-Driven**: After each session, you rate how it felt. This influences future prescriptions.

## Tech Stack

- **UI**: Jetpack Compose (Material 3)
- **Architecture**: MVVM with Clean Architecture principles (Domain/Data/UI split)
- **Dependency Injection**: Hilt
- **Database**: Room
- **Charts**: Vico
- **Testing**: JUnit 4, Truth, Turbine, Mockk, Compose UI Test

## Build Instructions

### Prerequisites
- Android Studio Ladybug (or newer)
- JDK 17+

### Steps
1. Clone the repository.
2. Open the project in Android Studio.
3. Sync Gradle.
4. Run the `app` module on an emulator or a real device (min SDK 26).

## Implementation Status

Currently in Phase 8 (Polish & Verification). Core features are implemented:
- Onboarding for starting weights.
- Daily Tracker with auto-prescriptions.
- Log tab with calendar-like view.
- Progression tab with weight/reps charts.
- Robust state restoration (app-kill safe).

## Spec

The detailed implementation specification can be found in [spec.md](spec.md).
