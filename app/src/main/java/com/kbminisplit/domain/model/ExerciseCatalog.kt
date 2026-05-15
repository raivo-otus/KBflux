package com.kbminisplit.domain.model

object ExerciseCatalog {
    // KB flow movements — displayed as reference rows on the Tracker (rep schemes
    // come from spec §2.2). They are *not* individually tracked: a session
    // records 3 circuit completions under [KbFlow] below, not 5×3 per-movement
    // sets.
    val Swings = Exercise("swings", "Swings", Category.KB, isPerSide = false, weightStepKg = 2.0)
    val CleanAndPress = Exercise("clean_and_press", "Clean & Press", Category.KB, isPerSide = true, weightStepKg = 2.0)
    val Lunge = Exercise("lunge", "Lunge", Category.KB, isPerSide = true, weightStepKg = 2.0)
    val GobletSquat = Exercise("goblet_squat", "Goblet Squat", Category.KB, isPerSide = false, weightStepKg = 2.0)
    val PushUp = Exercise("push_up", "Push-up", Category.KB, isPerSide = false, weightStepKg = 0.0)

    // Sentinel KB-flow exercise. One `set_entry` row per completed circuit
    // (3 per session) uses this slug. KB progression is single-weight (§9.3),
    // and the Progression chart for KB is one line (§6.1), so per-movement
    // rows would add storage without surfacing anywhere.
    val KbFlow = Exercise("kb_flow", "KB Flow", Category.KB, isPerSide = false, weightStepKg = 2.0)

    val LatPulldown = Exercise("lat_pulldown", "Lat Pulldown", Category.A, isPerSide = false, weightStepKg = 2.5)
    val BarbellRow = Exercise("barbell_row", "Barbell Row", Category.A, isPerSide = false, weightStepKg = 2.5)
    val Bench = Exercise("bench", "Bench Press", Category.B, isPerSide = false, weightStepKg = 2.5)
    val Ohp = Exercise("ohp", "Overhead Press", Category.B, isPerSide = false, weightStepKg = 2.5)
    val HighBarSquat = Exercise("high_bar_squat", "High-Bar Squat", Category.C, isPerSide = false, weightStepKg = 2.5)
    val Deadlift = Exercise("deadlift", "Deadlift", Category.C, isPerSide = false, weightStepKg = 2.5)

    val all: List<Exercise> = listOf(
        Swings, CleanAndPress, Lunge, GobletSquat, PushUp, KbFlow,
        LatPulldown, BarbellRow, Bench, Ohp, HighBarSquat, Deadlift,
    )

    /** Reference labels for the KB section header — not used for set tracking. */
    val kbFlowMovements: List<Exercise> = listOf(Swings, CleanAndPress, Lunge, GobletSquat, PushUp)

    fun bySlug(slug: String): Exercise? = all.firstOrNull { it.slug == slug }

    fun strengthForSplit(split: Split): Pair<Exercise, Exercise> = when (split) {
        Split.A -> LatPulldown to BarbellRow
        Split.B -> Bench to Ohp
        Split.C -> HighBarSquat to Deadlift
    }
}
