# KBminisplit — Bug fix plan

Plan for the eight bugs / likely-broken items called out in the read-only review (see chat). Each section is self-contained; tackle in the recommended order to minimise rebase pain on the data-layer changes.

> Verification: this repo uses Android Studio (no `gradlew`). Run unit tests + instrumented tests through the IDE.
> Test convention: new `@Test` methods MUST use block bodies (`fun foo() { … }`), never expression bodies — Truth chains can leak a non-`Unit` return type and break the whole class.

---

## Recommended order

1. **Bug 1** — SetButton double-tap (critical, isolated, ~30 min)
2. **Bug 6** — Onboarding `complete()` race
3. **Bug 7** — Tracker KB-bump race
4. **Bug 4** — Log auto-scroll flag
5. **Bug 5** — Midnight-crossing date staleness (Log + Tracker)
6. **Bug 8** — DatabaseSeedCallback scope
7. **Bug 3** — Onboarding write transaction
8. **Bug 2** — Session N+1 refactor (largest blast radius — do last)

---

## Bug 1 — `SetButton` double-tap race

**Severity:** Critical. Spec §4.2 calls for double-tap → Failed; current code reduces it to two single taps because the gesture detector restarts on every status change.

**Files**
- [app/src/main/java/com/kbminisplit/ui/components/SetButton.kt](app/src/main/java/com/kbminisplit/ui/components/SetButton.kt)

**Root cause**
`Modifier.pointerInput(status)` cancels and re-launches the gesture coroutine each time `status` flips. Tap #1 changes status → key changes → `detectTapGestures` restarts → tap #2 starts a fresh single-tap timer.

**Approach**
- [ ] Change `pointerInput(status)` to `pointerInput(Unit)` so the gesture detector survives status updates.
- [ ] Capture the latest `status` for the long-press branch via `rememberUpdatedState`.
- [ ] Capture `onComplete`, `onFail`, `onRevert` via `rememberUpdatedState` too — they would otherwise be frozen at the first composition snapshot.

**Testing**
- [ ] Add `app/src/androidTest/java/com/kbminisplit/ui/components/SetButtonTest.kt` (Compose UI test):
  - Single tap from Pending → `onComplete` fires exactly once.
  - Quick double-tap from Pending → `onFail` fires exactly once, `onComplete` does not.
  - Long-press from Pending → `onRevert` does NOT fire.
  - Long-press from Completed → `onRevert` fires.
- [ ] Manual device check: double-tap a KB circuit button — should land on the failed glyph, not the check.

**Risks**
- `pointerInput(Unit)` is fine here because all state needed at gesture time is read through `rememberUpdatedState`. Verify no captured value falls back to the stale closure.

---

## Bug 6 — `OnboardingViewModel.complete()` read-then-write race

**Severity:** Low (rare, currently mostly idempotent), but the pattern leaks into Bug 7.

**Files**
- [app/src/main/java/com/kbminisplit/ui/onboarding/OnboardingViewModel.kt](app/src/main/java/com/kbminisplit/ui/onboarding/OnboardingViewModel.kt)
- [app/src/test/java/com/kbminisplit/ui/onboarding/OnboardingViewModelTest.kt](app/src/test/java/com/kbminisplit/ui/onboarding/OnboardingViewModelTest.kt)

**Root cause**
```
if (_state.value.isSaving || _state.value.isComplete) return
_state.update { it.copy(isSaving = true) }
```
Two callers can pass the guard before either writes back.

**Approach**
- [ ] Add `private val saving = AtomicBoolean(false)`.
- [ ] First line of `complete()`: `if (!saving.compareAndSet(false, true)) return`.
- [ ] Compute `defaults` after the gate; if null, `saving.set(false)` and return.
- [ ] Wrap the `viewModelScope.launch` body in `try { … } catch (t) { _state.update { it.copy(isSaving = false) }; saving.set(false); /* TODO surface error */ }`. Do NOT reset `saving` on success — the screen has navigated away.

**Testing**
- [ ] Existing tests stay green.
- [ ] New test: two concurrent `complete()` calls invoke `settingsRepository.saveOnboarding` exactly once.
- [ ] New test: when `saveOnboarding` throws, `isSaving` resets and a second `complete()` retries.

**Risks**
- Currently there is no error UI; the catch leaves the user looking at an unchanged screen. Either land a TODO or extend `OnboardingUiState` with an `errorMessage: String?`.

---

## Bug 7 — `TrackerViewModel.onKbBumpAccept` lacks a commit gate

**Severity:** Medium. User can over-bump KB weight by spam-tapping.

**Files**
- [app/src/main/java/com/kbminisplit/ui/tracker/TrackerViewModel.kt](app/src/main/java/com/kbminisplit/ui/tracker/TrackerViewModel.kt)
- [app/src/test/java/com/kbminisplit/ui/tracker/TrackerViewModelTest.kt](app/src/test/java/com/kbminisplit/ui/tracker/TrackerViewModelTest.kt)

**Root cause**
`onKbBumpAccept` reads `defaults`, writes `defaults.kbWeightKg + 2.0`, snoozes, clears, re-bootstraps — none of it gated. Concurrent invocations stack.

**Approach**
- [ ] Add `private val bumping = AtomicBoolean(false)` (don't reuse `committing` — different semantic).
- [ ] Gate `onKbBumpAccept` with `compareAndSet`. Reset in `finally`.
- [ ] `onKbBumpSnooze` is also fire-and-forget and can race against itself; gate the same way (or short-circuit if a snooze for this month/count already exists).

**Testing**
- [ ] Add test: rapid concurrent `onKbBumpAccept()` invokes `settingsRepository.bumpKbWeight` exactly once.
- [ ] Add test: rapid concurrent `onKbBumpSnooze()` writes the snooze exactly once.
- [ ] Existing bump tests still green.

---

## Bug 4 — `LogScreen` auto-scroll-to-today survives process death

**Severity:** Medium UX nit, easy to notice.

**Files**
- [app/src/main/java/com/kbminisplit/ui/log/LogScreen.kt](app/src/main/java/com/kbminisplit/ui/log/LogScreen.kt)
- [app/src/main/java/com/kbminisplit/ui/log/LogViewModel.kt](app/src/main/java/com/kbminisplit/ui/log/LogViewModel.kt)
- [app/src/test/java/com/kbminisplit/ui/log/LogViewModelTest.kt](app/src/test/java/com/kbminisplit/ui/log/LogViewModelTest.kt)

**Root cause**
`rememberSaveable { mutableStateOf(false) }` is restored after process death, so a relaunch days later does not re-center on today.

**Approach (Option A — recommended)**
Lift the flag into the ViewModel. ViewModels survive rotation but not process death — exactly the desired scope.
- [ ] In `LogViewModel`, add `private var hasScrolledToToday = false`.
- [ ] Add `fun consumeFirstScroll(): Boolean { val first = !hasScrolledToToday; hasScrolledToToday = true; return first }`.
- [ ] In `LogScreen.WeekList`, replace the `rememberSaveable` flag: pass `viewModel::consumeFirstScroll` in (or gate the `LaunchedEffect` body on it).
- [ ] Be careful: the `LaunchedEffect` currently keys on `(state.todayRowIndex, state.rows.size)`. Keep that keying so the effect re-runs when the list materializes, but the consume-first-scroll call gates the actual `scrollToItem`.

**Testing**
- [ ] Unit test on `consumeFirstScroll` (block-body @Test): first call returns true, subsequent return false.
- [ ] Manual: scroll up, swipe-kill the app, relaunch → log re-centres on today.
- [ ] Manual: rotate device → scroll position preserved (acceptable trade-off if it isn't, since today re-centre is mild).

**Risks**
- If the screen is removed-and-readded inside the same ViewModel (Phase 7 nav graph), the flag will block the re-entry scroll. Reset on `onCleared` or expose a `resetFirstScroll()` if Phase 7 needs it.

---

## Bug 5 — `LocalDate.now(clock)` snapshot stale after midnight

**Severity:** Medium. Affects both Log (today border) and Tracker (`bootstrapIfNeeded` date check).

**Files**
- [app/src/main/java/com/kbminisplit/ui/log/LogViewModel.kt](app/src/main/java/com/kbminisplit/ui/log/LogViewModel.kt)
- [app/src/main/java/com/kbminisplit/ui/log/LogScreen.kt](app/src/main/java/com/kbminisplit/ui/log/LogScreen.kt)
- [app/src/main/java/com/kbminisplit/ui/tracker/TrackerViewModel.kt](app/src/main/java/com/kbminisplit/ui/tracker/TrackerViewModel.kt)
- [app/src/main/java/com/kbminisplit/ui/tracker/TrackerScreen.kt](app/src/main/java/com/kbminisplit/ui/tracker/TrackerScreen.kt)

**Root cause**
`LogViewModel.state` reads `LocalDate.now(clock)` only when `historyFlow` emits. `TrackerViewModel.bootstrapIfNeeded` reads it only when something asks it to bootstrap. A long-foreground session straddling midnight keeps yesterday's date.

**Approach**
- [ ] In `LogViewModel`: add `private val today = MutableStateFlow(LocalDate.now(clock))`. Expose `fun refreshDate() { today.value = LocalDate.now(clock) }`. Change `state` to `combine(history, today)` and pass `t` into `buildLogRows`.
- [ ] In `LogScreen`: trigger `refreshDate` on `ON_RESUME` (use `LifecycleResumeEffect` from `androidx.lifecycle:lifecycle-runtime-compose` — already on the classpath via `androidx-lifecycle-runtime-compose`).
- [ ] Mirror the same pattern in `TrackerViewModel`: a `today` `StateFlow`, plumbed into `combine`, and a `refreshDate()` called from `TrackerScreen` on resume. `bootstrapIfNeeded` reads `today.value` instead of `LocalDate.now(clock)`.

**Testing**
- [ ] Unit test (Log): pre-load history, observe state, call `refreshDate()` with a clock advanced past midnight, assert `todayRowIndex` moved.
- [ ] Unit test (Tracker): bootstrap on day N, advance clock to day N+1, call `refreshDate()`, assert in-progress is replaced.
- [ ] Manual: open at 23:58, leave for 5 min, observe today border move.

**Risks**
- Do NOT add a wall-clock timer (battery drain; unnecessary). Lifecycle resume is sufficient — the user has to look at the screen to notice.
- Inject the `Clock` everywhere — never call `LocalDate.now()` without it (already the convention).

---

## Bug 8 — `DatabaseSeedCallback` uses unscoped `CoroutineScope`

**Severity:** Low. Bounded leak (dies with process), but uncaught exceptions kill the scope's Job and silently break later seeds.

**Files**
- [app/src/main/java/com/kbminisplit/data/db/DatabaseSeed.kt](app/src/main/java/com/kbminisplit/data/db/DatabaseSeed.kt)
- [app/src/main/java/com/kbminisplit/data/di/DatabaseModule.kt](app/src/main/java/com/kbminisplit/data/di/DatabaseModule.kt)

**Root cause**
`CoroutineScope(Dispatchers.IO)` — no `SupervisorJob`, no `CoroutineExceptionHandler`, no parent. One throw silences subsequent `onOpen` seeds for the rest of the process.

**Approach**
- [ ] Define a qualifier annotation `@AppScope` (Hilt `@Qualifier`).
- [ ] Provide a singleton `@AppScope CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, t -> Log.e("DatabaseSeed", "seed failed", t) })`.
- [ ] Have `DatabaseSeedCallback` take the scope by constructor (drop the default).
- [ ] Pass it from `DatabaseModule.provideAppDatabase`.

**Alternative considered:** move seeding entirely to `KBMiniSplitApp.onCreate` using `ProcessLifecycleOwner.get().lifecycleScope`. Cleaner lifecycle but requires the `androidx.lifecycle:lifecycle-process` dependency and removes the catalog-additions-on-update behaviour that `onOpen` provides today. Stick with Option A unless we want to drop on-open re-seeding.

**Testing**
- [ ] Existing `ExerciseSeedTest` (instrumented) still passes.
- [ ] Add an instrumented test that throws once during seeding and verifies the next `onOpen` still seeds. Skip if mockk-on-Android-test friction is too high.

---

## Bug 3 — `SettingsRepository.saveOnboarding` is not transactional

**Severity:** High in failure mode — corrupts onboarding state irrecoverably (settings flagged onboarded, weights missing → app stuck on Tracker "Loading…").

**Files**
- [app/src/main/java/com/kbminisplit/data/repository/SettingsRepository.kt](app/src/main/java/com/kbminisplit/data/repository/SettingsRepository.kt)
- [app/src/androidTest/java/com/kbminisplit/data/repository/SettingsRepositoryTest.kt](app/src/androidTest/java/com/kbminisplit/data/repository/SettingsRepositoryTest.kt)

**Root cause**
`saveOnboarding` does `settingsDao.upsert(...)` then `settingsDao.upsertStartingWeights(...)`. Two separate transactions. Process death between them is unrecoverable.

**Approach**
- [ ] Inject `AppDatabase` into `SettingsRepository` (Hilt — add to `DatabaseModule` if not already provided).
- [ ] Wrap the two upserts in `database.withTransaction { … }` (extension from `androidx.room:room-ktx`, already on the classpath).
- [ ] Verify `withTransaction` dispatches off the calling thread (it does; uses Room's transaction executor).

**Testing**
- [ ] Add instrumented test: write a corrupted scenario where the second upsert throws; verify the first is rolled back (settings remains un-onboarded, no orphan weight rows).
- [ ] Existing happy-path tests pass unchanged.

**Risks**
- Beware nested DAO calls inside the `withTransaction` block — they reuse the same connection, so no deadlock, but don't combine with un-related Flow collection inside.

---

## Bug 2 — `SessionRepository` N+1 select

**Severity:** High (perf cliff that worsens monotonically with history).

**Files**
- [app/src/main/java/com/kbminisplit/data/db/SessionDao.kt](app/src/main/java/com/kbminisplit/data/db/SessionDao.kt)
- [app/src/main/java/com/kbminisplit/data/mapper/SessionMapper.kt](app/src/main/java/com/kbminisplit/data/mapper/SessionMapper.kt)
- [app/src/main/java/com/kbminisplit/data/repository/SessionRepository.kt](app/src/main/java/com/kbminisplit/data/repository/SessionRepository.kt)
- new file: `app/src/main/java/com/kbminisplit/data/db/SessionWithSets.kt`
- [app/src/androidTest/java/com/kbminisplit/data/repository/SessionRepositoryTest.kt](app/src/androidTest/java/com/kbminisplit/data/repository/SessionRepositoryTest.kt)

**Root cause**
`SessionRepository.toDomain()` calls `getSetsForSession(session.id)` once per row inside `Flow.map`. Every emit of `observeAll()` re-fetches every session's sets sequentially.

**Approach**
- [ ] Add `data class SessionWithSets(@Embedded val session: SessionEntity, @Relation(parentColumn="id", entityColumn="sessionId") val sets: List<SetEntryEntity>)`.
- [ ] In `SessionDao` add:
  - `@Transaction @Query("SELECT * FROM session ORDER BY date ASC") fun observeAllWithSets(): Flow<List<SessionWithSets>>`
  - `@Transaction @Query("SELECT * FROM session ORDER BY date ASC") suspend fun getAllWithSets(): List<SessionWithSets>`
  - `@Transaction @Query("SELECT * FROM session WHERE date BETWEEN :start AND :endInclusive ORDER BY date ASC") fun observeBetweenWithSets(start: String, endInclusive: String): Flow<List<SessionWithSets>>`
  - `@Transaction @Query("SELECT * FROM session WHERE date = :date LIMIT 1") suspend fun getByDateWithSets(date: String): SessionWithSets?`
- [ ] Add `fun SessionWithSets.toDomain(): Session` in `SessionMapper.kt` (sort the `sets` list by `(isPriming desc, setIndex asc)` after fetch — `@Relation` ignores ORDER BY clauses).
- [ ] Replace all reads in `SessionRepository` with the new DAO methods. Drop the private `List<SessionEntity>.toDomain()` helper and `getSetsForSession`/`getByDate`/`getAll`/`observeAll`/`observeBetween` once unused.
- [ ] Schema is unchanged → no Room migration, no schema-export bump.

**Testing**
- [ ] All existing instrumented `SessionRepositoryTest` cases pass unchanged.
- [ ] Add a unit test (mocked DAO) confirming `observeAll()` calls only the new `observeAllWithSets` (no per-session set fetch).
- [ ] Re-run `LogViewModelTest` and `TrackerViewModelTest` — they mock the repository, not the DAO, so they should be unaffected.

**Risks**
- `@Relation` does not honour ORDER BY — sets must be sorted in Kotlin. Pin the sort comparator in one place (mapper) and add a test for it.
- Watch for callers that depend on `SetEntryEntity.id` ordering — the post-sort by `(isPriming, setIndex)` may differ from a previous DB-level `ORDER BY` if any tests asserted on `id`.
- Once landed, consider switching `LogViewModel.state` and `TrackerViewModel.state` to `SharingStarted.WhileSubscribed(5_000)` (covered in the broader optimisation list, not this bug).

---

## After all eight land

- [ ] Run the full unit + instrumented test suites in Android Studio.
- [ ] Manual smoke: onboarding → Tracker session → commit → KB bump prompt → Log tab → tap a cell → kill + relaunch app.
- [ ] Update `todo.md` Phase 4 and Phase 5 verification checkboxes if they reference any of the now-fixed behaviours.
